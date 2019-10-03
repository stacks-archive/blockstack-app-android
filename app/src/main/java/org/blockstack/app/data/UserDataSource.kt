package org.blockstack.app.data

import android.content.Context
import android.net.Uri
import okhttp3.Call
import okhttp3.Request
import org.blockstack.android.sdk.Blockstack
import org.blockstack.android.sdk.model.BlockstackAccount
import org.blockstack.android.sdk.model.UserData
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Class that handles authentication w/ checkUsername credentials and retrieves user information.
 */
class UserDataSource(context: Context, val callFactory: Call.Factory) {
    val blockstack = Blockstack()

    suspend fun load(username: String, callback: (Result<UserData>) -> Unit) {
        try {
            val profile = blockstack.lookupProfile(username, null)

            val json = JSONObject()
            json.put("profile", profile.json)
            json.put("username", username)
            val userData = UserData(json)
            callback(Result.Success(userData))


        } catch (e: Throwable) {
            callback(Result.Error(IOException("Error logging in", e)))
        }
    }

    fun findNames(btcAddress: String):List<String> {
        val url = "https://core.blockstack.org/v1/addresses/bitcoin/$btcAddress"
        val builder = Request.Builder()
            .url(url)
        builder.addHeader("Referrer-Policy", "no-referrer")
        val request =  builder.build()
        val names = JSONObject(callFactory.newCall(request).execute().body()!!.string()).getJSONArray("names")
        return (0 until names.length()-1).map { names.getString(it)}
    }
}

