package org.blockstack.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.uport.sdk.jwt.model.JwtHeader
import okhttp3.OkHttpClient
import org.blockstack.android.sdk.Blockstack
import org.blockstack.android.sdk.DIDs
import org.blockstack.android.sdk.model.BlockstackAccount
import org.blockstack.android.sdk.model.BlockstackIdentity
import org.blockstack.android.sdk.model.UserData
import org.blockstack.app.ui.toAuthRequest
import org.json.JSONObject
import org.kethereum.crypto.getCompressedPublicKey
import org.kethereum.encodings.encodeToBase58String
import org.kethereum.hashes.ripemd160
import org.kethereum.hashes.sha256
import org.kethereum.model.ECKeyPair
import org.komputing.khex.extensions.hexToByteArray
import org.komputing.khex.extensions.toNoPrefixHexString
import java.security.InvalidParameterException
import java.util.*


/**
 * Class that requests authentication and user information.
 */

class AuthRepository private constructor(
    val userDataSource: UserDataSource,
    val walletSource: WalletSource,
    val blockstack: Blockstack,
    val syncUtils: SyncUtils
) {


    var identity: BlockstackIdentity? = null

    var account: ExtendedBlockstackAccount? = null
        private set

    val loginStates: MutableMap<String, LoginState> = mutableMapOf()


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
        if (!loginStates.containsKey(domain)) {
            return true
        }

        val permissions = loginStates.getValue(domain).permissions.map { p -> p.scope }
        for (scope in scopes) {
            if (!permissions.contains(scope)) {
                return true
            }
        }
        return false
    }

    suspend fun logUserInFor(
        domain: String,
        authRequest: String,
        decodedToken: Triple<JwtHeader, JSONObject, ByteArray>
    ): ExtendedBlockstackAccount {
        val isValid = blockstack.verifyAuthRequest(authRequest)
        if (!isValid) {
            throw InvalidParameterException("invalid token")
        }
        val authResponse = blockstack.makeAuthResponse(account!!.blockstackAccount, authRequest)
        val permissions = if (loginStates.contains(domain)) {
            loginStates.getValue(domain).permissions
        } else {
            mutableListOf()
        }
        loginStates[domain] =
            LoginState(decodedToken.second.toAuthRequest(), authResponse, permissions)
        return account!!
    }


    suspend fun logUserInFor(domain: String, scopes: ArrayList<String>): AuthRequest {
        val authResponse = withContext(Dispatchers.IO) {
            blockstack.makeAuthResponseUnencrypted(account!!.blockstackAccount, domain)
        }
        val permissions = if (loginStates.contains(domain)) {
            loginStates.getValue(domain).permissions
        } else {
            mutableListOf()
        }
        val authRequest = unencryptedAuthRequest(
            domain, scopes
        )
        loginStates[domain] =
            LoginState(authRequest, authResponse, permissions)
        return authRequest
    }


    fun grantPermissions(grantedPermissionList: ArrayList<String>) {
        accountMeta.permissions = grantedPermissionList
    }

    fun addPermissions(domain: String, scopes: ArrayList<String>) {
        if (loginStates.containsKey(domain)) {
            val currentScopes = loginStates.getValue(domain).permissions.map { p -> p.scope }
            for (scope in scopes) {
                if (currentScopes.indexOf(scope) == 0) {
                    loginStates.getValue(domain).permissions.add(Permission(scope))
                }
            }
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
                instance = AuthRepository(
                    userDataSource = UserDataSource(context, OkHttpClient()),
                    walletSource = WalletSource(context),
                    blockstack = Blockstack(),
                    syncUtils = SyncUtils(context)
                )
            }
        }
    }
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
