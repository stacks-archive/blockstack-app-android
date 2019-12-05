package org.blockstack.app.data

import org.blockstack.android.sdk.model.BlockstackAccount
import org.blockstack.android.sdk.model.UserData
import org.json.JSONObject

data class ExtendedBlockstackAccount(val blockstackAccount: BlockstackAccount, val user: UserData) {
    val settings: IdentitySettings = IdentitySettings(0)
    val gaiaHubUrl: String = "https://hub.blockstack.org"

    fun displayName(): String =
        emptyToNull(this.user.profile?.name)
            ?: this.user.json.getStringOrNull("username")
            ?: this.user.decentralizedID
            ?: "(No name set)"

    private fun emptyToNull(name: String?): String? {
        if (name == null) {
            return null
        } else if (name.isEmpty()) {
            return null
        } else {
            return name
        }
    }
}

private fun JSONObject.getStringOrNull(name: String): String? {
    return if (isNull(name)) {
        null
    } else {
        getString(name)
    }
}
