package dev.maxxing.github.app.notifications

import dev.maxxing.github.app.IosAppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.maxxing.github.app.iosLog
import platform.Foundation.NSUserDefaults
import kotlin.concurrent.Volatile

const val APNS_ENVIRONMENT_DEVELOPMENT: String = "development"
const val APNS_ENVIRONMENT_PRODUCTION: String = "production"

/**
 * Central-server contract for APNs device registration (the server lives
 * in a separate repository). Only the app-side seam is defined here.
 */
interface PushServerClient {
    suspend fun registerDevice(deviceToken: String, environment: String, userLogin: String)
    suspend fun unregisterDevice(deviceToken: String, environment: String, userLogin: String?)
}

/**
 * Used until a push server endpoint is configured. It accepts calls without doing
 * network I/O so the app remains fully usable; swap it with a real client when
 * the server contract is finalized.
 */
object NoOpPushServerClient : PushServerClient {
    override suspend fun registerDevice(deviceToken: String, environment: String, userLogin: String) = Unit
    override suspend fun unregisterDevice(deviceToken: String, environment: String, userLogin: String?) = Unit
}

/**
 * One device-token ↔ account association that exists (or may exist) on the
 * server. [confirmed] is false while a register request is outstanding or
 * failed, so a crash mid-request still leaves a record that can be
 * compensated by an unregister.
 */
internal data class PushAssociation(
    val deviceToken: String,
    val environment: String,
    val userLogin: String,
    val confirmed: Boolean,
) {
    fun sameTarget(other: PushAssociation): Boolean =
        deviceToken == other.deviceToken &&
            environment == other.environment &&
            userLogin == other.userLogin
}

/**
 * Persistent record of every association the server may hold, so that a
 * sign-out / toggle-off / account switch after a process restart can still
 * unregister the old token, and a failed unregister is never lost (even when
 * several tokens change while the server is unreachable). It deliberately
 * stores **no auth token**: only APNs token, environment and GitHub login.
 */
internal interface PushAssociationStore {
    fun load(): List<PushAssociation>
    fun save(associations: List<PushAssociation>)
}

internal class NSUserDefaultsPushAssociationStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PushAssociationStore {
    override fun load(): List<PushAssociation> =
        (defaults.stringForKey(KEY) ?: return emptyList())
            .lines()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 4 || parts.any { it.isEmpty() }) return@mapNotNull null
                PushAssociation(parts[0], parts[1], parts[2], confirmed = parts[3] == "1")
            }

    override fun save(associations: List<PushAssociation>) {
        if (associations.isEmpty()) {
            defaults.removeObjectForKey(KEY)
        } else {
            defaults.setObject(
                associations.joinToString("\n") {
                    "${it.deviceToken}\t${it.environment}\t${it.userLogin}\t${if (it.confirmed) "1" else "0"}"
                },
                forKey = KEY,
            )
        }
    }

    private companion object {
        const val KEY = "push_server_associations"
    }
}

/**
 * Serialized APNs registration state machine.
 *
 * Invariants:
 *  - the persisted [PushAssociationStore] is the source of truth for what the
 *    server may hold; the session login and `osNotificationsEnabled` are always
 *    re-read from their providers (events only trigger a sync);
 *  - every association other than the desired one is unregistered first, and
 *    nothing new is registered while any unregister is still failing;
 *  - an association is persisted before its register request is sent, and after
 *    the request the desired state is re-evaluated, which compensates a
 *    sign-out / toggle-off / account switch that happened in flight;
 *  - failed requests are retried on the next token event, foreground resume,
 *    account change or launch.
 */
