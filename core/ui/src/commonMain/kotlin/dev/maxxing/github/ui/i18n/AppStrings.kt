package dev.maxxing.github.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.GlobalSearchKind
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.RepoSecurityOverview
import dev.maxxing.github.model.ReviewEvent

/** Localized UI strings resolved dynamically via [LocalAppStrings]. */
@Immutable
abstract class AppStrings {
    // Common
    abstract val loading: String
    abstract val retry: String
    abstract val cancel: String
    abstract val save: String
    abstract val apply: String
    abstract val done: String
    abstract val refresh: String
    abstract val back: String
    abstract val clear: String
    abstract val create: String
    abstract val creating: String
    abstract val newAction: String
    abstract val searchAction: String
    abstract val selectOption: String
    abstract val selectedOption: String
    abstract val star: String
    abstract val starred: String
    abstract val stars: String
    abstract val follow: String
    abstract val followingLabel: String
    abstract val writeMode: String
    abstract val previewMode: String
    abstract val closeEditor: String
    abstract val closeAction: String
    abstract val openInBrowser: String
    abstract val copyUrl: String
    abstract val share: String
    abstract val shareEllipsis: String
    abstract val shareRepository: String
    abstract val gistsAction: String
    abstract val privateBadge: String
    abstract val privateRepo: String
    abstract val publicRepo: String
    abstract val fork: String
    abstract val notStarred: String
    abstract val statusDraft: String
    abstract val statusOpen: String
    abstract val statusMerged: String
    abstract val statusClosed: String
    abstract val unknownError: String
    abstract val failedLoadDetails: String
    abstract val openDetails: String

    // App startup & navigation
    abstract val startingApp: (String) -> String
    abstract val navHome: String
    abstract val navInbox: String
    abstract val navIssues: String
    abstract val navRepos: String
    abstract val navSettings: String

    // Sign-in screen
    abstract val signInSubtitle: String
    abstract val signInClientIdLabel: String
    abstract val signInWithGitHubButton: String
    abstract val signInUseCustomClientId: String
    abstract val signInHideCustomClientId: String
    abstract val signInClientIdDefaultSupporting: (String) -> String
    abstract val signInResetClientId: String
    abstract val signInRequestingCode: String
    abstract val signInEnterCode: String
    abstract val signInWaitingAuth: String
    abstract val signInCopyCode: String
    abstract val signInOpenGitHub: String
    abstract val signInCopiedValue: (String) -> String
    abstract val signInCodeCopiedOpening: (String) -> String
    abstract val signInCodeCopiedManual: (String, String) -> String
    abstract val signInWelcome: (String) -> String

    // Settings screen
    abstract val settingsTitle: String
    abstract val githubUserFallback: String
    abstract val unknownUser: String
    abstract val signOut: String
    abstract val apiRateLimit: String
    abstract val osNotificationsTitle: String
    abstract val enableOsAlerts: String
    abstract val enableOsAlertsDesc: String
    abstract val urgentOnly: String
    abstract val urgentOnlyDesc: String
    abstract val foregroundSyncInterval: String
    abstract val pollIntervalMinutes: (Int) -> String
    abstract val sendTestNotification: String
    abstract val testNotificationSent: String
    abstract val testNotificationBlocked: String
    abstract val themeTitle: String
    abstract val appearanceLabel: String
    abstract val appearanceSystem: String
    abstract val appearanceLight: String
    abstract val appearanceDark: String
    abstract val seedAccentLabel: String
    abstract val customColorLabel: String
    abstract val pickCustomColorDesc: String
    abstract val customAccentColorTitle: String
    abstract val hexColorLabel: String
    abstract val dynamicColorTitle: String
    abstract val dynamicColorDesc: String
    abstract val languageSectionTitle: String
    abstract val languageLabel: String
    abstract val languageDesc: String
    abstract val linkHandlingTitle: String
    abstract val linkHandlingEnabledDesc: String
    abstract val linkHandlingOfficialClaimsDesc: String
    abstract val linkHandlingOfficialInstalledDesc: String
    abstract val linkHandlingDefaultDesc: String
    abstract val unlinkGitHubMobile: String
    abstract val manageAppLinks: String
    abstract val enableGitHubLinks: String
    abstract val failedOpenGitHubMobileLinkSettings: String
    abstract val failedOpenAppLinkSettings: String
    abstract val oauthConfigTitle: String
    abstract val oauthClientIdLabel: String
    abstract val savedClientId: String

