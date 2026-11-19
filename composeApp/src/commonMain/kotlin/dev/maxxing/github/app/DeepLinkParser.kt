package dev.maxxing.github.app

/**
 * Strict iOS-entry deep-link parser.
 *
 * Used only by the iOS entry points (`IosDeepLinkBridge.handleUrl` and the
 * notification-tap payload parser). Android keeps using the lenient
 * `parseAppDeepLink` (`GitHubUrlParser.kt`) directly from its `MainActivity`.
 *
 * Accepted URLs are exactly `githubmaxxing://thread/{owner}/{repo}/{number}`:
 *  - scheme `githubmaxxing` and host `thread` (case-insensitive, as URL schemes
 *    and hosts are); no user-info and no port on the authority;
 *  - the path has exactly three non-empty segments (one trailing slash is
 *    tolerated), where the number is ASCII digits only and positive;
 *  - owner / repo are percent-decoded as strict UTF-8 and must be non-blank,
 *    without `/`, whitespace or control characters;
 *  - an optional query or fragment is ignored.
 *
 * Everything else (extra or empty segments, `0`, `-1`, `+1`, a malformed
 * percent escape, invalid UTF-8, surrounding whitespace, ...) returns `null`.
 */
fun parseStrictDeepLink(url: String?): IssuePullTarget? {
    val raw = url ?: return null
    val schemeSeparator = raw.indexOf("://")
    if (schemeSeparator <= 0) return null
    if (!raw.substring(0, schemeSeparator).equals(SCHEME, ignoreCase = true)) return null

    var remainder = raw.substring(schemeSeparator + 3)
    val end = listOf(remainder.indexOf('?'), remainder.indexOf('#'))
        .filter { it >= 0 }
        .minOrNull()
    if (end != null) remainder = remainder.substring(0, end)

    val pathStart = remainder.indexOf('/')
    if (pathStart <= 0) return null
    if (!remainder.substring(0, pathStart).equals(HOST, ignoreCase = true)) return null

    val segments = remainder.substring(pathStart + 1).split('/').toMutableList()
    if (segments.isNotEmpty() && segments.last().isEmpty()) segments.removeAt(segments.lastIndex)
    if (segments.size != 3 || segments.any { it.isEmpty() }) return null

    val owner = percentDecodeStrict(segments[0])?.takeIf { isValidOwnerOrRepo(it) } ?: return null
    val repo = percentDecodeStrict(segments[1])?.takeIf { isValidOwnerOrRepo(it) } ?: return null
    val numberText = percentDecodeStrict(segments[2]) ?: return null
    if (numberText.isEmpty() || numberText.any { it !in '0'..'9' }) return null
    val number = numberText.toIntOrNull()?.takeIf { it > 0 } ?: return null

    return IssuePullTarget(owner = owner, repo = repo, number = number)
}

private const val SCHEME = "githubmaxxing"
private const val HOST = "thread"

private fun isValidOwnerOrRepo(value: String): Boolean =
    value.isNotBlank() &&
        '/' !in value &&
        value.none { it.isWhitespace() || it.isISOControl() }

/**
 * Builds the canonical deep link for a notification payload. Returns `null`
 * when there is no valid issue/PR number, so callers can never produce
 * `githubmaxxing://thread/owner/repo/null`.
 */
fun buildThreadDeepLinkUrl(owner: String, repo: String, number: Int?): String? {
    if (number == null || number <= 0) return null
    val cleanOwner = owner.trim()
    val cleanRepo = repo.trim()
    if (!isValidOwnerOrRepo(cleanOwner) || !isValidOwnerOrRepo(cleanRepo)) return null
    return "githubmaxxing://$HOST/${encodePathSegment(cleanOwner)}/${encodePathSegment(cleanRepo)}/$number"
}

