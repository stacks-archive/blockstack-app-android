package org.blockstack.app.ui.identity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
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
        })

        identityViewModel.loadCurrentUser()

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
    }
}