internal class IosPushManager(
    private val client: PushServerClient,
    private val store: PushAssociationStore,
    private val deviceTokenProvider: () -> String?,
    private val sessionLoginProvider: () -> String?,
    private val notificationsEnabledProvider: () -> Boolean,
    private val environmentProvider: () -> String = { APNS_ENVIRONMENT_DEVELOPMENT },
    private val ensureSessionLoaded: suspend () -> Unit = {},
    private val logger: (String) -> Unit = {},
    /**
     * False while no real server client is configured. Nothing is registered
     * or recorded then, so a no-op client can never produce "confirmed" associations.
     */
    private val isServerConfigured: () -> Boolean = { true },
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null

    suspend fun onDeviceTokenReceived(tokenHex: String) {
        val clean = tokenHex.trim()
        if (!isValidDeviceToken(clean)) {
            logger("Ignoring invalid APNs device token")
            return
        }
        if (!ensureLoaded()) return
        mutex.withLock {
            cachedToken = clean
            syncLocked()
        }
    }

    suspend fun onRegistrationFailed(errorMessage: String) {
        // Never block app startup on APNs registration failure.
        logger("APNs registration failed: $errorMessage")
    }

    /** The preference itself is read from the provider; this only triggers a sync. */
    suspend fun onNotificationPreferenceChanged(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = sync()

    suspend fun onForegroundResume() = sync()

    suspend fun onAccountOrSessionChanged() = sync()

    private suspend fun sync() {
        if (!ensureLoaded()) return
        mutex.withLock { syncLocked() }
    }

    private suspend fun ensureLoaded(): Boolean = try {
        ensureSessionLoaded()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger("Unable to load the session before APNs sync: ${error.message}")
        false
    }

    private suspend fun syncLocked() {
        if (!isServerConfigured()) {
            logger("APNs server is not configured; skipping device registration")
            return
        }
        var attempt = 0
        while (attempt++ < MAX_CONVERGENCE_ATTEMPTS) {
            val desired = desired()

            var blocked = false
            for (entry in store.load()) {
                if (desired != null && entry.sameTarget(desired)) continue
                if (unregister(entry)) {
                    remove(entry)
                } else {
                    blocked = true
                }
            }
            // Never create a new association while an older one may still be live.
            if (blocked) return
            if (desired == null) return
            if (store.load().any { it.sameTarget(desired) && it.confirmed }) return

            upsert(desired.copy(confirmed = false))
            if (!register(desired)) return
            upsert(desired.copy(confirmed = true))
            // Loop: re-read the session / preference. If they changed while the
            // request was in flight, the next pass unregisters this association.
        }
        logger("APNs registration did not converge after $MAX_CONVERGENCE_ATTEMPTS attempts")
    }

    private fun upsert(entry: PushAssociation) {
        store.save(store.load().filterNot { it.sameTarget(entry) } + entry)
    }

    private fun remove(entry: PushAssociation) {
        store.save(store.load().filterNot { it.sameTarget(entry) })
    }

    private fun desired(): PushAssociation? {
        if (!notificationsEnabledProvider()) return null
        val login = sessionLoginProvider()?.trim()
            ?.takeIf { it.isNotEmpty() && '\t' !in it && '\n' !in it }
            ?: return null
        // The provider holds the newest token (set synchronously by the bridge), so
        // callbacks that run out of order cannot resurrect an older one.
        val token = (deviceTokenProvider()?.trim() ?: cachedToken)
            ?.takeIf { isValidDeviceToken(it) }
            ?: return null
        return PushAssociation(token, environmentProvider(), login, confirmed = false)
    }

    private suspend fun register(entry: PushAssociation): Boolean = try {
        client.registerDevice(entry.deviceToken, entry.environment, entry.userLogin)
        logger("Registered APNs device token for ${entry.userLogin}")
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger("Failed to register APNs device token: ${error.message}")
        false
    }

    private suspend fun unregister(entry: PushAssociation): Boolean = try {
        client.unregisterDevice(entry.deviceToken, entry.environment, entry.userLogin)
        logger("Unregistered APNs device token for ${entry.userLogin}")
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger("Failed to unregister APNs device token: ${error.message}")
        false
    }

    private companion object {
        const val MAX_CONVERGENCE_ATTEMPTS = 4
    }
}

/** APNs tokens are lowercase/uppercase hex; anything else is not usable. */
internal fun isValidDeviceToken(token: String): Boolean =
    token.length in 32..200 &&
        token.length % 2 == 0 &&
        token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/**
 * Swift-facing bridge called from `AppDelegate`:
 *
 * ```
 * application(_:didRegisterForRemoteNotificationsWithDeviceToken:)
 * application(_:didFailToRegisterForRemoteNotificationsWithError:)
 * ```
 *
 * plus sign-in / sign-out / notification-toggle changes. All work is
 * serialized inside [IosPushManager]; failures are logged and never block the
 * app.
 */
object IosPushBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var apnsEnvironment: String = APNS_ENVIRONMENT_DEVELOPMENT

    /**
     * The seam for the central APNs push server (endpoint not yet configured). Replace it before
     * the first token event (e.g. from `IosAppGraph`) with a real [PushServerClient]. While it is
     * [NoOpPushServerClient] nothing is registered and APNs is not treated as the delivery path.
     */
    @Volatile
    var serverClient: PushServerClient = NoOpPushServerClient

    /** True once APNs is enabled and a real [PushServerClient] has been installed. */
    val isServerConfigured: Boolean
        get() = IosPushFeature.isApnsEnabled && serverClient !== NoOpPushServerClient

    private val manager: IosPushManager by lazy {
        val container = IosAppGraph.container
        IosPushManager(
            client = object : PushServerClient {
                override suspend fun registerDevice(deviceToken: String, environment: String, userLogin: String) =
                    serverClient.registerDevice(deviceToken, environment, userLogin)

                override suspend fun unregisterDevice(deviceToken: String, environment: String, userLogin: String?) =
                    serverClient.unregisterDevice(deviceToken, environment, userLogin)
            },
            isServerConfigured = { isServerConfigured },
            store = NSUserDefaultsPushAssociationStore(),
            deviceTokenProvider = { cachedToken },
            sessionLoginProvider = {
                val session = container.sessionManager.session.value
                if (session == null) {
                    null
                } else {
                    session.userLogin?.takeIf { it.isNotBlank() }
                        ?: container.viewerProfile.value?.login?.takeIf { it.isNotBlank() }
                }
            },
            notificationsEnabledProvider = {
                container.sessionManager.notificationPreferences.value.osNotificationsEnabled
            },
            environmentProvider = { apnsEnvironment },
            ensureSessionLoaded = { IosAppGraph.ensureSessionLoaded() },
            logger = { iosLog(it) },
        )
    }

    /**
     * `development` for debug builds, `production` for TestFlight / App Store.
     * The Swift host knows its configuration, so it sets this before forwarding
     * the device token (defaults to development).
     */
    fun setApnsEnvironment(environment: String) {
        if (!IosPushFeature.isApnsEnabled) return
        apnsEnvironment = environment.trim().ifEmpty { APNS_ENVIRONMENT_DEVELOPMENT }
    }

    fun onDeviceTokenReceived(tokenHex: String) {
        if (!IosPushFeature.isApnsEnabled) return
        cachedToken = tokenHex
        scope.launch { manager.onDeviceTokenReceived(tokenHex) }
    }

    fun onRegistrationFailed(errorMessage: String) {
        if (!IosPushFeature.isApnsEnabled) return
        scope.launch { manager.onRegistrationFailed(errorMessage) }
    }

    fun onNotificationPreferenceChanged(enabled: Boolean) {
        if (!IosPushFeature.isApnsEnabled) return
        scope.launch { manager.onNotificationPreferenceChanged(enabled) }
    }

    fun onForegroundResume() {
        if (!IosPushFeature.isApnsEnabled) return
        scope.launch { manager.onForegroundResume() }
    }

    fun onAccountOrSessionChanged() {
        if (!IosPushFeature.isApnsEnabled) return
        scope.launch { manager.onAccountOrSessionChanged() }
    }

    /** Called after the OS notification permission changes. */
    fun onNotificationAuthorizationChanged() {
        if (!IosPushFeature.isApnsEnabled) return
        scope.launch { manager.onForegroundResume() }
    }
}