    // Home screen
    abstract val loadingHome: String
    abstract val failedLoadActivity: String
    abstract val pinnedBookmarks: String
    abstract val recentRepositories: String
    abstract val activityFeed: String
    abstract val feedTabFollowing: String
    abstract val feedTabMyActivity: String
    abstract val noRecentActivity: String
    abstract val contributionsTitle: String
    abstract val contributionsInLastYear: (Int) -> String
    abstract val contributionsLess: String
    abstract val contributionsMore: String

    // Inbox / Notifications screen
    abstract val inboxTitle: String
    abstract val inboxFilterUnread: String
    abstract val inboxFilterParticipating: String
    abstract val inboxFilterAll: String
    abstract val reasonAll: String
    abstract val reasonReviews: String
    abstract val reasonMentions: String
    abstract val reasonAssigned: String
    abstract val reasonCi: String
    abstract val filterNotificationsPlaceholder: String
    abstract val syncingNotifications: String
    abstract val failedLoadNotifications: String
    abstract val inboxZeroTitle: String
    abstract val inboxZeroUnreadHint: String
    abstract val inboxZeroFilteredHint: (String) -> String
    abstract val inboxUpToDate: String
    abstract val syncedUnreadCount: (Int) -> String
    abstract val syncedCount: (Int) -> String
    abstract val markedDone: String
    abstract val doneAction: String
    abstract val markDoneAction: String
    abstract val markAllReadAction: String
    abstract val allNotificationsMarkedRead: String
    abstract val selectNotificationToTriage: String

    // Issues & PRs screen
    abstract val issuesAndPrsTitle: String
    abstract val fetchingIssuesAndPrs: String
    abstract val failedLoadIssuesAndPrs: String
    abstract val presetCreated: String
    abstract val presetAssigned: String
    abstract val presetReviews: String
    abstract val presetMentioned: String
    abstract val stateOpen: String
    abstract val stateClosed: String
    abstract val stateAll: String
    abstract val selectIssueOrPr: String

    // Repos list & search screen
    abstract val exploreTitle: String
    abstract val repositoriesTitle: String
    abstract val repoTabMine: String
    abstract val repoTabStarred: String
    abstract val repoTabExplore: String
    abstract val searchInLabel: String
    abstract val searchKindRepos: String
    abstract val searchKindIssuesPrs: String
    abstract val searchKindCode: String
    abstract val searchKindUsers: String
    abstract val searchReposPlaceholder: String
    abstract val searchIssuesPlaceholder: String
    abstract val searchCodePlaceholder: String
    abstract val searchUsersPlaceholder: String
    abstract val trendingAll: String
    abstract val loadingTrending: String
    abstract val loadingResults: String
    abstract val noTrendingFound: String
    abstract val noSearchMatches: (String, String) -> String
    abstract val noStarredRepos: String
    abstract val noReposFound: String
    abstract val failedLoadTrending: String
    abstract val failedLoadRepos: String
    abstract val failedLoadStarredRepos: String
    abstract val repoSearchFailed: String
    abstract val issueSearchFailed: String
    abstract val codeSearchFailed: String
    abstract val userSearchFailed: String
    abstract val createRepoTitle: String
    abstract val repoNameLabel: String
    abstract val repoDescLabel: String
    abstract val initWithReadme: String
    abstract val createdRepoMessage: (String) -> String
    abstract val failedCreateRepo: String

    // User profile screen
    abstract val loadingUser: (String) -> String
    abstract val failedLoadProfile: String
    abstract val orgBadge: String
    abstract val organizationsTitle: String
    abstract val profileTabRepos: String
    abstract val profileTabStarred: String
    abstract val profileTabActivity: String
    abstract val profileTabFollowers: String
    abstract val profileTabFollowing: String
    abstract val noPublicRepos: String
    abstract val noStarredReposProfile: String
    abstract val noRecentPublicActivity: String
    abstract val noFollowersYet: String
    abstract val notFollowingAnyoneYet: String
    abstract val followedUser: (String) -> String
    abstract val unfollowedUser: (String) -> String
    abstract val followUpdateFailed: String

