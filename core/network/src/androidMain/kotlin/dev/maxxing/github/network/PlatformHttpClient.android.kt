package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit

internal actual fun createDefaultPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            dispatcher(
                Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                }
            )
            retryOnConnectionFailure(true)
        }
    }
    block()
}
