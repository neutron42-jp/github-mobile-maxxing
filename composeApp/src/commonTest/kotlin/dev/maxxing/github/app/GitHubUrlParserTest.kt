package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GitHubUrlParserTest {
    @Test
    fun `parses internal notification thread deep links`() {
        val parsed = parseAppDeepLink("githubmaxxing://thread/neutron42-jp/github-mobile-maxxing/12")
        val issueLink = assertIs<AppDeepLink.IssueOrPull>(parsed)
        assertEquals(true, issueLink.fromNotification)
        assertEquals("neutron42-jp", issueLink.target.owner)
        assertEquals("github-mobile-maxxing", issueLink.target.repo)
        assertEquals(12, issueLink.target.number)
    }

    @Test
    // iOS (Kotlin/Native) test names must not contain commas; renamed from the upstream name for that reason.
    fun `parses github issue and pull request and repository and user and reserved urls`() {
        val issue = assertIs<AppDeepLink.IssueOrPull>(
            parseAppDeepLink("https://github.com/octocat/Hello-World/issues/42")
        )
        assertEquals(false, issue.fromNotification)
        assertEquals("octocat", issue.target.owner)
        assertEquals("Hello-World", issue.target.repo)
        assertEquals(42, issue.target.number)

        val pr = assertIs<AppDeepLink.IssueOrPull>(
            parseAppDeepLink("https://www.github.com/octocat/Hello-World/pull/99/files#diff-1")
        )
        assertEquals(99, pr.target.number)

        val repo = assertIs<AppDeepLink.Repository>(
            parseAppDeepLink("https://github.com/neutron42-jp/github-mobile-maxxing.git")
        )
        assertEquals("neutron42-jp", repo.owner)
        assertEquals("github-mobile-maxxing", repo.repo)

        val user = assertIs<AppDeepLink.User>(
            parseAppDeepLink("https://github.com/neutron42-jp")
        )
        assertEquals("neutron42-jp", user.login)

        val reserved = assertIs<AppDeepLink.ExternalWeb>(
            parseAppDeepLink("https://github.com/trending")
        )
        assertEquals("https://github.com/trending", reserved.url)

        assertNull(parseAppDeepLink("https://example.com/octocat/repo"))
    }
}