    // Gists screen
    abstract val gistsTitle: String
    abstract val userGistsTitle: (String) -> String
    abstract val gistsTabMine: String
    abstract val gistsTabStarred: String
    abstract val missingGistScopeNotice: String
    abstract val loadingGists: String
    abstract val failedLoadGists: String
    abstract val failedLoadGistDetails: String
    abstract val noGistsFound: String
    abstract val copiedGistLink: String
    abstract val starredGist: String
    abstract val unstarredGist: String
    abstract val gistStarUpdateFailed: String
    abstract val gistPublicBadge: String
    abstract val gistSecretBadge: String
    abstract val gistFileCount: (Int) -> String
    abstract val gistNewTitle: String
    abstract val gistEditTitle: String
    abstract val gistDeleteTitle: String
    abstract val gistDeleteConfirm: (String) -> String
    abstract val gistCreatedMessage: String
    abstract val gistUpdatedMessage: String
    abstract val gistDeletedMessage: String
    abstract val gistSaveFailed: String
    abstract val gistDeleteFailed: String
    abstract val gistFilenameLabel: String
    abstract val gistContentsLabel: String
    abstract val gistPublicToggle: String
    abstract val gistPublicDesc: String
    abstract val gistSecretDesc: String
    abstract val gistEditAction: String
    abstract val gistDeleteAction: String
    abstract val deleteAction: String
    abstract val deletingAction: String
    abstract val savingAction: String

    // Repo detail, commits, upstream compare, insights, releases, discussions, security, actions, files
    abstract val repoDetailTabCode: String
    abstract val repoDetailTabIssues: String
    abstract val repoDetailTabPulls: String
    abstract val repoDetailTabActions: String
    abstract val repoDetailTabReleases: String
    abstract val repoDetailTabDiscussions: String
    abstract val repoDetailTabSecurity: String
    abstract val repoDetailTabInsights: String
    abstract val repoPinnedMessage: (String) -> String
    abstract val repoUnpinnedMessage: (String) -> String
    abstract val repoPinAction: String
    abstract val repoUnpinAction: String
    abstract val repoForkedFrom: (String) -> String
    abstract val repoForkLabel: String
    abstract val repoStarredMessage: (String) -> String
    abstract val repoUnstarredMessage: (String) -> String
    abstract val repoWatching: String
    abstract val repoWatch: String
    abstract val repoWatchingMessage: (String) -> String
    abstract val repoUnwatchedMessage: (String) -> String
    abstract val repoWatchUpdateFailed: String
    abstract val repoForking: String
    abstract val repoForkedMessage: (String) -> String
    abstract val repoForkFailed: String
    abstract val repoNetwork: String
    abstract val repoNetworkTitle: (String) -> String
    abstract val repoCompare: String
    abstract val repoCompareUpstream: String
    abstract val repoCommitsButton: String
    abstract val repoEditFile: String
    abstract val repoDeleteFile: String
    abstract val repoBinaryFile: (Long) -> String
    abstract val repoBranchOrTag: String
    abstract val repoSwitchBranchOrTag: String
    abstract val repoCheckingDivergence: (String) -> String
    abstract val repoEvenWithUpstream: (String, String) -> String
    abstract val repoZeroAheadBehind: (Int, String) -> String
    abstract val repoAheadBehind: (Int, Int, String) -> String
    abstract val repoAllCommits: String
    abstract val repoForkOnlyCommits: (Int) -> String
    abstract val repoLoadingCommits: String
    abstract val repoNoCommitsFound: (String) -> String
    abstract val repoForkSortStarred: String
    abstract val repoForkSortNewest: String
    abstract val repoNoStargazers: String
    abstract val repoNoForks: String
    abstract val repoNoContributors: String
    abstract val repoPeopleTabStars: String
    abstract val repoPeopleTabForks: String
    abstract val repoPeopleTabContributors: String
    abstract val repoUpdatedDate: (String) -> String
    abstract val repoNewIssueTitle: String
    abstract val repoNewPrTitle: String
    abstract val repoCreatePrButton: String
    abstract val repoIssueTitleLabel: String
    abstract val repoPrTitleLabel: String
    abstract val repoDraftPrToggle: String
    abstract val repoDescriptionMarkdown: String
    abstract val repoCreatedIssueMessage: (Int) -> String
    abstract val repoCreatedPrMessage: (Int) -> String
    abstract val repoCreateIssueFailed: String
    abstract val repoHeadLabel: String
    abstract val repoBaseLabel: String
    abstract val repoLoadingIssues: String
    abstract val repoLoadingPulls: String
    abstract val repoRefreshIssues: String
    abstract val repoRefreshPulls: String
    abstract val failedLoadDiscussions: String
    abstract val failedLoadDiscussion: String
    abstract val failedLoadSecurity: String
    abstract val failedLoadInsights: String
    abstract val failedLoadReleases: String
    abstract val failedLoadActions: String
    abstract val failedLoadJobs: String
    abstract val failedLoadCommits: String
    abstract val failedLoadCommitDiff: String
    abstract val compareFailed: String
    abstract val failedLoadStargazers: String
    abstract val failedLoadForks: String
    abstract val failedLoadContributors: String
    abstract val failedLoadDirectory: String
    abstract val failedLoadFile: String
    abstract val failedLoadIssues: String
    abstract val failedLoadPulls: String
    abstract val failedCreateRelease: String
    abstract val repoMetadataNotReady: String
    abstract val failedCreateDiscussion: String
    abstract val failedCreatePr: String
    abstract val failedPostComment: String

