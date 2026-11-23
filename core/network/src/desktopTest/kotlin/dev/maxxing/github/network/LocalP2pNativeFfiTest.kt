package dev.maxxing.github.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalP2pNativeFfiTest {

    @Test
    fun loadsNativeLibraryAndDerivesEndpointId() {
        assertTrue(LocalP2pClient.isSupported)
        val identity = LocalP2pClient.generateClientIdentity()
        assertEquals(64, identity.secretKeyHex.length)
        assertTrue(identity.endpointId.isNotBlank())
        assertEquals(
            identity.endpointId,
            LocalP2pClient.deriveClientEndpointId(identity.secretKeyHex)
        )
    }
}
