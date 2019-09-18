package org.blockstack.app.ui.identity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.blockstack.app.data.AuthRepository

/**
 * ViewModel provider factory to instantiate AuthenticationViewModel.
 * Required given AuthenticationViewModel has a non-empty constructor
 */
class IdentityViewModelFactory(val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IdentityViewModel::class.java)) {
            return IdentityViewModel(
                authRepository = AuthRepository.getInstance(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
