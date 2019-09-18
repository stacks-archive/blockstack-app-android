package org.blockstack.app.data

data class AuthRequest(
    val domain: String,
    val transitKey: String,
    val redirectUrl: String,
    val scopes: Array<String>
)
