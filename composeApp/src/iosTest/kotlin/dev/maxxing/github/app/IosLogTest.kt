package dev.maxxing.github.app

import kotlin.test.Test

class IosLogTest {

    // Regression: `NSLog("%@", kotlinString)` crashes with EXC_BAD_ACCESS because a
    // Kotlin String is not bridged inside a variadic call. Reaching the end of
    // these tests without a crash is the assertion.
    @Test
    fun logsPlainMessageWithoutCrashing() {
        iosLog("plain message")
    }

    @Test
    fun logsMessageContainingFormatSpecifiersWithoutCrashing() {
        iosLog("100% done %@ %s %d \u65e5\u672c\u8a9e")
    }
}
