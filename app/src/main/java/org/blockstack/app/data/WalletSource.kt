package org.blockstack.app.data

import android.content.Context
import android.preference.PreferenceManager
import org.kethereum.bip32.model.ExtendedKey
import org.kethereum.bip32.toExtendedKey
import org.kethereum.bip39.dirtyPhraseToMnemonicWords
import org.kethereum.bip39.generateMnemonic
import org.kethereum.bip39.model.MnemonicWords
import org.kethereum.bip39.toSeed
import org.kethereum.bip39.wordlists.WORDLIST_ENGLISH

/**
 * Class that handles authentication w/ checkUsername credentials and retrieves user information.
 */
class WalletSource(private val context: Context) {

    fun create(): MnemonicWords {
        val mnemonic = generateMnemonic(wordList = WORDLIST_ENGLISH)
        val mnemonicWords = MnemonicWords(mnemonic)
        return mnemonicWords
    }

    fun restore(encryptedRecovery: String, password: String): ExtendedKey {
        return restore(decrypt(encryptedRecovery, password))
    }

    private fun decrypt(encryptedRecovery: String, password: String): String {
        return encryptedRecovery
    }

    fun restore(mnemonic: String): ExtendedKey {
        val mnemonicWords = dirtyPhraseToMnemonicWords(mnemonic)
        return mnemonicWords.toSeed().toExtendedKey()
    }

    fun reset() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().apply()
    }

    fun register(username: String, words: MnemonicWords): BlockstackAccount {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("username", username)
            .putString("words", words.toString())
            .apply()
        val keys = words.toSeed().toExtendedKey()
        return BlockstackAccount(username, keys)
    }

    fun lastAccount(): BlockstackAccount? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val username = prefs.getString("username", null)
        val words = prefs.getString("words", null)
        if (username != null && words != null) {
            val keys = MnemonicWords(words).toSeed().toExtendedKey()
            return BlockstackAccount(username, keys)
        } else {
            return null
        }
    }
}

