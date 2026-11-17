package dev.maxxing.github.ui.i18n

import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.ActivityEventKind
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.DeviceFlowErrorKind
import dev.maxxing.github.model.DeviceFlowWaitReason
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.HttpStatusError
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.RepoSecurityOverview
import dev.maxxing.github.model.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppStringsTest {
    private class FakeHttpError(
        override val httpStatusCode: Int,
        message: String?,
        override val isSyntheticStatus: Boolean = false
    ) : Exception(message), HttpStatusError

    @Test
    fun stringsForReturnsExpectedTable() {
        assertSame(EnglishStrings, stringsFor(AppLanguage.DEFAULT))
        assertSame(JapaneseStrings, stringsFor(AppLanguage.JAPANESE))
    }

    @Test
    fun everyLanguageReadsItsOwnStringsCsvColumn() {
        assertEquals("en", GeneratedStringTable.languageCodes.first(), "English must be the first (fallback) column")
        AppLanguage.entries.forEach { language ->
            assertTrue(language.code in GeneratedStringTable.languageCodes, "strings.csv has no '${language.code}' column")
            assertSame(language, stringsFor(language).language)
        }
        assertEquals(AppLanguage.entries.map { it.code }.toSet().size, AppLanguage.entries.size, "two languages share a column")
    }

    @Test
    fun templatesFillNumberedPlaceholdersAndKeepOtherBraces() {
        assertEquals("b then a", formatTemplate("{1} then {0}", "a", "b"))
        assertEquals("7 / 7", formatTemplate("{0} / {0}", 7))
        assertEquals("{x} {} {2} {0", formatTemplate("{x} {} {2} {0", "a"))
        assertEquals("a{", formatTemplate("{0}{", "a"))
        // An argument that looks like a placeholder is inserted as is, never filled again.
        assertEquals("{1} and x", formatTemplate("{0} and {1}", "{1}", "x"))
        assertEquals("{99999999999}", formatTemplate("{99999999999}", "a"))
    }

    @Test
    fun languageOptionsAreAlwaysShownInTheirOwnLanguage() {
        listOf(EnglishStrings, JapaneseStrings).forEach { strings ->
            assertEquals("English", strings.languageOptionLabel(AppLanguage.DEFAULT))
            assertEquals("日本語", strings.languageOptionLabel(AppLanguage.JAPANESE))
        }
    }

    @Test
    fun coreLabelsDifferBetweenEnglishAndJapanese() {
        assertEquals("Settings & Appearance", EnglishStrings.settingsTitle)
        assertEquals("設定と外観", JapaneseStrings.settingsTitle)
        assertEquals("Custom", EnglishStrings.customColorLabel)
        assertEquals("カスタム", JapaneseStrings.customColorLabel)
        assertEquals("Create repository", EnglishStrings.createRepoTitle)
        assertEquals("リポジトリを作成", JapaneseStrings.createRepoTitle)
    }

    @Test
    fun englishKeepsUpstreamWordingWhereKeysWereSplit() {
        assertEquals("Fork", EnglishStrings.fork)
        assertEquals("Fork repository", EnglishStrings.repoForkLabel)
        assertEquals("Trending All", EnglishStrings.trendingAll)
        assertEquals("All", EnglishStrings.discussionCategoryAll)
        assertEquals("updated 2026-11-01", EnglishStrings.repoUpdatedDate("2026-11-01"))
        assertEquals("更新: 2026-11-01", JapaneseStrings.repoUpdatedDate("2026-11-01"))
        assertEquals("File", EnglishStrings.repoNewFileButton)
        assertEquals("Swap base and head", EnglishStrings.swapBaseHeadDesc)
        assertEquals("Send comment", EnglishStrings.sendCommentDesc)
        assertEquals("Cancel reply", EnglishStrings.cancelReplyDesc)
        assertEquals("Download asset", EnglishStrings.downloadAssetDesc)
        assertEquals("Copy gist link", EnglishStrings.copyGistLinkDesc)
        assertEquals("Failed", EnglishStrings.markAllReadFailed)
        assertEquals("Failed to load workflow runs", EnglishStrings.failedLoadWorkflowRuns)
        assertEquals("Comment (Markdown)", EnglishStrings.diffCommentBodyLabel)
        assertEquals("Comment body (Markdown)", EnglishStrings.editCommentBodyLabel)
    }

    @Test
    fun completionMessagesAreDistinctFromActionLabels() {
        assertEquals("Closed #12", EnglishStrings.issueClosedMessage(12))
        assertEquals("Reopened #12", EnglishStrings.issueReopenedMessage(12))
        assertEquals("Converted PR to draft", EnglishStrings.prConvertedToDraftMessage)
        assertEquals("Marked PR ready for review", EnglishStrings.prMarkedReadyMessage)
        assertEquals("Merged #5 (squash)", EnglishStrings.issueMergedMessage(5, MergeMethod.SQUASH))
        assertEquals("Merged #5 (rebase)", EnglishStrings.issueMergedMessage(5, MergeMethod.REBASE))

        assertEquals("#12 をクローズしました", JapaneseStrings.issueClosedMessage(12))
        assertEquals("#12 を再開しました", JapaneseStrings.issueReopenedMessage(12))
        assertEquals("下書きに変換しました", JapaneseStrings.prConvertedToDraftMessage)
        assertEquals("レビュー可能にしました", JapaneseStrings.prMarkedReadyMessage)
        assertEquals("#5 をマージしました（スカッシュしてマージ）", JapaneseStrings.issueMergedMessage(5, MergeMethod.SQUASH))
        assertEquals("レビューを送信しました（承認）", JapaneseStrings.issueReviewSubmitted(JapaneseStrings.reviewApproveLabel))
    }

    @Test
    fun confirmationQuestionsMatchUpstreamEnglishAndReadNaturallyInJapanese() {
        assertEquals(
            "Are you sure you want to merge into main using Squash and merge?",
            EnglishStrings.mergeConfirmQuestion("main", MergeMethod.SQUASH)
        )
        assertEquals(
            "Are you sure you want to merge into base using Create a merge commit?",
            EnglishStrings.mergeConfirmQuestion(null, MergeMethod.MERGE)
        )
        assertEquals("Are you sure you want to close this pull request?", EnglishStrings.stateChangeConfirmQuestion(false, true))
        assertEquals("Are you sure you want to reopen this issue?", EnglishStrings.stateChangeConfirmQuestion(true, false))

        assertEquals("main にマージしますか？（スカッシュしてマージ）", JapaneseStrings.mergeConfirmQuestion("main", MergeMethod.SQUASH))
        assertEquals("ベースブランチにマージしますか？（リベースしてマージ）", JapaneseStrings.mergeConfirmQuestion(null, MergeMethod.REBASE))
        assertEquals("このプルリクエストをクローズしますか？", JapaneseStrings.stateChangeConfirmQuestion(false, true))
        assertEquals("この Issue を再開しますか？", JapaneseStrings.stateChangeConfirmQuestion(true, false))
    }

    @Test
    fun emptyStatesKeepUpstreamEnglishAndAvoidStateAdjectivesInJapanese() {
        assertEquals("No Open Issues or PRs", EnglishStrings.noIssuesOrPrsTitle(IssueStateFilter.OPEN))
        assertEquals("No All Issues or PRs", EnglishStrings.noIssuesOrPrsTitle(IssueStateFilter.ALL))
        assertEquals(
            "There are no closed items for 'review requested'.",
            EnglishStrings.noIssuesOrPrsBody(IssueStateFilter.CLOSED, IssueFilterPreset.REVIEW_REQUESTED)
        )
        assertEquals("No Closed Pull Requests", EnglishStrings.repoItemsEmptyTitle(true, IssueStateFilter.CLOSED))
        assertEquals("There are no all issues in this repository.", EnglishStrings.repoItemsEmptyBody(false, IssueStateFilter.ALL))

        assertEquals("オープンな Issue / PR はありません", JapaneseStrings.noIssuesOrPrsTitle(IssueStateFilter.OPEN))
        assertEquals("クローズされた Issue / PR はありません", JapaneseStrings.noIssuesOrPrsTitle(IssueStateFilter.CLOSED))
        assertEquals("Issue / PR はありません", JapaneseStrings.noIssuesOrPrsTitle(IssueStateFilter.ALL))
        assertEquals(
            "「自分が作成」に一致する項目はありません。",
            JapaneseStrings.noIssuesOrPrsBody(IssueStateFilter.ALL, IssueFilterPreset.CREATED)
        )
        assertEquals(
            "「レビュー依頼」に一致するクローズされた項目はありません。",
            JapaneseStrings.noIssuesOrPrsBody(IssueStateFilter.CLOSED, IssueFilterPreset.REVIEW_REQUESTED)
        )
        assertEquals("Issue はありません", JapaneseStrings.repoItemsEmptyTitle(false, IssueStateFilter.ALL))
        assertEquals("オープンな Issue はありません", JapaneseStrings.repoItemsEmptyTitle(false, IssueStateFilter.OPEN))
        assertEquals("クローズされたプルリクエストはありません", JapaneseStrings.repoItemsEmptyTitle(true, IssueStateFilter.CLOSED))
        assertEquals("このリポジトリには Issue がありません。", JapaneseStrings.repoItemsEmptyBody(false, IssueStateFilter.ALL))
        assertEquals("このリポジトリにはオープンな Issue がありません。", JapaneseStrings.repoItemsEmptyBody(false, IssueStateFilter.OPEN))
        assertEquals("このリポジトリにはプルリクエストがありません。", JapaneseStrings.repoItemsEmptyBody(true, IssueStateFilter.ALL))
    }

    @Test
    fun errorMessageKeepsEnglishDetailButUsesJapaneseFallbackWithStatus() {
        val api401 = FakeHttpError(401, "Failed to load notifications (401)")
        val api404 = FakeHttpError(404, "Failed to load gists (404). Your token may lack the 'gist' scope")
        val offline = IllegalStateException("Unable to resolve host api.github.com")
        val noMessage = RuntimeException()

        assertEquals("Failed to load notifications (401)", EnglishStrings.errorMessage(api401, EnglishStrings.failedLoadNotifications))
        assertEquals("Unable to resolve host api.github.com", EnglishStrings.errorMessage(offline, EnglishStrings.failedLoadRepos))
        assertEquals("Failed to load repositories", EnglishStrings.errorMessage(noMessage, EnglishStrings.failedLoadRepos))
        assertEquals(api404.message, EnglishStrings.errorMessage(api404, EnglishStrings.failedLoadGists, EnglishStrings.gistScopeErrorHint))

        assertEquals(
            "通知の読み込みに失敗しました（HTTP\u00A0401）。サインインし直してください。\n詳細: Failed to load notifications (401)",
            JapaneseStrings.errorMessage(api401, JapaneseStrings.failedLoadNotifications)
        )
        assertEquals(
            "リポジトリの読み込みに失敗しました\n詳細: Unable to resolve host api.github.com",
            JapaneseStrings.errorMessage(offline, JapaneseStrings.failedLoadRepos)
        )
        assertEquals("リポジトリの読み込みに失敗しました", JapaneseStrings.errorMessage(noMessage, JapaneseStrings.failedLoadRepos))
        assertEquals(
            "Gist の読み込みに失敗しました（HTTP\u00A0404）。${JapaneseStrings.gistScopeErrorHint}\n詳細: ${api404.message}",
            JapaneseStrings.errorMessage(api404, JapaneseStrings.failedLoadGists, JapaneseStrings.gistScopeErrorHint)
        )
        assertEquals(
            "Gist の読み込みに失敗しました（HTTP\u00A0401）。サインインし直してください。\n詳細: x",
            JapaneseStrings.errorMessage(FakeHttpError(401, "x"), JapaneseStrings.failedLoadGists, JapaneseStrings.gistScopeErrorHint)
        )
        assertTrue(JapaneseStrings.gistScopeErrorHint.contains("'gist'"))
        assertTrue(JapaneseStrings.followScopeErrorHint.contains("'user:follow'"))
    }

    @Test
    fun deviceFlowStatesAreLocalizedInJapaneseAndUnchangedInEnglish() {
        val awaiting = DeviceCodeState.AwaitingUser(
            deviceCode = "d", userCode = "ABCD-1234", verificationUri = "https://github.com/login/device",
            expiresInSeconds = 900, intervalSeconds = 10,
            statusMessage = "Rate limited by GitHub; polling every 10s...",
            waitReason = DeviceFlowWaitReason.SLOW_DOWN
        )
        assertEquals("Rate limited by GitHub; polling every 10s...", EnglishStrings.deviceFlowWaitingText(awaiting))
        assertEquals("GitHub のレート制限のため、10 秒ごとに確認しています...", JapaneseStrings.deviceFlowWaitingText(awaiting))
        val noReason = awaiting.copy(statusMessage = null, waitReason = null)
        assertEquals(EnglishStrings.signInWaitingAuth, EnglishStrings.deviceFlowWaitingText(noReason))
        assertEquals(JapaneseStrings.signInWaitingAuth, JapaneseStrings.deviceFlowWaitingText(noReason))
        assertEquals(
            mapOf(
                DeviceFlowWaitReason.CODE_ISSUED to "GitHub でこのコードを入力して、${dev.maxxing.github.model.AppBranding.APP_NAME} を承認してください。",
                DeviceFlowWaitReason.AUTHORIZATION_PENDING to "ブラウザでの承認を待っています...",
                DeviceFlowWaitReason.NETWORK_RETRY to "一時的なネットワークエラーです。再試行しています...",
                DeviceFlowWaitReason.SLOW_DOWN to "GitHub のレート制限のため、10 秒ごとに確認しています..."
            ),
            DeviceFlowWaitReason.entries.associateWith { JapaneseStrings.deviceFlowWaitingText(awaiting.copy(waitReason = it)) }
        )

        val rejected = DeviceCodeState.Error(
            "GitHub rejected device code request (404). Verify your Client ID.",
            kind = DeviceFlowErrorKind.CODE_REQUEST_REJECTED,
            httpStatusCode = 404
        )
        assertEquals(rejected.message, EnglishStrings.deviceFlowErrorText(rejected))
        assertEquals(
            "GitHub がデバイスコードの発行を拒否しました（HTTP\u00A0404）。クライアント ID を確認してください。",
            JapaneseStrings.deviceFlowErrorText(rejected)
        )
        assertEquals(
            mapOf(
                DeviceFlowErrorKind.MISSING_CLIENT_ID to "OAuth クライアント ID を入力してください。",
                DeviceFlowErrorKind.CODE_REQUEST_REJECTED to "GitHub がデバイスコードの発行を拒否しました。クライアント ID を確認してください。",
                DeviceFlowErrorKind.NETWORK to "GitHub に接続できませんでした。ネットワーク接続を確認してください。\n詳細: English",
                DeviceFlowErrorKind.EXPIRED to "デバイスコードの有効期限が切れました。新しいコードを取得してください。",
                DeviceFlowErrorKind.DENIED to "ブラウザで承認が拒否されました。",
                DeviceFlowErrorKind.TIMED_OUT to "時間内に承認されませんでした。もう一度お試しください。",
                DeviceFlowErrorKind.OTHER to "GitHub での認証に失敗しました。もう一度お試しください。\n詳細: English"
            ),
            DeviceFlowErrorKind.entries.associateWith { JapaneseStrings.deviceFlowErrorText(DeviceCodeState.Error("English", kind = it)) }
        )
        // OTHER keeps GitHub's OAuth error code instead of only asking to retry.
        fun other(code: String) = DeviceCodeState.Error("GitHub said: $code", oauthErrorCode = code)
        assertEquals("GitHub said: device_flow_disabled", EnglishStrings.deviceFlowErrorText(other("device_flow_disabled")))
        assertEquals(
            "この OAuth App では Device Flow が有効になっていません。GitHub の OAuth App の設定で「Enable Device Flow」をオンにしてください（エラー: device_flow_disabled）。\n詳細: GitHub said: device_flow_disabled",
            JapaneseStrings.deviceFlowErrorText(other("device_flow_disabled"))
        )
        assertEquals(
            "クライアント ID が正しくありません。入力したクライアント ID を確認してください（エラー: incorrect_client_credentials）。\n詳細: GitHub said: incorrect_client_credentials",
            JapaneseStrings.deviceFlowErrorText(other("incorrect_client_credentials"))
        )
        assertEquals(
            "GitHub での認証に失敗しました（エラー: unsupported_grant_type）。\n詳細: GitHub said: unsupported_grant_type",
            JapaneseStrings.deviceFlowErrorText(other("unsupported_grant_type"))
        )

        val verifying = DeviceCodeState.Verifying("Fetching profile...")
        assertEquals("Fetching profile...", EnglishStrings.deviceFlowVerifyingText(verifying))
        assertEquals("サインイン処理中...", JapaneseStrings.deviceFlowVerifyingText(verifying))
    }

    @Test
    fun authMethodAndFileStatusAndStateBadgesAreLocalized() {
        assertEquals("OAuth device flow", EnglishStrings.authMethodLabel(AuthMethod.DEVICE_FLOW))
        assertEquals("Personal access token", EnglishStrings.authMethodLabel(AuthMethod.PERSONAL_ACCESS_TOKEN))
        assertEquals("OAuth デバイス認証", JapaneseStrings.authMethodLabel(AuthMethod.DEVICE_FLOW))
        assertEquals("パーソナルアクセストークン", JapaneseStrings.authMethodLabel(AuthMethod.PERSONAL_ACCESS_TOKEN))

        // English keeps the upstream three-letter badges.
        assertEquals(
            listOf("ADD", "MOD", "REM", "REN", "COP", "CHA"),
            FileChangeStatus.entries.map { EnglishStrings.fileStatusBadge(it) }
        )
        assertEquals(
            listOf("追加", "変更", "削除", "名前変更", "コピー", "変更"),
            FileChangeStatus.entries.map { JapaneseStrings.fileStatusBadge(it) }
        )

        assertEquals("DRAFT", EnglishStrings.issueStateBadgeLabel(IssueOrPullState.OPEN, isDraft = true))
        assertEquals("OPEN", EnglishStrings.issueStateBadgeLabel(IssueOrPullState.OPEN, isDraft = false))
        assertEquals("MERGED", EnglishStrings.issueStateBadgeLabel(IssueOrPullState.MERGED, isDraft = false))
        assertEquals("CLOSED", EnglishStrings.issueStateBadgeLabel(IssueOrPullState.CLOSED, isDraft = false))
        assertEquals("下書き", JapaneseStrings.issueStateBadgeLabel(IssueOrPullState.OPEN, isDraft = true))
        assertEquals("マージ済み", JapaneseStrings.issueStateBadgeLabel(IssueOrPullState.MERGED, isDraft = false))
        // Elsewhere the mixed-case English labels stay as they were upstream.
        assertEquals("Draft", EnglishStrings.statusDraft)

        assertEquals("GitHub Mobile Maxxing — GitHub Client", EnglishStrings.windowTitle("GitHub Mobile Maxxing"))
        assertEquals("GitHub Mobile Maxxing — GitHub クライアント", JapaneseStrings.windowTitle("GitHub Mobile Maxxing"))
    }

    @Test
    fun notificationReasonsAreLocalizedInJapanese() {
        NotificationReason.entries.forEach { reason ->
            assertTrue(EnglishStrings.notificationReasonLabel(reason).isNotBlank())
            assertTrue(JapaneseStrings.notificationReasonLabel(reason).isNotBlank())
        }
        assertEquals("review requested", EnglishStrings.notificationReasonLabel(NotificationReason.REVIEW_REQUESTED))
        assertEquals("アサイン", JapaneseStrings.notificationReasonLabel(NotificationReason.ASSIGN))
        assertEquals("レビュー依頼", JapaneseStrings.notificationReasonLabel(NotificationReason.REVIEW_REQUESTED))
        assertEquals("セキュリティ", JapaneseStrings.notificationReasonLabel(NotificationReason.SECURITY_ALERT))
    }

    @Test
    fun editorMetricsFormatsSingularAndPluralInEnglish() {
        assertEquals("1 line · 1 char", EnglishStrings.formatEditorMetricsLabel(1, 1))
        assertEquals("3 lines · 12 chars", EnglishStrings.formatEditorMetricsLabel(3, 12))
        assertEquals("1 行 · 1 文字", JapaneseStrings.formatEditorMetricsLabel(1, 1))
        assertEquals("3 行 · 12 文字", JapaneseStrings.formatEditorMetricsLabel(3, 12))
    }

    @Test
    fun checkAndCompareAndReviewLabelsFormatProperly() {
        assertEquals("CI: SUCCESS", EnglishStrings.formatCheckRollupLabel("SUCCESS"))
        assertEquals("SUCCESS", EnglishStrings.formatCheckRollupState("SUCCESS"))
        assertEquals("CI: 成功", JapaneseStrings.formatCheckRollupLabel("SUCCESS"))
        assertEquals("CI: 失敗", JapaneseStrings.formatCheckRollupLabel("FAILURE"))
        assertEquals("CI: 保留中", JapaneseStrings.formatCheckRollupLabel("PENDING"))
        assertEquals("待機中", JapaneseStrings.formatCheckRollupState("EXPECTED"))
        assertEquals("成功", JapaneseStrings.formatCheckRollupState("SUCCESS"))

        assertEquals("SUCCESS", EnglishStrings.formatCheckConclusionLabel("success"))
        assertEquals("成功", JapaneseStrings.formatCheckConclusionLabel("success"))
        assertEquals("タイムアウト", JapaneseStrings.formatCheckConclusionLabel("timed_out"))
        assertEquals("キャンセル", JapaneseStrings.formatCheckConclusionLabel("cancelled"))
        assertEquals("待機中", JapaneseStrings.formatCheckConclusionLabel("queued"))
        assertEquals("実行中", JapaneseStrings.formatCheckConclusionLabel("in_progress"))

        // Plain status text keeps the raw API value in English (as upstream) and is localized in Japanese.
        assertEquals("in_progress", EnglishStrings.formatCheckStatusText("in_progress"))
        assertEquals("success", EnglishStrings.formatCheckStatusText("success"))
        assertEquals("実行中", JapaneseStrings.formatCheckStatusText("in_progress"))

        assertEquals("DIVERGED", EnglishStrings.formatCompareStatusLabel("diverged"))
        assertEquals("分岐", JapaneseStrings.formatCompareStatusLabel("diverged"))
        assertEquals("同一", JapaneseStrings.formatCompareStatusLabel("identical"))

        assertEquals("CHANGES REQUESTED", EnglishStrings.formatReviewStateLabel("CHANGES_REQUESTED"))
        assertEquals("変更要求", JapaneseStrings.formatReviewStateLabel("CHANGES_REQUESTED"))
        assertEquals("承認済み", JapaneseStrings.formatReviewStateLabel("APPROVED"))
    }

    @Test
    fun associationReactionSeverityAndTimelineFormatProperly() {
        assertEquals("OWNER", EnglishStrings.formatAuthorAssociationLabel("OWNER"))
        assertEquals("オーナー", JapaneseStrings.formatAuthorAssociationLabel("OWNER"))
        assertEquals("コントリビューター", JapaneseStrings.formatAuthorAssociationLabel("CONTRIBUTOR"))
        assertEquals("初めてのコントリビューター", JapaneseStrings.formatAuthorAssociationLabel("FIRST_TIME_CONTRIBUTOR"))

        assertEquals("thumbs up", EnglishStrings.formatReactionLabel("THUMBS_UP"))
        assertEquals("いいね", JapaneseStrings.formatReactionLabel("THUMBS_UP"))
        assertEquals("笑い", JapaneseStrings.formatReactionLabel("LAUGH"))

        assertEquals("CRITICAL", EnglishStrings.formatSeverityLabel("critical"))
        assertEquals("緊急", JapaneseStrings.formatSeverityLabel("critical"))
        assertEquals("中", JapaneseStrings.formatSeverityLabel("moderate"))

        assertEquals("@alice closed this", EnglishStrings.formatTimelineLine("alice", "closed this"))
        assertEquals("@alice がクローズしました", JapaneseStrings.formatTimelineLine("alice", "closed this"))
        assertEquals("@alice が再開しました", JapaneseStrings.formatTimelineLine("alice", "reopened this"))
        assertEquals("@alice が main にマージしました", JapaneseStrings.formatTimelineLine("alice", "merged into main"))
        assertEquals("@alice labeled this", JapaneseStrings.formatTimelineLine("alice", "labeled this"))
        // No actor (e.g. a deleted user): English keeps upstream "@system", Japanese drops the "@".
        assertEquals("@system closed this", EnglishStrings.formatTimelineLine(null, "closed this"))
        assertEquals("システムがクローズしました", JapaneseStrings.formatTimelineLine(null, "closed this"))
        assertEquals("システムが main にマージしました", JapaneseStrings.formatTimelineLine(null, "merged into main"))
        assertEquals("システム labeled this", JapaneseStrings.formatTimelineLine(null, "labeled this"))
        // The producer writes "merged into base" when the ref name is unknown.
        assertEquals("@bob merged into base", EnglishStrings.formatTimelineLine("bob", "merged into base"))
        assertEquals("@bob がベースブランチにマージしました", JapaneseStrings.formatTimelineLine("bob", "merged into base"))
    }

    @Test
    fun syntheticStatusesAreNotShownAsHttpStatuses() {
        val graphQl = FakeHttpError(422, "Resource not accessible by personal access token", isSyntheticStatus = true)
        assertEquals("Resource not accessible by personal access token", EnglishStrings.errorMessage(graphQl, EnglishStrings.failedLoadRepos))
        assertEquals(
            "リポジトリの読み込みに失敗しました\n詳細: Resource not accessible by personal access token",
            JapaneseStrings.errorMessage(graphQl, JapaneseStrings.failedLoadRepos)
        )
    }

    @Test
    fun jobLogErrorsKeepTheHintWithoutDoubleParentheses() {
        val gone = FakeHttpError(410, "Failed to load job logs (410)")
        assertEquals("Failed to load job logs (410)", EnglishStrings.jobLogsErrorText(gone))
        assertEquals("Logs unavailable (check repo scope / expiration)", EnglishStrings.jobLogsErrorText(RuntimeException()))
        assertEquals(
            "ログを取得できません（HTTP\u00A0410）。repo スコープと、ログの保持期限が切れていないかを確認してください。\n詳細: Failed to load job logs (410)",
            JapaneseStrings.jobLogsErrorText(gone)
        )
        assertEquals("ログを取得できません。ネットワーク接続を確認してください。", JapaneseStrings.jobLogsErrorText(RuntimeException()))
    }

    @Test
    fun dependabotNoticeIsLocalizedFromTheStatusCode() {
        val loaded = RepoSecurityOverview()
        assertNull(EnglishStrings.dependabotAlertsNoticeText(loaded))
        assertNull(JapaneseStrings.dependabotAlertsNoticeText(loaded))

        val forbidden = RepoSecurityOverview(
            alertsAccessNotice = "Dependabot alerts require repository admin or security_events permission.",
            alertsAccessStatusCode = 403
        )
        assertEquals(forbidden.alertsAccessNotice, EnglishStrings.dependabotAlertsNoticeText(forbidden))
        assertEquals(
            "Dependabot アラートを表示するには、リポジトリの管理者権限または security_events 権限が必要です。",
            JapaneseStrings.dependabotAlertsNoticeText(forbidden)
        )
        assertEquals(
            "このリポジトリでは Dependabot の脆弱性アラートが有効になっていません。",
            JapaneseStrings.dependabotAlertsNoticeText(RepoSecurityOverview(alertsAccessNotice = "x", alertsAccessStatusCode = 404))
        )
        assertEquals(
            "Dependabot アラートを取得できませんでした（HTTP\u00A0500）。\n詳細: x",
            JapaneseStrings.dependabotAlertsNoticeText(RepoSecurityOverview(alertsAccessNotice = "x", alertsAccessStatusCode = 500))
        )
        assertEquals(
            "Dependabot アラートを取得できませんでした。\n詳細: offline",
            JapaneseStrings.dependabotAlertsNoticeText(RepoSecurityOverview(alertsAccessNotice = "offline"))
        )
    }

    @Test
    fun activityLinesPutTheActorFirstInJapanese() {
        fun event(kind: ActivityEventKind, summary: String, issueNumber: Int? = null, branchName: String? = null) =
            ActivityEvent(
                id = kind.name,
                kind = kind,
                actor = UserSummary(login = "alice"),
                repoFullName = "org/repo",
                repoOwner = "org",
                repoName = "repo",
                summaryText = summary,
                targetIssueNumber = issueNumber,
                branchName = branchName
            )

        val push = event(ActivityEventKind.PUSH, "pushed 2 commits to main", branchName = "main")
        assertEquals("@alice pushed 2 commits to main", EnglishStrings.formatActivityEventLine(push))
        assertEquals("@alice が main に 2 件のコミットをプッシュしました", JapaneseStrings.formatActivityEventLine(push))
        assertEquals(
            "@alice がプルリクエスト #42 をマージしました",
            JapaneseStrings.formatActivityEventLine(event(ActivityEventKind.PULL_REQUEST, "merged pull request #42", issueNumber = 42))
        )
        assertEquals(
            "@alice が #9 にコメントしました",
            JapaneseStrings.formatActivityEventLine(event(ActivityEventKind.ISSUE_COMMENT, "commented on #9", issueNumber = 9))
        )
        // Unknown phrases never produce a broken "が" sentence.
        assertEquals(
            "@alice のアクティビティ（pushed something new）",
            JapaneseStrings.formatActivityEventLine(event(ActivityEventKind.PUSH, "pushed something new"))
        )
    }

    @Test
    fun fontSelectionLabelsAndFontUiStringsAreLocalized() {
        val blankSystem = FontSelection(kind = FontSourceKind.SYSTEM_FAMILY, value = "", displayName = "")
        assertEquals("System Default", EnglishStrings.fontSelectionLabel(blankSystem))
        assertEquals("システム標準", JapaneseStrings.fontSelectionLabel(blankSystem))

        val namedSystem = FontSelection(kind = FontSourceKind.SYSTEM_FAMILY, value = "Noto Sans")
        assertEquals("Noto Sans", EnglishStrings.fontSelectionLabel(namedSystem))
        assertEquals("Noto Sans", JapaneseStrings.fontSelectionLabel(namedSystem))

        assertEquals("Typography & Fonts", EnglishStrings.fontSectionTitle)
        assertEquals("タイポグラフィとフォント", JapaneseStrings.fontSectionTitle)
        assertEquals("Imported font: Test.ttf", EnglishStrings.importedFontMessage("Test.ttf"))
        assertEquals("フォントを読み込みました: Test.ttf", JapaneseStrings.importedFontMessage("Test.ttf"))
        assertEquals("Select Font File (.ttf / .otf / .ttc)", EnglishStrings.fontFileChooserTitle)
        assertEquals("Font files (*.ttf, *.otf, *.ttc)", EnglishStrings.fontFileFilterLabel)
    }
}
