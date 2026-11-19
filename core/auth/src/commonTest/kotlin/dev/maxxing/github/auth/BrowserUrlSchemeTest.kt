package dev.maxxing.github.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserUrlSchemeTest {

    @Test
    fun allowsOnlyHttpAndHttpsSchemes() {
        assertTrue(isAllowedBrowserUrlScheme("https"))
        assertTrue(isAllowedBrowserUrlScheme("http"))
        assertTrue(isAllowedBrowserUrlScheme("HTTPS"))
        assertTrue(isAllowedBrowserUrlScheme("Http"))

        assertFalse(isAllowedBrowserUrlScheme(null))
        assertFalse(isAllowedBrowserUrlScheme(""))
        assertFalse(isAllowedBrowserUrlScheme("javascript"))
        assertFalse(isAllowedBrowserUrlScheme("file"))
        assertFalse(isAllowedBrowserUrlScheme("intent"))
        assertFalse(isAllowedBrowserUrlScheme("content"))
        assertFalse(isAllowedBrowserUrlScheme("tel"))
    }
}
