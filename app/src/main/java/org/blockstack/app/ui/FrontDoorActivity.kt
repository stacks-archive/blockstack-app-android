package org.blockstack.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import org.blockstack.android.sdk.Scope
import org.blockstack.app.R
import org.blockstack.app.data.AuthRepository
import org.blockstack.app.data.AuthRequest
import org.blockstack.app.ui.auth.AuthenticatorActivity
import org.blockstack.app.ui.identity.IdentityActivity
import org.blockstack.app.ui.permissions.PermissionsActivity
import org.blockstack.app.ui.permissions.PermissionsActivity.Companion.KEY_DOMAIN


class FrontDoorActivity : Activity() {

    private var lastAuthRequest: AuthRequest? = null
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authRepository = AuthRepository.getInstance(this.applicationContext)

        if (intent.action == Intent.ACTION_MAIN) {
            startActivity(Intent(this, IdentityActivity::class.java))
            finish()
        } else if (intent.action == Intent.ACTION_VIEW) {
            val authRequest = getAuthRequest()

            if (isLoggedInFor(authRequest)) {
                handleLoggedInUser(authRequest)
            } else {
                startActivityForResult(
                    Intent(this, AuthenticatorActivity::class.java),
                    REQUEST_CODE_NEW_ACCOUNT
                )

            }
        }
    }

    private fun handleLoggedInUser(authRequest: AuthRequest) {
        showUserName(authRepository.currentDisplayName)
        if (!authRepository.checkPermissions(authRequest.domain, authRequest.scopes)) {
            requestBlockstackPermissions(authRequest)
        } else {
            finish()
        }
    }

    private fun isLoggedInFor(authRequest: AuthRequest): Boolean {
        if (!authRepository.isLoggedInFor(authRequest.domain)) {
            if (authRepository.isLoggedIn) {
                return authRepository.logUserInFor(authRequest)
            } else {
                return false
            }
        } else {
            return true
        }
    }

    private fun getAuthRequest(): AuthRequest {
        val authRequestToken = intent.data.getQueryParameter("authRequest")

        val authRequest = if (authRequestToken != null) {
            authRepository.decodeAuthRequest(authRequestToken)
        } else {
            AuthRequest(intent.getStringExtra(KEY_DOMAIN), "", "", arrayOf(Scope.StoreWrite.scope))
        }
        lastAuthRequest = authRequest
        return authRequest
    }

    private fun requestBlockstackPermissions(authRequest: AuthRequest) {
        startActivityForResult(
            Intent(
                this,
                PermissionsActivity::class.java
            ).putExtra(PermissionsActivity.KEY_SCOPES, authRequest.scopes)
                .putExtra(PermissionsActivity.KEY_CALLING_PACKAGE, callingPackage),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (resultCode == Activity.RESULT_OK) {
                if (callingPackage != null) {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(KEY_USER_DATA, authRepository.user?.json?.toString())
                    )
                } else {
                    val authRequest = getAuthRequest()
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW, Uri.parse(authRequest.redirectUrl)
                                .buildUpon().appendQueryParameter(
                                    "authResponse",
                                    "encodedJWT"
                                ).build()
                        )
                    )
                }
                finish()
            }
        } else if (requestCode == REQUEST_CODE_NEW_ACCOUNT) {
            if (resultCode == Activity.RESULT_OK) {
                handleLoggedInUser(lastAuthRequest!!)
            }
        }
    }

    private fun showUserName(displayName: String) {
        Toast.makeText(this, getString(R.string.logged_in_as, displayName), Toast.LENGTH_SHORT)
            .show()
    }

    companion object {
        private val REQUEST_CODE_PERMISSIONS: Int = 1
        private val REQUEST_CODE_NEW_ACCOUNT: Int = 2
        val KEY_USER_DATA = "user_data"
    }
}
