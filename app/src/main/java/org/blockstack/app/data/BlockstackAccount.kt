package org.blockstack.app.data

import org.blockstack.android.sdk.model.UserData
import org.json.JSONObject
import org.kethereum.bip32.model.ExtendedKey
import org.kethereum.crypto.toAddress

data class BlockstackAccount(val username: String, val keys: ExtendedKey) {

    companion object {
        data class MetaData(
            var permissions: List<String> = emptyList(),
            var email: String? = null,
            var profileUrl: String? = null
        )
    }
}


fun BlockstackAccount.toUserData(): UserData {
    return UserData(
        JSONObject().put("identityAddress", this.keys.keyPair.publicKey.toAddress())
            .put("username", this.username)
    )
}
