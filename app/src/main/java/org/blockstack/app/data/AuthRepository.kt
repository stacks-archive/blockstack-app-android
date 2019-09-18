package org.blockstack.app.data

import android.content.Context
import android.util.Log
import me.uport.sdk.jwt.JWTTools
import org.blockstack.android.sdk.model.UserData
import java.util.*


/**
 * Class that requests authentication and user information.
 */

class AuthRepository private constructor(
    val userDataSource: UserDataSource,
    val walletSource: WalletSource
) {


    val currentDisplayName: String
        get() =
            user?.profile?.name
                ?: user?.json?.getString("username")
                ?: user?.json?.getString("identityAddress")
                ?: "(No name set)"

    // in-memory cache of the loggedInUser object
    var user: UserData? = null
        private set

    var account: BlockstackAccount? = null
        private set

    private var accountMeta = BlockstackAccount.Companion.MetaData()

    val isLoggedIn: Boolean
        get() = account != null


    fun isLoggedInFor(domain: String): Boolean {
        return user != null
    }

    init {
        // If user credentials will be cached in local storage, it is recommended it be encrypted
        // @see https://developer.android.com/training/articles/keystore
        user = null
        account = walletSource.lastAccount()
    }

    fun logout() {
        user = null
        account = null
        walletSource.reset()
    }

    fun checkUsername(username: String, callback: (Result<Boolean>) -> Unit) {
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
        account = walletSource.register(username, keys)
        callback(Result.Success(account!!))
    }

    private fun setLoggedInUser(domain: String, user: UserData) {
        this.user = user
        // If user credentials will be cached in local storage, it is recommended it be encrypted
        // @see https://developer.android.com/training/articles/keystore
    }

    fun checkPermissions(domain: String, scopes: Array<String>): Boolean {
        // TODO check permissions
        return false
    }

    fun logUserInFor(authRequest: AuthRequest): Boolean {
        val appPrivateKey = deriveAppPrivateKey(authRequest.domain)
        user = account?.toUserData()
        user?.json?.put("appPrivateKey", appPrivateKey)
        return true
    }

    private fun deriveAppPrivateKey(domain: String): String {
        return "TODO derive app private key"
    }

    fun grantPermissions(grantedPermissionList: ArrayList<String>) {
        accountMeta.permissions = grantedPermissionList
    }

    fun decodeAuthRequest(authRequestToken: String): AuthRequest {
        val jwt = JWTTools()
        val (header, payload, sig) = jwt.decodeRaw(authRequestToken)
        return toAuthRequest(payload)
    }

    private fun toAuthRequest(payload:Map<String, Any?>): AuthRequest {
        return AuthRequest("", "", "", arrayOf())
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
                    userDataSource = UserDataSource(context),
                    walletSource = WalletSource(context)
                )
            }
        }
    }
}