    // Commits, compare, actions, file editor
    abstract val commitVerifiedBadge: String
    abstract val commitVerifiedDesc: String
    abstract val commitFilesChangedSummary: (Int, Int, Int) -> String
    abstract val commitLoading: (String) -> String
    abstract val commitDetailsUnavailable: String
    abstract val commitParentLabel: (String) -> String
    abstract val compareLoading: (String, String) -> String
    abstract val compareSelectRefs: String
    abstract val compareAheadBehind: (Int, Int) -> String
    abstract val compareSummaryCounts: (Int, Int) -> String
    abstract val compareCommitsHeader: (Int) -> String
    abstract val compareChangedFilesHeader: (Int) -> String
    abstract val actionsAllRuns: String
    abstract val actionsRunButton: String
    abstract val actionsRefreshRuns: String
    abstract val actionsLoadingRuns: String
    abstract val actionsNoRuns: String
    abstract val actionsRunsBack: String
    abstract val actionsRefreshJobs: String
    abstract val actionsLoadingJobs: String
    abstract val actionsCancelRun: String
    abstract val actionsRerunAll: String
    abstract val actionsRerunFailed: String
    abstract val actionsLogsButton: String
    abstract val actionsJobsBack: String
    abstract val actionsLoadingLogs: String
    abstract val actionsNoLogs: String
    abstract val actionsRunWorkflowTitle: String
    abstract val actionsSelectWorkflow: String
    abstract val actionsBranchOrTagLabel: (String) -> String
    abstract val actionsInputsLabel: String
    abstract val actionsStarting: String
    abstract val actionsDispatchedMessage: (String) -> String
    abstract val actionsDispatchFailed: String
    abstract val actionsRerunFailedError: String
    abstract val actionsCancelFailedError: String
    abstract val actionsLogsUnavailable: String
    /** Shown in the file editor summary when the path field is still empty. */
    abstract val fileUntitledName: String
    abstract val fileCreateTitle: String
    abstract val fileEditTitle: String
    abstract val fileCommitting: String
    abstract val fileCommitButton: String
    abstract val fileContentsLabel: String
    abstract val filePathLabel: String
    abstract val fileCommitMessageLabel: String
    abstract val fileBranchLabel: String
    abstract val fileDefaultCreateCommitMsg: (String) -> String
    abstract val fileDefaultUpdateCommitMsg: (String) -> String
    abstract val fileMetadataSummary: (String, String, String) -> String
    abstract val fileCommittedMessage: (String, String) -> String
    abstract val fileCommitFailed: String
    abstract val fileDeleteTitle: String
    abstract val fileDeleteConfirm: (String, String) -> String
    abstract val fileDeletedMessage: (String, String) -> String
    abstract val fileDeleteFailed: String

    // Repo Insights
    abstract val insightsLoading: String
    abstract val insightsHeader: String
    abstract val insightsComputingNotice: String
    abstract val insightsLast4wCommits: String
    abstract val insights52wCommits: String
    abstract val insightsLanguages: String
    abstract val insightsNoLanguages: String
    abstract val insightsWeeklyPulse: String
    abstract val insightsComputingPulse: String
    abstract val insightsNoWeeklyPulse: String
    abstract val insightsPulseSummary: (Int, Int) -> String
    abstract val insightsTopContributors: String
    abstract val insightsNoContributors: String
    abstract val insightsCommitCount: (Int) -> String

