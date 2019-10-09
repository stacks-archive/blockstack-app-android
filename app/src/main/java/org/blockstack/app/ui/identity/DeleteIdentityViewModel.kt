package org.blockstack.app.ui.identity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.blockstack.app.data.AuthRepository

class DeleteIdentityViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _userData = MutableLiveData<Account>()
    val userData: LiveData<Account> = _userData

    fun loadCurrentUser() {
        viewModelScope.launch {
            authRepository.loadAccount()
            if (authRepository.isLoggedIn) {
                _userData.value = Account(
                    authRepository.account?.displayName()
                        ?: "unknown user",
                    authRepository.account?.user?.profile?.avatarImage ?: "",
                    authRepository.account?.user?.profile?.description ?: "",
                    "encrypted seeds words"
                )
            } else {
                _userData.value = null
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _userData.value = null
    }
}
