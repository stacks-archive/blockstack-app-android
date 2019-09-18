package org.blockstack.app.ui.identity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.blockstack.app.data.AuthRepository
import org.kethereum.crypto.toAddress

class IdentityViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _userData = MutableLiveData<Account>()
    val userData: LiveData<Account> = _userData

    fun loadCurrentUser() {
        if (authRepository.isLoggedIn) {
            _userData.value = Account(
                authRepository.user?.profile?.name
                    ?: authRepository.user?.decentralizedID
                    ?: authRepository.account?.keys?.keyPair?.toAddress()?.cleanHex
                    ?: "unknown user",
                authRepository.user?.profile?.avatarImage ?: "",
                authRepository.user?.profile?.description ?: ""
            )
        } else {
            _userData.value = null
        }
    }

    fun logout() {
        authRepository.logout()
        _userData.value = null
    }
}