    // Repo Releases
    abstract val releasesCount: (Int) -> String
    abstract val releasesHeader: String
    abstract val releasesNew: String
    abstract val releasesLoading: String
    abstract val releasesNoneTitle: String
    abstract val releasesNoneWithTags: (Int) -> String
    abstract val releasesNoneEmpty: String
    abstract val releaseLatest: String
    abstract val releasePrerelease: String
    abstract val releaseShowNotes: String
    abstract val releaseHideNotes: String
    abstract val releaseAssetsCount: (Int) -> String
    abstract val releaseSourceZip: String
    abstract val releaseSourceTarGz: String
    abstract val releaseDownloadsCount: (String) -> String
    abstract val releaseDownloadArchiveDesc: String
    abstract val releaseCreateTitle: String
    abstract val releaseTargetBranchSubtitle: (String) -> String
    abstract val releasePublishing: String
    abstract val releaseSaveDraft: String
    abstract val releasePublish: String
    abstract val releaseNotesLabel: String
    abstract val releaseTagLabel: String
    abstract val releaseTitleLabel: String
    abstract val releaseTargetLabel: String
    abstract val releaseAutoNotes: String
    abstract val releaseDraftToggle: String

    // Repo Discussions & Security
    abstract val discussionsCount: (Int) -> String
    abstract val discussionsHeader: String
    abstract val discussionsLoading: String
    abstract val discussionsDisabledOrEmpty: String
    abstract val discussionsEmpty: String
    abstract val discussionsHint: String
    abstract val discussionAnswered: String
    abstract val discussionAnswer: String
    abstract val discussionRepliesCount: (Int) -> String
    abstract val discussionReplyAction: String
    abstract val discussionReplyingTo: (String) -> String
    abstract val discussionWriteReply: String
    abstract val discussionAddComment: String
    abstract val discussionUpvoteDesc: String
    abstract val discussionAuthorInCategory: (String, String) -> String
    abstract val discussionNewTitle: String
    abstract val discussionCategoryLabel: String
    abstract val discussionBodyLabel: String
    abstract val securityHeader: String
    abstract val securityChecking: String
    abstract val securityDependabotCount: (Int) -> String
    abstract val securityAdvisoriesCount: (Int) -> String
    abstract val securityAllClearTitle: String
    abstract val securityAllClearDesc: String
    abstract val securityAffected: (String) -> String
    abstract val securityFix: (String) -> String

    // Issue / PR detail, labels, editor, diff viewer, notifications
    abstract val issueDetailTabChat: String
    abstract val issueDetailTabChecks: String
    abstract val issueDetailTabFiles: String
    abstract val issueLoadingNumber: (Int) -> String
    abstract val issueLoadingChecks: String
    abstract val issueNoChecks: String
    abstract val issueRefreshChecks: String
    abstract val issueReviewAction: String
    abstract val issueReopenAction: String
    abstract val issueCloseAction: String
    abstract val issueMergeAction: String
    abstract val issueMergeConfirmTitle: (Int) -> String
    abstract val issueMergeFailed: String
    abstract val issueReopenConfirmTitle: (Int) -> String
    abstract val issueCloseConfirmTitle: (Int) -> String
    abstract val issueStateUpdateFailed: String
    abstract val issueCommentTitle: (Int) -> String
    abstract val issueCommentPost: String
    abstract val issueCommentLabel: String
    abstract val issueCommentPosted: String
    abstract val issueCommentPostFailed: String
    abstract val issueSubmitReviewTitle: String
    abstract val issueSubmitAction: String
    abstract val issueReviewSummaryLabel: String
    abstract val issueReviewSubmitted: (String) -> String
    abstract val issueReviewFailed: String
    abstract val issueDecisionLabel: String
    abstract val labelsTitle: String
    abstract val selectLabelsTitle: String
    abstract val loadingLabels: String
    abstract val issueManageLabelsTitle: String
    abstract val issueNoLabels: String
    abstract val issueNewLabel: String
    abstract val createLabelAction: String
    abstract val createdLabelMessage: (String) -> String
    abstract val failedCreateLabel: String
    abstract val failedLoadLabels: String
    abstract val issueLabelsUpdated: String
    abstract val failedUpdateLabels: String
    abstract val issueManageAssigneesTitle: String
    abstract val issueNoAssignees: String
    abstract val failedLoadAssignees: String
    abstract val issueAssigneesUpdated: String
    abstract val failedUpdateAssignees: String
    abstract val issueSelectMilestoneTitle: String
    abstract val issueNoMilestone: String
    abstract val failedLoadMilestones: String
    abstract val issueMilestoneUpdated: String
    abstract val issueMilestoneCleared: String
    abstract val failedUpdateMilestone: String
    abstract val milestoneDue: (String) -> String
    abstract val failedLoadCiChecks: String
    abstract val failedUpdateDraftState: String
    abstract val reactionFailed: String
    abstract val addReactionDesc: String
    abstract val reactedDesc: String
    abstract val noFileDiffs: String
    abstract val reviewCommentLineTitle: (String, Int) -> String
    abstract val fileCommentTitle: (String) -> String
    abstract val addedInlineDiffComment: String
    abstract val failedPostDiffComment: String
    abstract val issueEditCommentTitle: String
    abstract val commentOnIssueSubtitle: (String, Int) -> String
    abstract val issueCommentUpdated: String
    abstract val failedUpdateComment: String
    abstract val issueDeleteCommentTitle: String
    abstract val issueDeleteCommentConfirm: (String) -> String
    abstract val issueCommentDeleted: String
    abstract val failedDeleteComment: String
    abstract val editCommentDesc: String
    abstract val deleteCommentDesc: String
    abstract val issueRequestReviewersTitle: String
    abstract val issueUsernameLabel: String
    abstract val issueRequestAction: String
    abstract val addReviewerDesc: String
    abstract val requestedReviewersMessage: (String) -> String
    abstract val failedRequestReviewers: String
    abstract val reviewersHeader: String
    abstract val systemActorFallback: String
    abstract val issueLabelsCount: (Int) -> String
    abstract val issueAssigneesCount: (Int) -> String
    abstract val issueMilestoneFallback: String
    abstract val issueReviewersCount: (Int) -> String
    abstract val issueReadyForReview: String
    abstract val issueConvertToDraft: String
    abstract val issueReactAction: String
    abstract val mergeSquashLabel: String
    abstract val mergeCommitLabel: String
    abstract val mergeRebaseLabel: String
    abstract val reviewApproveLabel: String
    abstract val reviewRequestChangesLabel: String
    abstract val reviewCommentLabel: String

