package org.blockstack.app.data

import android.content.Context
import android.preference.PreferenceManager
import org.blockstack.android.sdk.model.BlockstackAccount
import org.blockstack.android.sdk.model.BlockstackIdentity
import org.kethereum.bip32.generateChildKey
import org.kethereum.bip32.model.ExtendedKey
import org.kethereum.bip32.toExtendedKey
import org.kethereum.bip32.toKey
import org.kethereum.bip39.dirtyPhraseToMnemonicWords
import org.kethereum.bip39.generateMnemonic
import org.kethereum.bip39.model.MnemonicWords
import org.kethereum.bip39.toSeed
import org.kethereum.bip39.wordlists.WORDLIST_ENGLISH
import org.kethereum.bip44.BIP44Element

/**
 * Class that handles authentication w/ checkUsername credentials and retrieves user information.
 */
class WalletSource(private val context: Context) {

    fun create(): MnemonicWords {
        val mnemonic = generateMnemonic(wordList = WORDLIST_ENGLISH)
        val mnemonicWords = MnemonicWords(mnemonic)
        return mnemonicWords
    }

    fun restore(encryptedRecovery: String, password: String): BlockstackAccount {
        return restore(decrypt(encryptedRecovery, password))
    }

    private fun decrypt(encryptedRecovery: String, password: String): String {
        return encryptedRecovery
    }

    fun restore(mnemonic: String): BlockstackAccount {
        val mnemonicWords = dirtyPhraseToMnemonicWords(mnemonic)
        val identity = BlockstackIdentity(mnemonicWords.toSeed().toKey("m/888'/0'"))
        val keys = identity.identityKeys.generateChildKey(BIP44Element(true, 0))
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("username", null)
            .putString("words", mnemonicWords.toString())
            .apply()
        return BlockstackAccount(null, keys, identity.salt)

    }

    fun reset() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().apply()
    }

    fun register(username: String, words: MnemonicWords): BlockstackAccount {
        save(username, words.toString())
        val identity = BlockstackIdentity(words.toSeed().toKey("m/888'/0'"))
        val keys = identity.identityKeys.generateChildKey(BIP44Element(true, 0))
        return BlockstackAccount(username, keys, identity.salt)
    }

    fun lastAccount(): BlockstackAccount? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val username = prefs.getString("username", null)
        val words = prefs.getString("words", null)
        if (words != null) {
            val identity = BlockstackIdentity(MnemonicWords(words).toSeed().toKey("m/888'/0'"))
            val keys = identity.identityKeys.generateChildKey(BIP44Element(true, 0))
            return BlockstackAccount(username, keys, identity.salt)
        } else {
            return null
        }
    }

    fun save(username: String, words: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("username", username)
            .putString("words", words)
            .apply()
    }
}

