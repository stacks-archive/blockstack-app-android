package org.blockstack.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonException
import me.uport.sdk.core.decodeBase64
import me.uport.sdk.jwt.InvalidJWTException
import me.uport.sdk.jwt.JWTEncodingException
import me.uport.sdk.jwt.JWTUtils
import me.uport.sdk.jwt.model.JwtHeader
import org.blockstack.android.sdk.BlockstackSignIn
import org.blockstack.android.sdk.Scope
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.app.R
import org.blockstack.app.data.AuthRepository
import org.blockstack.app.data.AuthRequest
import org.blockstack.app.ui.auth.AuthenticatorActivity
import org.blockstack.app.ui.identity.IdentityActivity
import org.blockstack.app.ui.permissions.PermissionsActivity
import org.blockstack.app.ui.permissions.PermissionsActivity.Companion.KEY_CALLING_PACKAGE
import org.blockstack.app.ui.permissions.PermissionsActivity.Companion.KEY_SCOPES
import org.json.JSONArray
import org.json.JSONObject
import org.kethereum.crypto.CryptoAPI
import org.kethereum.extensions.toHexStringNoPrefix
import java.net.URI
import java.util.*


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
            GlobalScope.launch {
                val authRequest = getAuthRequest()
                val decodedToken = decodeToken(authRequest)
                val domain = decodedToken.second.getString("domain_name")
                if (!authRepository.isLoggedInFor(domain)) {
                    if (authRepository.isLoggedIn) {
                        authRepository.logUserInFor(domain, authRequest, decodedToken)
                        handleLoggedInUser(decodedToken.second.toAuthRequest())
                    } else {
                        startActivityForResult(
                            Intent(this@FrontDoorActivity, AuthenticatorActivity::class.java),
                            REQUEST_CODE_NEW_ACCOUNT
                        )
                    }
                } else {
                    authRepository.logUserInFor(domain, authRequest, decodedToken)
                    handleLoggedInUser(decodedToken.second.toAuthRequest())
                }
            }
        }
    }

    private fun handleLoggedInUser(authRequest: AuthRequest) {
        runOnUiThread {
            showUserName(authRepository.displayName())
        }
        if (!authRepository.checkPermissions(
                authRequest.domain,
                authRequest.scopes.toTypedArray()
            )
        ) {
            requestBlockstackPermissions(authRequest)
        } else {
            finish()
        }
    }


    private suspend fun getAuthRequest(): String {
        val authRequestToken = intent.data.getQueryParameter("authRequest")

        val authRequest = if (authRequestToken != null) {
            authRequestToken
        } else {
            val blockstackSignIn = BlockstackSignIn(
                BlockstackConfig(
                    URI(intent.getStringExtra(KEY_DOMAIN)),
                    "",
                    "",
                    arrayOf(Scope.StoreWrite)
                )
            )
            val keyPair = CryptoAPI.keyPairGenerator.generate()
            val transitPrivateKey = keyPair.privateKey.key.toHexStringNoPrefix()
            blockstackSignIn.makeAuthRequest(
                transitPrivateKey,
                Date().time + 3600 * 24 * 7,
                emptyMap()
            )
        }
        return authRequest
    }

    private fun requestBlockstackPermissions(authRequest: AuthRequest) {
        startActivityForResult(
            Intent(
                this,
                PermissionsActivity::class.java
            ).putExtra(KEY_SCOPES, authRequest.scopes.toTypedArray())
                .putExtra(KEY_DOMAIN, authRequest.domain)
                .putExtra(KEY_CALLING_PACKAGE, callingPackage),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (resultCode == RESULT_OK) {
                val domain = data?.getStringExtra(KEY_DOMAIN)
                if (callingPackage != null) {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(KEY_USER_DATA, authRepository.userDataForDomain(domain))
                    )
                } else {
                    val uri = authRepository.uriForDomain(domain)
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW, uri
                        )
                    )

                }
                finish()

            }
        } else if (requestCode == REQUEST_CODE_NEW_ACCOUNT) {
            if (resultCode == RESULT_OK) {
                handleLoggedInUser(lastAuthRequest!!)
            }
        }
    }

    private fun decodeToken(token: String): Triple<JwtHeader, JSONObject, ByteArray> {

        //Split token by . from jwtUtils
        val (encodedHeader, encodedPayload, encodedSignature) = JWTUtils.splitToken(token)
        if (encodedHeader.isEmpty())
            throw InvalidJWTException("Header cannot be empty")
        else if (encodedPayload.isEmpty())
            throw InvalidJWTException("Payload cannot be empty")
        //Decode the pieces
        val headerString = String(encodedHeader.decodeBase64())
        val payloadString = String(encodedPayload.decodeBase64())
        val signatureBytes = encodedSignature.decodeBase64()

        try {
            //Parse Json
            val header = JwtHeader.fromJson(headerString)
            val payload = JSONObject(payloadString)
            return Triple(header, payload, signatureBytes)
        } catch (ex: JsonException) {
            throw JWTEncodingException("cannot parse the JWT($token)", ex)
        }
    }

    private fun showUserName(displayName: String) {
        Toast.makeText(this, getString(R.string.logged_in_as, displayName), Toast.LENGTH_SHORT)
            .show()
    }

    companion object {
        private val REQUEST_CODE_PERMISSIONS: Int = 1
        private val REQUEST_CODE_NEW_ACCOUNT: Int = 2

        val KEY_PERMISSIONS = "permissions"
        val KEY_USER_DATA = "user_data"
        val KEY_DOMAIN = "domain"
    }
}

fun JSONObject.toAuthRequest(): AuthRequest {
    val jsonScopes = JSONArray(getString("scopes"))
    val scopes = ArrayList<String>((0 until jsonScopes.length()).map { jsonScopes.getString(it) })
    return AuthRequest(
        getString("domain_name"),
        getJSONArray("public_keys").getString(0), getString("redirect_uri"), scopes
    )
}
