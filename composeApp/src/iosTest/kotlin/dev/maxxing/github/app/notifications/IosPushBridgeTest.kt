package dev.maxxing.github.app.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPushBridgeTest {

    private val tokenA = "ab".repeat(32)
    private val tokenB = "cd".repeat(32)

    @Test
    fun registersWhenAuthenticatedAndNotificationsEnabled() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        assertEquals(listOf("register:$tokenA:development:alice"), fixture.client.calls)
        assertEquals(listOf(confirmed(tokenA, "alice")), fixture.store.items)

        // A no-op sync must not re-register.
        fixture.manager.onForegroundResume()
        assertEquals(1, fixture.client.calls.size)
    }

    @Test
    fun doesNotRegisterWhenSignedOut() = runTest {
        val fixture = Fixture(login = null)
        fixture.manager.onDeviceTokenReceived(tokenA)
        assertTrue(fixture.client.calls.isEmpty())
    }

    @Test
    fun doesNotRegisterWhenOsNotificationsDisabled() = runTest {
        val fixture = Fixture(enabled = false)
        fixture.manager.onDeviceTokenReceived(tokenA)
        assertTrue(fixture.client.calls.isEmpty())

        // Turning notifications on from Settings registers immediately.
        fixture.enabled = true
        fixture.manager.onNotificationPreferenceChanged(true)
        assertEquals(listOf("register:$tokenA:development:alice"), fixture.client.calls)
    }

    @Test
    fun invalidDeviceTokenIsIgnored() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived("not-a-hex-token")
        fixture.manager.onDeviceTokenReceived("")
        fixture.manager.onDeviceTokenReceived("zz".repeat(32))
        assertTrue(fixture.client.calls.isEmpty())
    }

    @Test
    fun registrationFailureDoesNotThrowAndCanRetry() = runTest {
        val fixture = Fixture()
        fixture.client.failRegister = true
        fixture.manager.onDeviceTokenReceived(tokenA)
        assertEquals(1, fixture.client.calls.size)

        fixture.client.failRegister = false
        fixture.manager.onForegroundResume()
        assertEquals(2, fixture.client.calls.size)
        assertTrue(fixture.client.calls.last().startsWith("register:"))
    }

    @Test
    fun signOutUnregistersCurrentAssociation() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.login = null
        fixture.manager.onAccountOrSessionChanged()

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )
        assertEquals(emptyList(), fixture.store.items)
    }

    @Test
    fun toggleOffUnregistersCurrentAssociation() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.enabled = false
        fixture.manager.onNotificationPreferenceChanged(false)

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )
    }

    @Test
    fun registrationCompletingAfterLogoutCompensatesWithUnregister() = runTest {
        val fixture = Fixture()
        val gate = CompletableDeferred<Unit>()
        fixture.client.registerGate = gate

        val job = launch { fixture.manager.onDeviceTokenReceived(tokenA) }
        runCurrent()
        assertEquals(0, fixture.client.calls.size)

        // Sign out while the register request is in flight.
        fixture.login = null
        gate.complete(Unit)
        advanceUntilIdle()
        job.join()

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )

        // The next sync must not register the signed-out account.
        fixture.manager.onForegroundResume()
        assertEquals(2, fixture.client.calls.size)
    }

    @Test
    fun registrationCompletingAfterToggleOffCompensatesWithUnregister() = runTest {
        val fixture = Fixture()
        val gate = CompletableDeferred<Unit>()
        fixture.client.registerGate = gate

        val job = launch { fixture.manager.onDeviceTokenReceived(tokenA) }
        runCurrent()

        fixture.enabled = false
        gate.complete(Unit)
        advanceUntilIdle()
        job.join()

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )
    }

    @Test
    fun accountSwitchUnregistersOldLoginBeforeRegisteringNewOne() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.login = "bob"
        fixture.manager.onAccountOrSessionChanged()

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
                "register:$tokenA:development:bob",
            ),
            fixture.client.calls,
        )
    }

    @Test
    fun tokenRefreshUnregistersOldTokenBeforeRegisteringNewOne() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.manager.onDeviceTokenReceived(tokenB)

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
                "register:$tokenB:development:alice",
            ),
            fixture.client.calls,
        )
    }

    @Test
    fun unregisterFailureIsPersistedAndRetriedOnForegroundResume() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.login = null
        fixture.client.failUnregister = true
        fixture.manager.onAccountOrSessionChanged()
        assertEquals(listOf(confirmed(tokenA, "alice")), fixture.store.items)

        fixture.client.failUnregister = false
        fixture.manager.onForegroundResume()

        assertEquals(emptyList(), fixture.store.items)
        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )
    }

    @Test
    fun accountSwitchDoesNotRegisterNewAccountWhileOldUnregisterFails() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.client.failUnregister = true
        fixture.login = "bob"
        fixture.manager.onAccountOrSessionChanged()

        // The plan requires the old association to be gone before registering the new one.
        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )
        assertEquals(listOf(confirmed(tokenA, "alice")), fixture.store.items)

        fixture.client.failUnregister = false
        fixture.manager.onForegroundResume()
        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
                "register:$tokenA:development:bob",
            ),
            fixture.client.calls,
        )
        assertEquals(listOf(confirmed(tokenA, "bob")), fixture.store.items)
    }

    @Test
    fun tokensChangingWhileUnregisterFailsAreAllRetained() = runTest {
        val fixture = Fixture()
        val tokenC = "ef".repeat(32)
        fixture.manager.onDeviceTokenReceived(tokenA)

        fixture.client.failUnregister = true
        fixture.manager.onDeviceTokenReceived(tokenB)
        fixture.manager.onDeviceTokenReceived(tokenC)
        assertEquals(listOf(confirmed(tokenA, "alice")), fixture.store.items)

        fixture.client.failUnregister = false
        fixture.manager.onForegroundResume()
        assertEquals(listOf(confirmed(tokenC, "alice")), fixture.store.items)
        assertTrue("unregister:$tokenA:development:alice" in fixture.client.calls.drop(3))
    }

    @Test
    fun signOutAfterRestartUnregistersAssociationLoadedFromStore() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        // "Restart": a fresh manager over the same persisted store, with no
        // cached APNs token and the user already signed out.
        fixture.login = null
        val restarted = fixture.newManager()
        restarted.onForegroundResume()

        assertEquals(
            listOf(
                "register:$tokenA:development:alice",
                "unregister:$tokenA:development:alice",
            ),
            fixture.client.calls,
        )
        assertEquals(emptyList(), fixture.store.items)
    }

    @Test
    fun crashDuringRegisterLeavesCompensableRecord() = runTest {
        val fixture = Fixture()
        fixture.client.failRegister = true
        fixture.manager.onDeviceTokenReceived(tokenA)
        assertEquals(listOf(PushAssociation(tokenA, "development", "alice", confirmed = false)), fixture.store.items)

        fixture.login = null
        fixture.newManager().onAccountOrSessionChanged()
        assertEquals("unregister:$tokenA:development:alice", fixture.client.calls.last())
        assertEquals(emptyList(), fixture.store.items)
    }

    @Test
    fun staleTokenEventDoesNotOverrideNewerProviderToken() = runTest {
        val fixture = Fixture()
        // The bridge publishes the newest token synchronously, then the
        // callbacks run in reverse order.
        fixture.token = tokenB
        fixture.manager.onDeviceTokenReceived(tokenB)
        fixture.manager.onDeviceTokenReceived(tokenA)
        fixture.manager.onForegroundResume()

        assertEquals(listOf(confirmed(tokenB, "alice")), fixture.store.items)
        assertEquals(listOf("register:$tokenB:development:alice"), fixture.client.calls)
    }

    @Test
    fun latestPreferenceIsReadFromProviderNotFromEvents() = runTest {
        val fixture = Fixture()
        fixture.manager.onDeviceTokenReceived(tokenA)

        // Events can arrive out of order; the provider value wins.
        fixture.enabled = false
        fixture.manager.onNotificationPreferenceChanged(true)

        assertEquals("unregister:$tokenA:development:alice", fixture.client.calls.last())
    }

    @Test
    fun unconfiguredServerRegistersNothingAndRecordsNothing() = runTest {
        val fixture = Fixture(serverConfigured = false)
        fixture.manager.onDeviceTokenReceived(tokenA)
        fixture.manager.onForegroundResume()

        assertTrue(fixture.client.calls.isEmpty())
        assertEquals(emptyList(), fixture.store.items)

        // Once a server is configured the same token registers normally.
        fixture.serverConfigured = true
        fixture.manager.onForegroundResume()
        assertEquals(listOf("register:$tokenA:development:alice"), fixture.client.calls)
    }

    @Test
    fun registrationFailedCallbackOnlyLogs() = runTest {
        val fixture = Fixture()
        fixture.manager.onRegistrationFailed("simulator has no APNs entitlement")
        assertTrue(fixture.client.calls.isEmpty())
    }

    @Test
    fun disabledApnsSwitchGatesBridgeCalls() = runTest {
        val fakeClient = FakePushServerClient()
        val previousClient = IosPushBridge.serverClient
        try {
            IosPushBridge.serverClient = fakeClient
            // Master switch is false by default, so the bridge never treats a client as active
            // and drops APNs registration callbacks without invoking the server client.
            kotlin.test.assertFalse(IosPushFeature.isApnsEnabled)
            kotlin.test.assertFalse(IosPushBridge.isServerConfigured)
            IosPushBridge.setApnsEnvironment("production")
            IosPushBridge.onDeviceTokenReceived(tokenA)
            IosPushBridge.onRegistrationFailed("ignored")
            IosPushBridge.onNotificationPreferenceChanged(true)
            IosPushBridge.onForegroundResume()
            IosPushBridge.onAccountOrSessionChanged()
            IosPushBridge.onNotificationAuthorizationChanged()
            advanceUntilIdle()
            assertTrue(fakeClient.calls.isEmpty())
        } finally {
            IosPushBridge.serverClient = previousClient
        }
    }

    private inner class Fixture(
        var login: String? = "alice",
        var enabled: Boolean = true,
        serverConfigured: Boolean = true,
    ) {
        var serverConfigured: Boolean = serverConfigured
        val client = FakePushServerClient()
        val store = FakePushAssociationStore()
        var token: String? = null

        fun newManager() = IosPushManager(
            client = client,
            store = store,
            deviceTokenProvider = { token },
            sessionLoginProvider = { login },
            notificationsEnabledProvider = { enabled },
            environmentProvider = { "development" },
            isServerConfigured = { serverConfigured },
        )

        val manager = newManager()
    }
}

private class FakePushServerClient : PushServerClient {
    val calls = mutableListOf<String>()
    var registerGate: CompletableDeferred<Unit>? = null
    var unregisterGate: CompletableDeferred<Unit>? = null
    var failRegister = false
    var failUnregister = false

    override suspend fun registerDevice(deviceToken: String, environment: String, userLogin: String) {
        registerGate?.await()
        calls += "register:$deviceToken:$environment:$userLogin"
        if (failRegister) throw IllegalStateException("register failed")
    }

    override suspend fun unregisterDevice(
        deviceToken: String,
        environment: String,
        userLogin: String?,
    ) {
        unregisterGate?.await()
        calls += "unregister:$deviceToken:$environment:${userLogin ?: "null"}"
        if (failUnregister) throw IllegalStateException("unregister failed")
    }
}

private fun confirmed(token: String, login: String) =
    PushAssociation(token, "development", login, confirmed = true)

private class FakePushAssociationStore : PushAssociationStore {
    var items: List<PushAssociation> = emptyList()

    override fun load(): List<PushAssociation> = items

    override fun save(associations: List<PushAssociation>) {
        items = associations
    }
}