    // Full-screen editor & diff viewer
    abstract val collapseDetails: String
    abstract val expandDetails: String
    abstract val nothingToPreview: String
    abstract val monospaceCodeHint: String
    abstract val markdownSupportedHint: String
    abstract val openFullScreenDiff: String
    abstract val closeDiffViewer: String
    abstract val commentOnFile: String
    abstract val diffScrollMode: String
    abstract val diffWrapMode: String
    abstract val diffPrevFile: String
    abstract val diffNextFile: String
    abstract val diffTapLineHint: String
    abstract val diffBinaryOrLarge: String
    abstract val diffEmptyPatch: String

    // Notification detail & OS notifications
    abstract val notifUpdatedAt: (String) -> String
    abstract val notifOpenOnGitHub: String
    abstract val notifLoadingDetails: (String) -> String
    abstract val notifCheckRunsTitle: String
    abstract val notifWorkflowRunsTitle: String
    abstract val notifRerunningFailed: String
    abstract val notifRerunningWorkflow: String
    abstract val notifCancelRequested: String
    abstract val subjectIssue: String
    abstract val subjectPullRequest: String
    abstract val subjectCommit: String
    abstract val subjectRelease: String
    abstract val subjectDiscussion: String
    abstract val subjectCheckSuite: String
    abstract val subjectNotification: String
    abstract val osNotifUrgentChannelName: String
    abstract val osNotifUrgentChannelDesc: String
    abstract val osNotifActivityChannelName: String
    abstract val osNotifActivityChannelDesc: String
    abstract val osNotifMarkReadAction: String
    abstract val osNotifBatchTitle: (Int) -> String
    abstract val osNotifTestReadyTitle: (String) -> String
    abstract val osNotifTestReadyBody: String
    abstract val osNotifDesktopTestTitle: (String) -> String
    abstract val osNotifDesktopTestBody: String

    // Custom Typography & Fonts
    abstract val fontSectionTitle: String
    abstract val primaryFontLabel: String
    abstract val primaryFontDesc: String
    abstract val fallbackFontLabel: String
    abstract val fallbackFontDesc: String
    abstract val fallbackFontRequiresAndroid10: String
    abstract val resetFontsToSystemDefault: String
    abstract val fontPreviewTitle: String
    abstract val fontPreviewPrimarySample: String
    abstract val fontPreviewSecondarySample: String
    abstract val selectPrimaryFontTitle: String
    abstract val selectFallbackFontTitle: String
    abstract val importedFontMessage: (String) -> String
    abstract val failedImportFont: String
    abstract val importFontFileButton: String
    abstract val searchSystemFontsPlaceholder: String
    abstract val systemDefaultLabel: String
    abstract val fontGenericSans: String
    abstract val fontGenericSerif: String
    abstract val fontGenericMonospace: String
    abstract val importedFontFilesHeader: String
    abstract val genericFontFamiliesHeader: String
    abstract val loadingSystemFonts: String
    abstract val systemFontsHeader: String
    abstract val noMatchingSystemFonts: String
    abstract val deleteImportedFontTitle: String
    abstract val deleteSelectedFontInUseDesc: String
    abstract val failedDeleteFontFile: String
    abstract val deleteImportedFontDesc: String

