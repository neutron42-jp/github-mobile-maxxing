package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

internal actual fun createDefaultPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(CIO) {
    engine {
        maxConnectionsCount = 64
        endpoint.apply {
            maxConnectionsPerRoute = 16
            pipelineMaxSize = 20
            keepAliveTime = 30_000
            connectTimeout = 10_000
            connectAttempts = 2
        }
    }
    block()
}
