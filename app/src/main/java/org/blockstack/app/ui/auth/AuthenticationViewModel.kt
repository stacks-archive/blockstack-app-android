package org.blockstack.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.blockstack.app.R
import org.blockstack.app.data.AuthRepository
import org.blockstack.app.data.Result

class AuthenticationViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    fun checkUsername(username: String) {
        viewModelScope.launch {
            authRepository.checkUsername(username) { checkResult ->
                if (checkResult is Result.Success) {
                    _loginForm.value = LoginFormState(isUsernameAvailable = true)
                } else {
                    _loginResult.value =
                        LoginResult(error = R.string.invalid_username)
                }
            }
        }
    }

    fun registerUsername(username: String) {
        authRepository.registerUsername(username) { registerResult ->
            if (registerResult is Result.Success) {
                _loginResult.value =
                    LoginResult(success = LoggedInUser(displayName = username))
            }
        }
    }

    fun loginDataChanged(username: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = LoginFormState(
                usernameError = R.string.invalid_username,
                isUsernameAvailable = true
            )
        } else {
            _loginForm.value = LoginFormState(isDataValid = true, isUsernameAvailable = true)
        }
    }

    private fun isUserNameValid(username: String): Boolean {
        return username.length >= 8
    }

    fun restore(text: String) {
        viewModelScope.launch {
            val account = authRepository.restoreIdentity(text)
            _loginResult.value =
                LoginResult(LoggedInUser(account.displayName()))

        }
    }
}

