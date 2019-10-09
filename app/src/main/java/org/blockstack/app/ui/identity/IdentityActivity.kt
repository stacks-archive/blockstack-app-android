package org.blockstack.app.ui.identity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_identity.*
import kotlinx.android.synthetic.main.content_identity.*
import org.blockstack.app.R
import org.blockstack.app.ui.auth.AuthenticatorActivity

class IdentityActivity : AppCompatActivity() {

    private lateinit var identityViewModel: IdentityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity)
        setSupportActionBar(toolbar)


        identityViewModel =
            ViewModelProviders.of(
                this,
                IdentityViewModelFactory(applicationContext)
            )
                .get(IdentityViewModel::class.java)

        identityViewModel.userData.observe(this@IdentityActivity, Observer {
            if (it == null) {
                startActivityForResult(
                    Intent(this, AuthenticatorActivity::class.java),
                    REQUEST_CODE_AUTHENTICATOR
                )
            }
            val userData = it ?: return@Observer

            val avatarUrl = userData.avatarUrl
            if (!TextUtils.isEmpty(avatarUrl)) {
                Picasso.get().load(avatarUrl).into(profile)
            } else {
                profile.setImageResource(R.drawable.ic_launcher)
            }
            name.text = userData.name
            bio.text = userData.bio
            button.isEnabled = it.encryptedSeedWords != null
        })

        identityViewModel.loadCurrentUser()

        button.setOnClickListener {
            val encryptedSeedWords = identityViewModel.userData.value?.encryptedSeedWords
            if (encryptedSeedWords != null) {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, encryptedSeedWords)
                    type = "text/plain"
                }

                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            } else {
                Toast.makeText(this, "no seed words", Toast.LENGTH_SHORT).show()
            }
        }

        logout.setOnClickListener {
            identityViewModel.logout()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_AUTHENTICATOR) {
            if (resultCode == Activity.RESULT_OK) {
                identityViewModel.loadCurrentUser()
            }
        }
    }

    companion object {
        const val REQUEST_CODE_AUTHENTICATOR = 1
        const val KEY_IS_DELETING_ACCOUNT = "is_deleting_account"
        const val KEY_ACCOUNT_NAME = "account_name"
    }
}
