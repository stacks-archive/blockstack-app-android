package org.blockstack.app.data

import android.content.Context
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.model.UserData
import org.blockstack.android.sdk.model.toBlockstackConfig
import org.json.JSONObject
import org.kethereum.bip32.model.ExtendedKey
import java.io.IOException
import java.net.URL

/**
 * Class that handles authentication w/ checkUsername credentials and retrieves user information.
 */
class UserDataSource(context: Context) {
    val session = BlockstackSession(context, "notNeeded".toBlockstackConfig(emptyArray()))

    fun load(username: String, callback: (Result<UserData>) -> Unit) {
        try {
            val userData =
                session.lookupProfile(username, URL("https://core.blockstack.org/v1/names/")) {
                    if (it.hasErrors) {
                        callback(Result.Error(Exception(it.error)))
                    } else {
                        val json = JSONObject()
                        json.put("profile", it.value!!.json)
                        json.put("username", username)
                        val userData = UserData(json)
                        callback(Result.Success(userData))
                    }
                }
        } catch (e: Throwable) {
            callback(Result.Error(IOException("Error logging in", e)))
        }
    }

    fun register(username: String, keys: ExtendedKey):BlockstackAccount {
        return BlockstackAccount(username, keys)
    }
}

