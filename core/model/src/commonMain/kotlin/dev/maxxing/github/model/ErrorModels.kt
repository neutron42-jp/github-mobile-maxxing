package dev.maxxing.github.model

/**
 * Implemented by errors that carry an HTTP status code, so UI layers that cannot see the
 * network module (e.g. core/ui's localized strings) can still describe the failure.
 */
interface HttpStatusError {
    val httpStatusCode: Int

    /**
     * True when [httpStatusCode] was not returned by the server but chosen by the client
     * (e.g. a GraphQL error delivered with HTTP 200), so it should not be shown as an HTTP status.
     */
    val isSyntheticStatus: Boolean get() = false
}
