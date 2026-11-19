package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

internal actual fun createDefaultPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest {
            setTimeoutInterval(25.0)
        }
        configureSession {
            HTTPMaximumConnectionsPerHost = 12
            timeoutIntervalForRequest = 25.0
        }
    }
    block()
}
