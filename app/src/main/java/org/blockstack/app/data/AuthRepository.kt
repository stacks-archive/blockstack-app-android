package org.blockstack.app.data

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.ByteString
import org.blockstack.android.sdk.Blockstack
import org.blockstack.android.sdk.DIDs
import org.blockstack.android.sdk.Scope
import org.blockstack.android.sdk.model.*
import org.blockstack.app.ui.permissions.COLLECTION_SCOPE_PREFIX
import org.json.JSONObject
import org.kethereum.crypto.getCompressedPublicKey
import org.kethereum.crypto.toECKeyPair
import org.kethereum.encodings.encodeToBase58String
import org.kethereum.extensions.toHexStringNoPrefix
import org.kethereum.hashes.ripemd160
import org.kethereum.hashes.sha256
import org.kethereum.model.ECKeyPair
import org.kethereum.model.PrivateKey
import org.komputing.khex.extensions.hexToByteArray
import org.komputing.khex.extensions.toNoPrefixHexString
import java.util.*


/**
 * Class that requests authentication and user information.
 */
const val GAIA_HUB_COLLECTION_KEY_FILE_NAME = ".collections.keys"

class AuthRepository private constructor(
    val userDataSource: UserDataSource,
    val walletSource: WalletSource,
    val blockstack: Blockstack,
    val hub: Hub,
    val syncUtils: SyncUtils
) {


    var identity: BlockstackIdentity? = null

    var account: ExtendedBlockstackAccount? = null
        private set

    val loginStates: MutableMap<String, LoginState> = mutableMapOf()
    val permissions: MutableMap<String, MutableList<Permission>> = mutableMapOf()

    private var accountMeta = BlockstackAccount.Companion.MetaData()

    val isLoggedIn: Boolean
        get() = account != null


    fun isLoggedInFor(domain: String): Boolean {
        return loginStates[domain] != null
    }

    suspend fun loadAccount(): ExtendedBlockstackAccount? {
        if (account == null) {
            val blockstackAccount = walletSource.lastAccount()
            if (blockstackAccount != null) {
                val user =
                    buildUserData(blockstackAccount.username, blockstackAccount.ownerAddress)
                account = ExtendedBlockstackAccount(blockstackAccount, user)
                return account
            } else {
                return null
            }
        } else {
            return account
        }
    }

    suspend fun buildUserData(username: String?, ownerAddress: String): UserData {
        val userDataJson = JSONObject()
            .put("ownerAddress", ownerAddress)
            .put("decentralizedID", DIDs.addressToDID(ownerAddress))

        if (username != null) {
            val profile = withContext(Dispatchers.IO) {
                blockstack.lookupProfile(username, null) // TODO use zonefile url from settings
            }
            userDataJson
                .put("username", username)
                .put("profile", profile.json)
        } else {
            userDataJson
                .put("profile", JSONObject())
        }

        return UserData(userDataJson)
    }

    fun logout() {
        loginStates.clear()
        account?.blockstackAccount?.username?.run {
            syncUtils.removeAccount(this)
        }
        account = null
        walletSource.reset()
    }

    suspend fun checkUsername(username: String, callback: (Result<Boolean>) -> Unit) {
        // handle checkUsername
        userDataSource.load(username) {
            Log.d(TAG, it.toString())
            if (it is Result.Error) {
                if (it.exception.message == "Error: Invalid zonefile lookup response: did not contain `address` or `zonefile` field") {
                    callback(Result.Success(true))
                } else {
                    callback(it)
                }
            } else {
                callback(Result.Error(Exception("Username not available")))
            }
        }

    }


    fun registerUsername(username: String, callback: (Result<BlockstackAccount>) -> Unit) {
        val keys = walletSource.create()
        val blockstackAccount = walletSource.register(username, keys)
        val account = ExtendedBlockstackAccount(
            blockstackAccount,
            UserData(JSONObject().put("ownerAddress", blockstackAccount.ownerAddress))
        )
        this.account = account
        syncUtils.createSyncAccount(syncUtils.getAccountName(account.user)!!)
        callback(Result.Success(account.blockstackAccount))
    }


    fun shouldCheckPermissions(domain: String, scopes: Array<String>): Boolean {
        if (!permissions.containsKey(domain)) {
            return true
        }

        if (permissions.containsKey(domain)) {
            val permissions = permissions.getValue(domain).map { p -> p.scope }
            for (scope in scopes) {
                if (!permissions.contains(scope)) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun logInWithAuthRequest(authRequest: AuthRequest): ExtendedBlockstackAccount {
        val extendedBlockstackAccount = account!!
        val domain = authRequest.domain
        val scopes = authRequest.scopes.map { Scope(it) }.toTypedArray()
        val authResponse =
            if (TextUtils.isEmpty(authRequest.transitKey)) {
                blockstack.makeAuthResponseUnencrypted(
                    extendedBlockstackAccount.blockstackAccount,
                    domain,
                    scopes
                )
            } else {
                val token = authRequest.encodedAuthRequest
                if (token != null) {
                    blockstack.makeAuthResponse(
                        extendedBlockstackAccount.blockstackAccount,
                        token,
                        scopes
                    )
                } else {
                    return extendedBlockstackAccount
                }
            }
        processCollectionScopes(authRequest, authResponse, account!!.gaiaHubUrl, account!!.settings)
        updateLoginState(authRequest, authResponse)
        return extendedBlockstackAccount
    }


    private suspend fun processCollectionScopes(
        authRequest: AuthRequest,
        authResponse: String,
        gaiaHubUrl: String,
        identitySettings: IdentitySettings
    ) {
        val appPrivateKey = account!!.blockstackAccount.getAppsNode().getAppNode(authRequest.domain).getPrivateKeyHex()
        val collectionsNode = account!!.blockstackAccount.getCollectionsNode()
        val collectionScopes =
            authRequest.scopes.filter { s -> s.startsWith(COLLECTION_SCOPE_PREFIX) }
        val collectionKeys =
            fetchOrCreateCollectionKeys(collectionScopes, collectionsNode, identitySettings)
        val collectionHubConfigs =
            getCollectionGaiaHubConfigs(collectionScopes, collectionsNode, gaiaHubUrl)
        updateAppCollectionKeys(
            collectionScopes,
            appPrivateKey,
            gaiaHubUrl,
            collectionKeys,
            collectionHubConfigs
        )
    }

    private suspend fun updateAppCollectionKeys(
        collectionScopes: List<String>,
        appPrivateKey: String,
        gaiaHubUrl: String,
        collectionKeys: List<String>,
        collectionHubConfigs: List<GaiaHubConfig>
    ) {
        val hubConfig = hub.connectToGaia(gaiaHubUrl, appPrivateKey, null)
        val keyFile = getAppCollectionKeyFile(appPrivateKey, hubConfig.urlPrefix, hubConfig.address)
        collectionScopes.mapIndexed { index, scope ->
            keyFile.put(
                scope,
                CollectionKey(collectionKeys[index], collectionHubConfigs[index]).json
            )
            true
        }
        val response = writeCollectionKeysToAppStorage(appPrivateKey, hubConfig, keyFile)
        Log.d(TAG, response.toString())
    }

    private suspend fun writeCollectionKeysToAppStorage(
        appPrivateKey: String?,
        hubConfig: GaiaHubConfig,
        keyFile: JSONObject
    ): Response {
        Log.d(TAG, keyFile.toString())
        val publicKey = PrivateKey(appPrivateKey!!).toECKeyPair().toHexPublicKey64()
        val encryptedKeyFile = blockstack.encryptContent(keyFile.toString(),CryptoOptions( publicKey = publicKey))
        Log.d(TAG, encryptedKeyFile.value?.cipherText ?: encryptedKeyFile.error?.toString())
        return hub.uploadToGaiaHub(
            GAIA_HUB_COLLECTION_KEY_FILE_NAME,
            ByteString.encodeString(encryptedKeyFile.value!!.json.toString(), Charsets.UTF_8),
            hubConfig,
            "application/json"
        )

    }

    private suspend fun getAppCollectionKeyFile(
        appPrivateKey: String?,
        gaiaHubBucketUrl: String,
        appBucketAddress: String
    ): JSONObject {
        val GAIA_HUB_COLLECTION_KEY_FILE_NAME = ".collection"
        val keyFileUrl = "$gaiaHubBucketUrl$appBucketAddress/$GAIA_HUB_COLLECTION_KEY_FILE_NAME"
        val response = hub.getFromGaiaHub(keyFileUrl)
        if (response.isSuccessful) {
            val keyFileResult = blockstack.decryptContent(
                response.body()!!.string(),
                false,
                CryptoOptions(privateKey = appPrivateKey)
            )
            return JSONObject(keyFileResult.value as String)
        } else {
            return JSONObject()
        }
    }

    private suspend fun getCollectionGaiaHubConfigs(
        scopes: List<String>,
        collectionsNode: CollectionsNode,
        gaiaHubUrl: String
    ): List<GaiaHubConfig> {

        return scopes.map { scope ->
            CoroutineScope(Dispatchers.IO).async {
                val collectionPrivateKey =
                    collectionsNode.getCollectionNode(scope)
                        .keys.keyPair.privateKey.key.toHexStringNoPrefix()
                val gaiaScopes = arrayOf(AuthScope.COLLECTION_AUTH_SCOPE)

                hub.connectToGaia(gaiaHubUrl, collectionPrivateKey, "", gaiaScopes)
            }
        }.awaitAll()
    }

    suspend fun fetchOrCreateCollectionKeys(
        scopes: List<String>,
        collectionsNode: CollectionsNode,
        settings: IdentitySettings
    ): List<String> {
        return scopes.map { scope ->
            CoroutineScope(Dispatchers.IO).async {
                val encryptionKeyIndex = getCollectionEncryptionIndex(scope, settings)
                collectionsNode.getCollectionEncryptionNode(scope, encryptionKeyIndex)
                    .keys.keyPair.privateKey.key.toHexStringNoPrefix()
            }

        }.awaitAll()
    }

    private suspend fun getCollectionEncryptionIndex(
        scope: String,
        settings: IdentitySettings
    ): Int {
        return 0
    }


    private fun updateLoginState(
        authRequest: AuthRequest,
        authResponse: String
    ) {
        val domain = authRequest.domain
        val grantedPermissions = if (loginStates.contains(domain)) {
            loginStates.getValue(domain).permissions
        } else {
            mutableListOf()
        }
        loginStates[domain] =
            LoginState(authRequest, authResponse, grantedPermissions)
    }

    fun setNewLoginState(
        authRequest: AuthRequest
    ) {
        val domain = authRequest.domain
        val lastLoginState = if (loginStates.contains(domain)) {
            loginStates.getValue(domain)
        } else {
            null
        }

        val permissions = lastLoginState?.permissions ?: mutableListOf()
        val authResponse = lastLoginState?.authResponse ?: ""

        loginStates[domain] =
            LoginState(authRequest, authResponse, permissions)
    }


    fun grantPermissions(grantedPermissionList: ArrayList<String>) {
        accountMeta.permissions = grantedPermissionList
    }

    fun addPermissions(domain: String, scopes: ArrayList<String>) {
        if (loginStates.containsKey(domain)) {
            val currentScopes = loginStates.getValue(domain).permissions.map { p -> p.scope }
            for (scope in scopes) {
                if (currentScopes.indexOf(scope) == 0) {
                    if (scope.startsWith(COLLECTION_SCOPE_PREFIX)) {

                    }
                    loginStates.getValue(domain).permissions.add(Permission(scope))
                }
            }
            permissions[domain] = loginStates.getValue(domain).permissions
        }
    }

    fun uriForDomain(domain: String?): Uri? {
        val loginState = loginStates[domain]
        if (loginState != null) {
            val token = loginState.authResponse
            if (loginState.authRequest.redirectUrl.isNotEmpty()) {
                return Uri.parse(loginState.authRequest.redirectUrl)
                    .buildUpon().appendQueryParameter(
                        "authResponse",
                        token
                    ).build()
            } else {
                return null
            }
        } else {
            return null
        }
    }

    fun userDataForDomain(domain: String?): String? {
        val loginState = loginStates[domain]
        if (loginState != null) {
            return account!!.user.json.toString()
        } else {
            return null
        }
    }

    suspend fun restoreIdentity(seedWords: String): ExtendedBlockstackAccount {
        return withContext(Dispatchers.IO) {

            var blockstackAccount = walletSource.restore(seedWords)

            val ownerAddress = blockstackAccount.ownerAddress
            val usernames = userDataSource.findNames(ownerAddress)

            val username = if (usernames.isNotEmpty()) {
                val username = usernames[0]
                walletSource.save(username, seedWords)
                blockstackAccount =
                    BlockstackAccount(username, blockstackAccount.keys, blockstackAccount.salt)
                username
            } else {
                syncUtils.createSyncAccount(blockstackAccount.ownerAddress)
                null
            }

            val user = buildUserData(username, blockstackAccount.ownerAddress)
            syncUtils.createSyncAccount(syncUtils.getAccountName(user)!!)


            val account = ExtendedBlockstackAccount(blockstackAccount, user)
            this@AuthRepository.account = account
            account
        }
    }


    companion object {
        val TAG = AuthRepository::class.java.simpleName


        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            if (instance == null) createInstance(context)
            return instance!!
        }

        @Synchronized
        private fun createInstance(context: Context) {
            if (instance == null) {
                val callFactory = OkHttpClient()
                instance = AuthRepository(
                    userDataSource = UserDataSource(context, callFactory),
                    walletSource = WalletSource(context),
                    blockstack = Blockstack(callFactory),
                    hub = Hub(callFactory),
                    syncUtils = SyncUtils(context)
                )
            }
        }
    }
}

data class CollectionKey(val encryptionKey: String, val hubConfig: GaiaHubConfig) {
    val json: JSONObject
        get() = JSONObject()
            .put("encryptionKey", encryptionKey)
            .put("hubConfig", hubConfig.json)

}

private fun DIDs.Companion.addressToDID(ownerAddress: String): String = "did:btc-addr:$ownerAddress"


fun ECKeyPair.toHexPublicKey64(): String {
    return this.getCompressedPublicKey().toNoPrefixHexString()
}

fun ECKeyPair.toBtcAddress(): String {
    val publicKey = toHexPublicKey64()
    val sha256 = publicKey.hexToByteArray().sha256()
    val hash160 = sha256.ripemd160()
    val extended = "00${hash160.toNoPrefixHexString()}"
    val checksum = checksum(extended)
    val address = (extended + checksum).hexToByteArray().encodeToBase58String()
    return address
}

private fun checksum(extended: String): String {
    val checksum = extended.hexToByteArray().sha256().sha256()
    val shortPrefix = checksum.slice(0..3)
    return shortPrefix.toNoPrefixHexString()
}
