package org.blockstack.app.ui.identity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_delete_identity.*
import kotlinx.android.synthetic.main.content_delete_identity.*
import org.blockstack.app.R

class DeleteIdentityActivity : AppCompatActivity() {

    private lateinit var deleteIdentityViewModel: DeleteIdentityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_identity)
        setSupportActionBar(toolbar)


        deleteIdentityViewModel =
            ViewModelProviders.of(
                this,
                DeleteIdentityViewModelFactory(applicationContext)
            ).get(DeleteIdentityViewModel::class.java)

        backup_button.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "THE encrypted seed phrases")
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }

        remove_button.setOnClickListener{
            finish()
        }

        deleteIdentityViewModel.loadCurrentUser()


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_BACKUP) {
            if (resultCode == Activity.RESULT_OK) {
                finish()
            }
        }
    }

    companion object {
        const val REQUEST_CODE_BACKUP = 1
        const val KEY_ACCOUNT_NAME = "account_name"
    }
}
