package org.blockstack.app.calendar


import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.android.synthetic.main.content_account.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blockstack.android.sdk.BlockstackSignIn
import org.blockstack.android.sdk.Scope
import org.blockstack.android.sdk.SessionStore
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.android.sdk.model.UserData
import org.blockstack.app.data.AuthRepository
import org.blockstack.app.data.SyncUtils
import org.json.JSONObject.NULL
import org.kethereum.crypto.CryptoAPI
import org.kethereum.extensions.toHexStringNoPrefix
import org.openintents.calendar.sync.R
import org.openintents.calendar.sync.blockstackConfig
import org.openintents.distribution.about.About
import java.net.URI
import java.util.*


class AccountActivity : AppCompatActivity() {

    private lateinit var progressTexts: Array<String>
    private var currentUserData: UserData? = null
    private val TAG = AccountActivity::class.java.simpleName

    private val progressHandler = Handler()
    private var progressTextIndex: Int = 0

    private lateinit var authRepository: AuthRepository

    val progressUpdate: Runnable = object : Runnable {
        override fun run() {
            progressTextIndex %= progressTexts.size
            progressText.text = progressTexts[progressTextIndex]
            if (progressText.visibility == View.VISIBLE) {
                progressTextIndex++
                progressHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        setSupportActionBar(toolbar)

        account.visibility = View.GONE
        accountName.visibility = View.GONE
        accountDomain.visibility = View.GONE
        signInButton.visibility = View.GONE
        signOutButton.visibility = View.GONE
        calendarButton.visibility = View.GONE
        syncNowButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        blockstackHelp.visibility = View.VISIBLE
        progressTexts = resources.getStringArray(R.array.progress_texts)
        progressHandler.postDelayed(progressUpdate, 0)

        accountDomain.text = blockstackConfig.appDomain.authority
        blockstackHelp.text =
            getString(R.string.blockstack_help, blockstackConfig.appDomain.authority)

        authRepository = AuthRepository.getInstance(this)

        CoroutineScope(Dispatchers.Main).launch {
            val signIn = BlockstackSignIn(
                BlockstackConfig(
                    URI("https://cal.openintents.org"),
                    "/",
                    "/manifest.json",
                    arrayOf(Scope.StoreWrite)
                ),
                SessionStore(PreferenceManager.getDefaultSharedPreferences(this@AccountActivity))
            )
            val keyPair = CryptoAPI.keyPairGenerator.generate()
            val transitPrivateKey = keyPair.privateKey.key.toHexStringNoPrefix()
            val authRequest =
                signIn.makeAuthRequest(transitPrivateKey, Date().time + 3600 * 24 * 7, emptyMap())
            val decodedToken = authRepository.blockstack.decodeToken(authRequest)
            authRepository.logUserInFor("https://cal.openintents.org", authRequest, decodedToken)
            onLoaded()
        }


        calendarButton.setOnClickListener {
            val builder = CalendarContract.CONTENT_URI.buildUpon()
            builder.appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val intent = Intent(Intent.ACTION_VIEW)
                .setData(builder.build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        accountName.setOnClickListener {
            val authorities = arrayOf(CalendarContract.AUTHORITY)
            val accountTypes = arrayOf(SyncUtils.ACCOUNT_TYPE)
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            intent.putExtra(Settings.EXTRA_AUTHORITIES, authorities)
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, accountTypes)
            startActivity(intent)
        }

        syncNowButton.setOnClickListener {
            GlobalScope.launch {
                // sync account should have been created already
                authRepository.syncUtils.getAccountName(currentUserData)?.let {
                    val account = authRepository.syncUtils.createSyncAccount(
                        it
                    )
                    if (account != null) {
                        authRepository.syncUtils.triggerRefresh(account)
                    }
                }
            }
        }
    }

    private fun onLoaded() {
        val signedIn = authRepository.isLoggedInFor("https://cal.openintents.org")
        if (signedIn) {
            currentUserData = authRepository.account?.user
        }
        runOnUiThread {
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            if (signedIn) {
                accountName.text = authRepository.syncUtils.getAccountName(currentUserData)
                signInButton.visibility = View.GONE
                signOutButton.visibility = View.VISIBLE
            } else {
                accountName.text = ""
                signInButton.visibility = View.VISIBLE
                signOutButton.visibility = View.GONE
            }
            calendarButton.visibility = signOutButton.visibility
            syncNowButton.visibility = signOutButton.visibility
            blockstackHelp.visibility = signInButton.visibility
            account.visibility = signOutButton.visibility
            accountName.visibility = signOutButton.visibility
            accountDomain.visibility = signOutButton.visibility
        }


    }

    private fun onSignIn() {
        if (PermissionChecker.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) == PermissionChecker.PERMISSION_GRANTED
        ) {

            val userData = authRepository.account?.user
            Log.d(TAG, userData?.decentralizedID)
            GlobalScope.launch {
                authRepository.syncUtils.getAccountName(userData)?.let {
                    authRepository.syncUtils.createSyncAccount(
                        it
                    )
                    onLoaded()
                }
            }

        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_blockstack -> {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        HELP_URI
                    )
                )
                return true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, About::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            for (grantResult in grantResults) {
                if (grantResult != PermissionChecker.PERMISSION_GRANTED) {
                    return
                }
            }
            onSignIn()
        }
    }

    companion object {
        private val HELP_URI: Uri = Uri.parse("http://openintents.org/calendar")
        private val REQUEST_CODE_PERMISSIONS: Int = 1
    }
}


