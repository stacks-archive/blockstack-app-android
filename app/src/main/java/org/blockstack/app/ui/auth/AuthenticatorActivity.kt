package org.blockstack.app.ui.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_authenticate.*
import org.blockstack.app.R
import org.blockstack.app.ui.identity.IdentityActivity

class AuthenticatorActivity : AppCompatActivity() {

    private lateinit var authenticationViewModel: AuthenticationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_authenticate)

        authenticationViewModel =
            ViewModelProviders.of(this, AuthenticationViewModelFactory(applicationContext))
                .get(AuthenticationViewModel::class.java)

        authenticationViewModel.loginFormState.observe(this@AuthenticatorActivity, Observer {
            val loginState = it ?: return@Observer

            loading.visibility = View.GONE
            check.isEnabled = loginState.isDataValid
            register.isEnabled = loginState.isUsernameAvailable

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
        })

        authenticationViewModel.loginResult.observe(this@AuthenticatorActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                updateUiWithUser(loginResult.success)
            }
            setResult(Activity.RESULT_OK)
            finish()
        })

        username.afterTextChanged {
            authenticationViewModel.loginDataChanged(
                username.text.toString()
            )
        }

        check.setOnClickListener {
            loading.visibility = View.VISIBLE
            authenticationViewModel.checkUsername(username.text.toString() + ".id.blockstack")
        }

        register.setOnClickListener {
            loading.visibility = View.VISIBLE
            authenticationViewModel.registerUsername(username.text.toString() + ".id.blockstack")
        }

        restore.setOnClickListener{
            authenticationViewModel.restore(seedwords.text.toString())
        }
    }

    private fun updateUiWithUser(model: LoggedInUser) {
        val welcome = getString(R.string.welcome)
        val displayName = model.displayName
        startActivity(Intent(this, IdentityActivity::class.java))
        Toast.makeText(
            applicationContext,
            "$welcome $displayName",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val KEY_OPTIONS = "options"
        const val KEY_REQUIRED_FEATURES = "required_features"
        const val KEY_IS_ADDING_NEW_ACCOUNT = "is_adding_new_account"
        const val KEY_AUTH_TYPE = "auth_type"
        const val KEY_ACCOUNT_TYPE = "account_type"
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
