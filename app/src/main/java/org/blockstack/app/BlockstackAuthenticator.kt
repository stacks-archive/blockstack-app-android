package org.blockstack.app

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import org.blockstack.app.ui.FrontDoorActivity
import org.blockstack.app.ui.auth.AuthenticatorActivity
import org.blockstack.app.ui.identity.DeleteIdentityActivity
import org.blockstack.app.ui.identity.IdentityActivity


class BlockstackAuthenticator(val context: Context) : AbstractAccountAuthenticator(context) {

    override fun getAuthTokenLabel(authTokenType: String?): String? {
        return if (AUTH_TOKEN_TYPE === authTokenType) {
            this.context.getString(R.string.blockstack_label)
        } else {
            null
        }
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle
    ): Bundle {
        throw UnsupportedOperationException()
    }

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle
    ): Bundle {
        throw UnsupportedOperationException()
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle
    ): Bundle {
        val am = AccountManager.get(context)
        var authToken = am.peekAuthToken(account, authTokenType)

        if (!TextUtils.isEmpty(authToken)) {
            val result = Bundle()
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken)
            return result
        }

        val intent = Intent(context, FrontDoorActivity::class.java)
        intent.action = FrontDoorActivity.ACTION_GET_AUTH_TOKEN
        intent.putExtra(
            AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response
        )
        intent.putExtra(FrontDoorActivity.KEY_ACCOUNT_TYPE, account.type)
        intent.putExtra(FrontDoorActivity.KEY_AUTH_TYPE, authTokenType)
        intent.putExtra(FrontDoorActivity.KEY_OPTIONS, options)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>
    ): Bundle {
        throw UnsupportedOperationException()
    }

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String?
    ): Bundle? {
        return null
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<String>?,
        options: Bundle
    ): Bundle? {
        val intent = Intent(context, AuthenticatorActivity::class.java)
            .putExtra(AuthenticatorActivity.KEY_ACCOUNT_TYPE, accountType)
            .putExtra(AuthenticatorActivity.KEY_AUTH_TYPE, authTokenType)
            .putExtra(AuthenticatorActivity.KEY_IS_ADDING_NEW_ACCOUNT, true)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            .putExtra(AuthenticatorActivity.KEY_REQUIRED_FEATURES, requiredFeatures)
            .putExtra(AuthenticatorActivity.KEY_OPTIONS, options)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun getAccountRemovalAllowed(
        response: AccountAuthenticatorResponse?,
        account: Account?
    ): Bundle {
        val intent = Intent(context, DeleteIdentityActivity::class.java)
            .putExtra(DeleteIdentityActivity.KEY_ACCOUNT_NAME, account!!.name)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    companion object {
        const val AUTH_TOKEN_TYPE = "org.blockstack.id"
    }
}
