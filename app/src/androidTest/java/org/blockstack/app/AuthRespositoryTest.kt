package org.blockstack.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.runBlocking
import org.blockstack.android.sdk.model.BlockstackAccount
import org.blockstack.android.sdk.model.BlockstackIdentity
import org.blockstack.app.data.AuthRepository
import org.blockstack.app.data.Result
import org.blockstack.app.ui.identity.IdentityActivity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kethereum.bip32.generateChildKey
import org.kethereum.bip32.toExtendedKey
import org.kethereum.bip32.toKey
import org.kethereum.bip39.model.MnemonicWords
import org.kethereum.bip39.toSeed
import org.kethereum.bip44.BIP44Element
import java.util.concurrent.CountDownLatch


private val SEED_PHRASE =
    "sound idle panel often situate develop unit text design antenna vendor screen opinion balcony share trigger accuse scatter visa uniform brass update opinion media"
private val BTC_ADDRESS = "1JeTQ5cQjsD57YGcsVFhwT7iuQUXJR6BSk"

@RunWith(AndroidJUnit4::class)
class AuthRespositoryTest {
    @get:Rule
    val rule = ActivityTestRule(IdentityActivity::class.java)

    lateinit var authRespository: AuthRepository


    @Before
    fun setup() {
        authRespository = AuthRepository.getInstance(rule.activity)
    }


    @Test
    fun testRestore() {
        val latch = CountDownLatch(1)
        var result: Result<BlockstackAccount>? = null
        runBlocking {
            authRespository.restoreIdentity(SEED_PHRASE) {
                result = it
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result is Result.Success, `is` (true))
        assertThat((result as Result.Success).data.ownerAddress, `is`(BTC_ADDRESS))
    }

    @Test
    fun testRestore2() {
        val words = MnemonicWords(SEED_PHRASE)
        val identity = BlockstackIdentity(words.toSeed().toKey("m/888'/0'"))
        val keys = identity.identityKeys.generateChildKey(BIP44Element(true, 0))
        val account = BlockstackAccount(null, keys, identity.salt)
        assertThat(account.ownerAddress, `is`(BTC_ADDRESS))
    }
}
