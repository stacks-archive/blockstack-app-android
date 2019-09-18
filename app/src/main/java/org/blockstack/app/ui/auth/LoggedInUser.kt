package org.blockstack.app.ui.auth

/**
 * User details post authentication that is exposed to the UI
 */
data class LoggedInUser(
  val displayName: String
  //... other data fields that may be accessible to the UI
)