    // Completion messages, dedicated accessibility labels and error fallbacks
    abstract val issueClosedMessage: (Int) -> String
    abstract val issueReopenedMessage: (Int) -> String
    abstract val prConvertedToDraftMessage: String
    abstract val prMarkedReadyMessage: String
    abstract val diffCommentBodyLabel: String
    abstract val editCommentBodyLabel: String
    abstract val discussionCategoryAll: String
    abstract val failedLoadWorkflowRuns: String
    abstract val failedLoadCommit: String
    abstract val failedLoadLogs: String
    abstract val failedRerunWorkflow: String
    abstract val failedCancelRun: String
    abstract val markAllReadFailed: String
    abstract val refreshDiscussionsDesc: String
    abstract val refreshDiscussionDesc: String
    abstract val backToDiscussionsDesc: String
    abstract val discussionLoading: String
    abstract val cancelReplyDesc: String
    abstract val sendCommentDesc: String
    abstract val refreshSecurityDesc: String
    abstract val refreshReleasesDesc: String
    abstract val openReleaseInBrowserDesc: String
    abstract val downloadAssetDesc: String
    abstract val repoNewFileButton: String
    abstract val refreshCommitsDesc: String
    abstract val refreshCompareDesc: String
    abstract val swapBaseHeadDesc: String
    abstract val copyGistLinkDesc: String
    abstract val gistScopeErrorHint: String
    abstract val followScopeErrorHint: String
    abstract val authMethodDeviceFlow: String
    abstract val authMethodPat: String
    abstract val fontFileChooserTitle: String
    abstract val fontFileFilterLabel: String
    abstract val windowTitle: (String) -> String

    fun fontSelectionLabel(selection: FontSelection): String = when (selection.kind) {
        FontSourceKind.SYSTEM_DEFAULT -> systemDefaultLabel
        FontSourceKind.GENERIC_SANS -> fontGenericSans
        FontSourceKind.GENERIC_SERIF -> fontGenericSerif
        FontSourceKind.GENERIC_MONOSPACE -> fontGenericMonospace
        FontSourceKind.SYSTEM_FAMILY -> selection.displayName.ifBlank { selection.value }.ifBlank { systemDefaultLabel }
        FontSourceKind.CUSTOM_FILE -> selection.displayName.ifBlank {
            selection.value.substringAfterLast('/').substringAfterLast('\\')
        }
    }

    /** Each language is always shown in its own name, whatever the current UI language is. */
    fun languageOptionLabel(language: AppLanguage): String = when (language) {
        AppLanguage.DEFAULT -> "English"
        AppLanguage.JAPANESE -> "日本語"
    }

    fun authMethodLabel(method: AuthMethod): String = when (method) {
        AuthMethod.DEVICE_FLOW -> authMethodDeviceFlow
        AuthMethod.PERSONAL_ACCESS_TOKEN -> authMethodPat
    }

    fun issueFilterPresetLabel(preset: IssueFilterPreset): String = when (preset) {
        IssueFilterPreset.CREATED -> presetCreated
        IssueFilterPreset.ASSIGNED -> presetAssigned
        IssueFilterPreset.REVIEW_REQUESTED -> presetReviews
        IssueFilterPreset.MENTIONED -> presetMentioned
    }

    fun issueStateFilterLabel(filter: IssueStateFilter): String = when (filter) {
        IssueStateFilter.OPEN -> stateOpen
        IssueStateFilter.CLOSED -> stateClosed
        IssueStateFilter.ALL -> stateAll
    }

    fun searchKindLabel(kind: GlobalSearchKind): String = when (kind) {
        GlobalSearchKind.REPOSITORIES -> searchKindRepos
        GlobalSearchKind.ISSUES_PRS -> searchKindIssuesPrs
        GlobalSearchKind.CODE -> searchKindCode
        GlobalSearchKind.USERS -> searchKindUsers
    }

