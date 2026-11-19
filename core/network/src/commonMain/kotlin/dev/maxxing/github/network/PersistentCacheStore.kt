package dev.maxxing.github.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lightweight, best-effort disk store for Stale-While-Revalidate (SWR) response caching.
 * All operations must be safe to call on background dispatchers and never throw on I/O errors.
 */
interface PersistentCacheStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, payload: String)
    suspend fun clearAll()
}

object NoOpPersistentCacheStore : PersistentCacheStore {
    override suspend fun read(key: String): String? = null
    override suspend fun write(key: String, payload: String) = Unit
    override suspend fun clearAll() = Unit
}

expect fun createDefaultPersistentCacheStore(): PersistentCacheStore

internal val SwrCacheJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

internal inline fun <reified T> encodeSwrPayload(value: T): String? =
    runCatching { SwrCacheJson.encodeToString(value) }.getOrNull()

internal inline fun <reified T> decodeSwrPayload(raw: String): T? =
    runCatching { SwrCacheJson.decodeFromString<T>(raw) }.getOrNull()

internal fun sanitizeCacheKeyToFileName(key: String): String {
    val sb = StringBuilder(key.length + 8)
    for (ch in key) {
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-' || ch == '.' -> sb.append(ch)
            else -> {
                sb.append('_')
                sb.append(ch.code.toString(16))
            }
        }
    }
    return sb.toString().take(180) + ".json"
}
