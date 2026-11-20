package dev.maxxing.github.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Number of trailing UTF-16 characters the Actions log viewer keeps. */
internal const val JOB_LOG_TAIL_CHARS = 40_000

/** Prefix the viewer shows when an Actions log exceeded [JOB_LOG_TAIL_CHARS]. */
internal const val JOB_LOG_TRUNCATION_MARKER = "... (truncated to latest 40 KB) ...\n"

/** Fixed read size; the retained tail is independent of the response size. */
internal const val JOB_LOG_READ_BUFFER_BYTES = 8 * 1024

/**
 * Reads an Actions log body as a stream and retains only the trailing [limit] characters.
 *
 * The response is never materialized in full: peak retention is the character ring plus one read buffer.
 * GitHub serves Actions logs as UTF-8 text; decoding is incremental so a multi-byte character split
 * across network chunks is not corrupted. [HttpResponse.bodyAsChannel] is consumed inside the caller's
 * `HttpStatement.execute` block, so the statement/body is cleaned up when the coroutine is cancelled or
 * the read fails.
 */
internal suspend fun HttpResponse.readJobLogTail(limit: Int = JOB_LOG_TAIL_CHARS): String {
    require(limit > 0) { "limit must be positive" }
    val channel = bodyAsChannel()
    val tail = BoundedCharTail(limit)
    val decoder = Utf8ChunkDecoder()
    val buffer = ByteArray(JOB_LOG_READ_BUFFER_BYTES)
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) continue
            decoder.feed(buffer, read) { chars -> tail.append(chars) }
        }
        decoder.finish { chars -> tail.append(chars) }
    } catch (cause: Throwable) {
        // Release the body channel immediately; `HttpStatement.execute` also cleans up the response.
        channel.cancel(cause)
        throw cause
    }
    val text = tail.text()
    return if (tail.totalChars > limit) JOB_LOG_TRUNCATION_MARKER + text else text
}

/**
 * Fixed-size ring of the most recent characters seen while streaming.
 *
 * Memory is `2 * limit` bytes on the JVM regardless of how large the response is.
 */
internal class BoundedCharTail(private val limit: Int) {
    private val buffer = CharArray(limit)
    private var writeIndex = 0
    private var size = 0

    /** Total characters observed, including those that fell out of the ring. */
    var totalChars: Long = 0L
        private set

    init {
        require(limit > 0) { "limit must be positive" }
    }

    fun append(chars: CharSequence) {
        val length = chars.length
        if (length == 0) return
        totalChars += length
        if (length >= limit) {
            val offset = length - limit
            for (i in 0 until limit) {
                buffer[i] = chars[offset + i]
            }
            writeIndex = 0
            size = limit
            return
        }
        for (i in 0 until length) {
            buffer[writeIndex] = chars[i]
            writeIndex++
            if (writeIndex == limit) writeIndex = 0
        }
        size = minOf(limit, size + length)
    }

    fun text(): String {
        if (size == 0) return ""
        val start = if (size == limit) writeIndex else 0
        val chars = CharArray(size)
        for (i in 0 until size) {
            chars[i] = buffer[(start + i) % limit]
        }
        return chars.concatToString()
    }
}

/**
 * Incremental UTF-8 decoder that carries at most three bytes of a split sequence between chunks.
 *
 * Malformed input is replaced with U+FFFD, matching `String.decodeToString()`/`bodyAsText()`.
 */
internal class Utf8ChunkDecoder {
    private val pending = ByteArray(UTF8_MAX_SEQUENCE_BYTES)
    private var pendingSize = 0

    fun feed(bytes: ByteArray, length: Int, sink: (String) -> Unit) {
        require(length in 0..bytes.size) { "length must be within the byte array" }
        if (length == 0) return
        val combined: ByteArray
        val total: Int
        if (pendingSize == 0) {
            combined = bytes
            total = length
        } else {
            combined = ByteArray(pendingSize + length)
            pending.copyInto(combined, 0, 0, pendingSize)
            bytes.copyInto(combined, pendingSize, 0, length)
            total = combined.size
        }
        val incomplete = incompleteUtf8SuffixLength(combined, total)
        val complete = total - incomplete
        if (complete > 0) {
            sink(combined.decodeToString(0, complete))
        }
        if (incomplete > 0) {
            combined.copyInto(pending, 0, complete, total)
        }
        pendingSize = incomplete
    }

    fun finish(sink: (String) -> Unit) {
        if (pendingSize == 0) return
        sink(pending.decodeToString(0, pendingSize))
        pendingSize = 0
    }

    private companion object {
        const val UTF8_MAX_SEQUENCE_BYTES = 4
    }
}

/**
 * Number of trailing bytes that begin an incomplete UTF-8 sequence, or 0 when the chunk ends on a boundary.
 *
 * Only lead bytes within the last four bytes are considered; a trailing continuation byte without a lead
 * is handed to the decoder so it produces the same replacement characters as a full-buffer decode.
 */
private fun incompleteUtf8SuffixLength(bytes: ByteArray, length: Int): Int {
    var back = 0
    val maxBack = minOf(3, length - 1)
    while (back <= maxBack) {
        val byte = bytes[length - 1 - back].toInt() and 0xFF
        if (byte and 0xC0 != 0x80) {
            val expected = when {
                byte and 0x80 == 0x00 -> 1
                byte and 0xE0 == 0xC0 -> 2
                byte and 0xF0 == 0xE0 -> 3
                byte and 0xF8 == 0xF0 -> 4
                else -> return 0
            }
            val available = back + 1
            return if (available < expected) available else 0
        }
        back++
    }
    return 0
}
