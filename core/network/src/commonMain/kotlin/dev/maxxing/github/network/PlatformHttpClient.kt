package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Creates a platform-tuned Ktor [HttpClient] with higher per-host concurrency,
 * connection pooling, and keep-alive defaults for GitHub API & CDN traffic.
 */
internal expect fun createDefaultPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit = {}
): HttpClient