    fun mergeMethodLabel(method: MergeMethod): String = when (method) {
        MergeMethod.SQUASH -> mergeSquashLabel
        MergeMethod.MERGE -> mergeCommitLabel
        MergeMethod.REBASE -> mergeRebaseLabel
    }

    fun reviewEventLabel(event: ReviewEvent): String = when (event) {
        ReviewEvent.APPROVE -> reviewApproveLabel
        ReviewEvent.REQUEST_CHANGES -> reviewRequestChangesLabel
        ReviewEvent.COMMENT -> reviewCommentLabel
    }

    fun notificationSubjectLabel(type: NotificationSubjectType): String = when (type) {
        NotificationSubjectType.ISSUE -> subjectIssue
        NotificationSubjectType.PULL_REQUEST -> subjectPullRequest
        NotificationSubjectType.COMMIT -> subjectCommit
        NotificationSubjectType.RELEASE -> subjectRelease
        NotificationSubjectType.DISCUSSION -> subjectDiscussion
        NotificationSubjectType.CHECK_SUITE -> subjectCheckSuite
        NotificationSubjectType.UNKNOWN -> subjectNotification
    }

    /** Header badge for an Issue/PR state. */
    open fun issueStateBadgeLabel(state: IssueOrPullState, isDraft: Boolean): String = when {
        isDraft -> statusDraft
        state == IssueOrPullState.OPEN -> statusOpen
        state == IssueOrPullState.MERGED -> statusMerged
        else -> statusClosed
    }

    /** CI pill with a "CI:" prefix, e.g. "CI: SUCCESS". */
    fun formatCheckRollupLabel(state: String): String = "CI: ${formatCheckRollupState(state)}"

    /**
     * Text to show for a failed operation. [fallback] describes what failed; [notFoundHint] explains
     * a likely cause of an HTTP 404 (e.g. a missing token scope).
     */
    abstract fun errorMessage(error: Throwable, fallback: String, notFoundHint: String? = null): String

    /** Failed job log download. */
    abstract fun jobLogsErrorText(error: Throwable): String

    /** Why Dependabot alerts are missing from [overview], or null when they loaded. */
    abstract fun dependabotAlertsNoticeText(overview: RepoSecurityOverview): String?

    abstract fun noIssuesOrPrsTitle(state: IssueStateFilter): String
    abstract fun noIssuesOrPrsBody(state: IssueStateFilter, preset: IssueFilterPreset): String
    abstract fun repoItemsEmptyTitle(isPullRequests: Boolean, state: IssueStateFilter): String
    abstract fun repoItemsEmptyBody(isPullRequests: Boolean, state: IssueStateFilter): String
    abstract fun issueMergedMessage(number: Int, method: MergeMethod): String
    abstract fun mergeConfirmQuestion(baseRef: String?, method: MergeMethod): String
    abstract fun stateChangeConfirmQuestion(willOpen: Boolean, isPullRequest: Boolean): String
    abstract fun fileStatusBadge(status: FileChangeStatus): String
    abstract fun deviceFlowWaitingText(state: DeviceCodeState.AwaitingUser): String
    abstract fun deviceFlowVerifyingText(state: DeviceCodeState.Verifying): String
    abstract fun deviceFlowErrorText(state: DeviceCodeState.Error): String

    abstract fun formatEditorMetricsLabel(lines: Int, chars: Int): String
    abstract fun notificationReasonLabel(reason: NotificationReason): String
    /** Bare CI rollup state for list pills (no prefix). */
    abstract fun formatCheckRollupState(state: String): String
    abstract fun formatCheckConclusionLabel(label: String): String
    /** Check/run status as plain text (descriptions and accessibility labels). */
    abstract fun formatCheckStatusText(status: String): String
    abstract fun formatCompareStatusLabel(status: String): String
    abstract fun formatReviewStateLabel(state: String): String
    abstract fun formatAuthorAssociationLabel(association: String): String
    abstract fun formatReactionLabel(contentKey: String): String
    abstract fun formatSeverityLabel(severity: String): String
    /** Timeline line "actor + action" such as "@alice closed this"; a null actor is shown as the system. */
    abstract fun formatTimelineLine(actorLogin: String?, actionLabel: String): String
    /** Activity feed line "actor + summary" such as "@alice pushed 2 commits to main". */
    abstract fun formatActivityEventLine(event: ActivityEvent): String
}

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.DEFAULT -> EnglishStrings
    AppLanguage.JAPANESE -> JapaneseStrings
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

@Composable
fun appStrings(): AppStrings = LocalAppStrings.current
