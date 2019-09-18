package org.blockstack.app.ui.auth

/**
 * Data validation state of the checkUsername form.
 */
data class LoginFormState(
    val usernameError: Int? = null,
    val isDataValid: Boolean = false,
    val isUsernameAvailable: Boolean = true
)
