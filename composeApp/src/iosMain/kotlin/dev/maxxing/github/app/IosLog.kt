package dev.maxxing.github.app

import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * Logs [message] with `NSLog`.
 *
 * `NSLog` is variadic, and Kotlin/Native does not bridge a Kotlin `String` passed
 * as a `%@` argument to an `NSString`: the raw Kotlin string object is read as an
 * Objective-C object and the process crashes with `EXC_BAD_ACCESS`. The message
 * is therefore wrapped in an `NSString` and always passed as an argument, never
 * as the format string (so a `%` in the message is harmless).
 */
internal fun iosLog(message: String) {
    NSLog("%@", NSString.create(string = message))
}
