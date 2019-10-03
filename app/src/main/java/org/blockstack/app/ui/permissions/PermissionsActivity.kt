package org.blockstack.app.ui.permissions

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_permissions.*
import org.blockstack.app.R
import org.blockstack.app.ui.FrontDoorActivity

class PermissionsActivity : AppCompatActivity() {

    private lateinit var permissionsViewModel: PermissionsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)


        permissionsViewModel =
            ViewModelProviders.of(this, PermissionsViewModelFactory(applicationContext))
                .get(PermissionsViewModel::class.java)

        val scopes = intent.getStringArrayExtra(KEY_SCOPES)
        val domain = intent.getStringExtra(KEY_DOMAIN)
        val callingPackage = intent.getStringExtra(KEY_CALLING_PACKAGE)
        permissionsViewModel.load(scopes, callingPackage, domain)

        permissionsViewModel.scope.observe(this, Observer {
            permissions_text.text = it.description
        })

        permissionsViewModel.grantedPermissions.observe(this, Observer {
            setResult(Activity.RESULT_OK, Intent().putExtra(FrontDoorActivity.KEY_DOMAIN, domain))
            finish()
        })

        deny.setOnClickListener {
            permissionsViewModel.deny()
        }
        allow.setOnClickListener {
            permissionsViewModel.allow()
        }
    }

    companion object {
        const val KEY_SCOPES = "scopes"
        const val KEY_DOMAIN = "domain"
        const val KEY_CALLING_PACKAGE = "calling_package"
        const val REQUEST_CODE_AUTHENTICATOR = 1
    }
}
