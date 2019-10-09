package org.blockstack.notes

import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.accounts.OperationCanceledException
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.blockstack.android.sdk.*
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.android.sdk.model.UserData
import java.net.URI
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var blockstackSession: BlockstackSession2
    private var currentAuthToken: AccountManagerFuture<Bundle>? = null
    private val REQUEST_CODE_SIGNUP: Int = 1
    private var mAccountManager: AccountManager? = null
    private var authToken: String? = null

    private val ACCOUNT_TYPE = "org.blockstack.id"
    private val AUTH_TOKEN_TYPE = "https://notes.riot.ai"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        authToken = null
        mAccountManager = AccountManager.get(this)

        // Ask for an auth token
        mAccountManager?.getAuthTokenByFeatures(
            ACCOUNT_TYPE,
            AUTH_TOKEN_TYPE,
            arrayOf(Scope.StoreWrite.scope, Scope.Email.scope),
            this,
            null,
            arrayListOf(Scope.StoreWrite.scope, Scope.Email.scope).toPermissionsBundle(),
            GetAuthTokenCallback(),
            null
        )

        val callFactory = OkHttpClient()
        blockstackSession =
            BlockstackSession2(
                SessionStore(PreferenceManager.getDefaultSharedPreferences(this)),
                AndroidExecutor(this),
                BlockstackConfig(
                    URI(AUTH_TOKEN_TYPE), "", "/manifest.json",
                    arrayOf(Scope.StoreWrite, Scope.Email)
                ),
                callFactory,
                Blockstack(callFactory)
            )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_refresh_token -> {
                // Clear session and ask for new auth token
                mAccountManager?.invalidateAuthToken(ACCOUNT_TYPE, authToken)
                currentAuthToken = mAccountManager?.getAuthTokenByFeatures(
                    ACCOUNT_TYPE,
                    AUTH_TOKEN_TYPE,
                    null,
                    this,
                    null,
                    arrayListOf(Scope.StoreWrite.scope, Scope.Email.scope).toPermissionsBundle(),
                    GetAuthTokenCallback(),
                    null
                )
                CoroutineScope(Dispatchers.Default).launch {
                    Log.d(
                        "TAG",
                        currentAuthToken!!.result.getString(AccountManager.KEY_AUTHTOKEN)
                    )
                }
                return true

            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private inner class GetAuthTokenCallback : AccountManagerCallback<Bundle> {

        override fun run(result: AccountManagerFuture<Bundle>) {
            val bundle: Bundle

            try {
                bundle = result.result

                val intent = bundle.get(AccountManager.KEY_INTENT) as Intent?
                if (null != intent) {
                    startActivityForResult(intent, REQUEST_CODE_SIGNUP)
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
                        authToken = token
                        if (token != null) {
                            val decodedToken = blockstackSession.blockstack.decodeToken(token)
                            val tokenPayload = decodedToken.second
                            val appPrivateKey = tokenPayload.getString("private_key")
                            val coreSessionToken = tokenPayload.optString("core_token")
                            val userData = blockstackSession.authResponseToUserData(
                                tokenPayload,
                                "https://core.blockstack.org",
                                appPrivateKey,
                                coreSessionToken,
                                token
                            )

                            val accountName = bundle.getString(AccountManager.KEY_ACCOUNT_NAME)
                            text1.text = "$accountName ${userData.profile?.name}- Retrieved auth token: $authToken"
                        } else {
                            // Save session username & auth token
                            text1.text = "Retrieved auth token: $authToken"
                        }
                    }
                }
            } catch (e: OperationCanceledException) {
                // If signup was cancelled, force activity termination
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

    }
}

private fun ArrayList<String>.toPermissionsBundle(): Bundle {
    val bundle = Bundle()
    bundle.putStringArrayList("permissions", this)
    return bundle
}