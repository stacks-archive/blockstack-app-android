package org.blockstack.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.uport.sdk.jwt.model.JwtHeader
import okhttp3.OkHttpClient
import org.blockstack.android.sdk.Blockstack
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
    val blockstack: Blockstack
) {


    var identity: BlockstackIdentity? = null

    var account: BlockstackAccount? = null
        private set

    // in-memory cache of the loggedInUser object for account
    val user: UserData? = null
    val loginStates: MutableMap<String, LoginState> = mutableMapOf()


    private var accountMeta = BlockstackAccount.Companion.MetaData()

    val isLoggedIn: Boolean
        get() = account != null


    fun isLoggedInFor(domain: String): Boolean {
        return loginStates[domain] != null
    }

    fun displayName(): String =
        user?.profile?.name
            ?: user?.json?.getString("username")
            ?: user?.json?.getString("identityAddress")
            ?: "(No name set)"


    init {
        // If user credentials will be cached in local storage, it is recommended it be encrypted
        // @see https://developer.android.com/training/articles/keystore
        account = walletSource.lastAccount()
    }

    fun logout() {
        loginStates.clear()
        account = null
        walletSource.reset()
    }

    fun checkUsername(username: String, callback: (Result<Boolean>) -> Unit) {
        // handle checkUsername
        GlobalScope.launch {
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
    }


    fun registerUsername(username: String, callback: (Result<BlockstackAccount>) -> Unit) {
        val keys = walletSource.create()
        account = walletSource.register(username, keys)
        callback(Result.Success(account!!))
    }


    fun checkPermissions(domain: String, scopes: Array<String>): Boolean {
        // TODO check permissions
        return false
    }

    suspend fun logUserInFor(
        domain: String,
        authRequest: String,
        decodedToken: Triple<JwtHeader, JSONObject, ByteArray>
    ): Boolean {
        val authResponse = blockstack.makeAuthResponse(account!!, authRequest)
        val isValid = blockstack.verifyAuthRequest(authRequest)
        if (!isValid) {
            throw InvalidParameterException("invalid token")
        }
        loginStates[domain] =
            LoginState(decodedToken.second.toAuthRequest(), authResponse, mutableListOf())
        return true
    }

    fun grantPermissions(grantedPermissionList: ArrayList<String>) {
        accountMeta.permissions = grantedPermissionList
    }

    suspend fun makeAuthResponse(authRequestToken: String): String? {
        return blockstack.makeAuthResponse(account!!, authRequestToken)
    }

    fun addPermission(permissions: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun uriForDomain(domain: String?): Uri? {
        val loginState = loginStates[domain]
        if (loginState != null) {
            val token = loginState.authResponse
            return Uri.parse(loginState.authRequest.redirectUrl)
                .buildUpon().appendQueryParameter(
                    "authResponse",
                    token
                ).build()
        } else {
            return null
        }
    }

    fun userDataForDomain(domain: String?): String? {
        val loginState = loginStates[domain]
        if (loginState != null) {
            return user?.json.toString()
        } else {
            return null
        }
    }

    suspend fun restoreIdentity(seedWords: String, c: (Result<BlockstackAccount>) -> Unit) {
        val account = walletSource.restore(seedWords)
        this.account = account
        GlobalScope.launch {
            val ownerAddress = account.ownerAddress
            Log.d(TAG, ownerAddress)
            val usernames = lookupNames(ownerAddress)
            if (usernames.isNotEmpty()) {
                walletSource.save(usernames.get(0), seedWords)
                c(Result.Success(BlockstackAccount(usernames.get(0), account.keys, account.salt)))
            } else {
                c(Result.Success(BlockstackAccount(null, account.keys, account.salt)))
            }
        }
    }

    private suspend fun lookupNames(btcAddress: String): List<String> {
        return userDataSource.findNames(btcAddress)
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
                    blockstack = Blockstack()
                )
            }
        }
    }
}


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
