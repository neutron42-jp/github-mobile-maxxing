package dev.maxxing.github.app

import dev.maxxing.github.model.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SessionLifecycleKeyTest {

    @Test
    fun sessionEffectKeyIsStableAcrossTokenRefreshAndDistinguishesNullLoginSession() {
        val signedOut = sessionEffectKey(null)
        val unresolvedLogin = sessionEffectKey(AuthSession(accessToken = "tok_1", userLogin = null))
        val unresolvedLoginRefreshed = sessionEffectKey(AuthSession(accessToken = "tok_2", userLogin = null))
        val aliceFirst = sessionEffectKey(AuthSession(accessToken = "tok_a1", userLogin = "Alice"))
        val aliceRefreshed = sessionEffectKey(AuthSession(accessToken = "tok_a2", userLogin = "alice"))
        val bob = sessionEffectKey(AuthSession(accessToken = "tok_b1", userLogin = "bob"))

        // A signed-in session without a resolved login must still differ from signed-out
        assertNotEquals(signedOut, unresolvedLogin)
        assertEquals(unresolvedLogin, unresolvedLoginRefreshed)

        // Token refreshes for the same account keep the same key
        assertEquals(aliceFirst, aliceRefreshed)

        // Switching accounts changes the key
        assertNotEquals(aliceFirst, bob)
        assertNotEquals(signedOut, aliceFirst)
    }
}
