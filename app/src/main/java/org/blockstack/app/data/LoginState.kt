package org.blockstack.app.data

data class LoginState(val authRequest:AuthRequest, val authResponse: String, val permissions: MutableList<Permission>)