/** Keys used in the `userInfo` dictionary of GitHub notification payloads. */
const val NOTIFICATION_KEY_THREAD_ID: String = "thread_id"
const val NOTIFICATION_KEY_DEEP_LINK: String = "deep_link"
const val NOTIFICATION_KEY_OWNER: String = "owner"
const val NOTIFICATION_KEY_REPO: String = "repo"
const val NOTIFICATION_KEY_SUBJECT_NUMBER: String = "subject_number"

/** Where a notification tap should navigate. */
sealed interface NotificationTapTarget {
    /** Open the issue/PR detail for a fully formed target. */
    data class Thread(val target: IssuePullTarget) : NotificationTapTarget

    /** Open the Inbox (e.g. `subjectNumber == null` / no usable deep link). */
    data object Inbox : NotificationTapTarget

    /** Not a notification we know how to route. */
    data object None : NotificationTapTarget
}

/**
 * Parses the `userInfo` of a notification tap.
 *
 * A `deep_link` is preferred, then the structured `owner` / `repo` /
 * `subject_number` fields. If neither yields a valid thread but the payload
 * still looks like a GitHub notification (it has `thread_id`, a `deep_link`
 * key or structured fields), the tap opens the Inbox. Truly unknown payloads
 * produce [NotificationTapTarget.None].
 */
fun parseNotificationTapTarget(userInfo: Map<String, String?>): NotificationTapTarget {
    val deepLink = userInfo[NOTIFICATION_KEY_DEEP_LINK]?.trim().orEmpty()
    if (deepLink.isNotEmpty()) {
        parseStrictDeepLink(deepLink)?.let { return NotificationTapTarget.Thread(it) }
    }

    val owner = userInfo[NOTIFICATION_KEY_OWNER]?.trim().orEmpty()
    val repo = userInfo[NOTIFICATION_KEY_REPO]?.trim().orEmpty()
    val number = userInfo[NOTIFICATION_KEY_SUBJECT_NUMBER]
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
    if (owner.isNotEmpty() && repo.isNotEmpty() && number != null) {
        buildThreadDeepLinkUrl(owner, repo, number)
            ?.let { parseStrictDeepLink(it) }
            ?.let { return NotificationTapTarget.Thread(it) }
    }

    val looksLikeGitHubNotification =
        !userInfo[NOTIFICATION_KEY_THREAD_ID].isNullOrBlank() ||
            deepLink.isNotEmpty() ||
            !userInfo[NOTIFICATION_KEY_OWNER].isNullOrBlank() ||
            !userInfo[NOTIFICATION_KEY_REPO].isNullOrBlank() ||
            !userInfo[NOTIFICATION_KEY_SUBJECT_NUMBER].isNullOrBlank()
    return if (looksLikeGitHubNotification) NotificationTapTarget.Inbox else NotificationTapTarget.None
}

// ---- Pure-Kotlin percent encoding / decoding -------------------------------

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun encodePathSegment(value: String): String {
    val builder = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xFF
        val char = unsigned.toChar()
        val unreserved = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
            char == '-' || char == '.' || char == '_' || char == '~'
        if (unreserved) {
            builder.append(char)
        } else {
            builder.append('%')
            builder.append(HEX_DIGITS[unsigned shr 4])
            builder.append(HEX_DIGITS[unsigned and 0x0F])
        }
    }
    return builder.toString()
}

/** Strict UTF-8 percent decoding; `null` for a malformed escape or invalid UTF-8. */
private fun percentDecodeStrict(value: String): String? {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%') {
            if (index + 2 >= value.length) return null
            val high = hexValue(value[index + 1]) ?: return null
            val low = hexValue(value[index + 2]) ?: return null
            bytes.add(((high shl 4) or low).toByte())
            index += 3
        } else {
            bytes.addAll(char.toString().encodeToByteArray().toList())
            index++
        }
    }
    return try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    } catch (_: Exception) {
        null
    }
}

private fun hexValue(char: Char): Int? = when (char) {
    in '0'..'9' -> char - '0'
    in 'a'..'f' -> char - 'a' + 10
    in 'A'..'F' -> char - 'A' + 10
    else -> null
}
