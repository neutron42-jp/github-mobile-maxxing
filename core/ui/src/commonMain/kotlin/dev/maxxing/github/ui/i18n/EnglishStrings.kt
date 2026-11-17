package dev.maxxing.github.ui.i18n

import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.RepoSecurityOverview

object EnglishStrings : AppStrings() {
    override val loading = "Loading..."
    override val retry = "Retry"
    override val cancel = "Cancel"
    override val save = "Save"
    override val apply = "Apply"
    override val done = "Done"
    override val refresh = "Refresh"
    override val back = "Back"
    override val clear = "Clear"
    override val create = "Create"
    override val creating = "Creating..."
    override val newAction = "New"
    override val searchAction = "Search"
    override val selectOption = "Select option"
    override val selectedOption = "Selected"
    override val star = "Star"
    override val starred = "Starred"
    override val stars = "Stars"
    override val follow = "Follow"
    override val followingLabel = "Following"
    override val writeMode = "Write"
    override val previewMode = "Preview"
    override val closeEditor = "Close editor"
    override val closeAction = "Close"
    override val openInBrowser = "Open in browser"
    override val copyUrl = "Copy URL"
    override val share = "Share"
    override val shareEllipsis = "Share..."
    override val shareRepository = "Share repository"
    override val gistsAction = "Gists"
    override val privateBadge = "Private"
    override val privateRepo = "Private repository"
    override val publicRepo = "Public repository"
    override val fork = "Fork"
    override val notStarred = "Not starred"
    override val statusDraft = "Draft"
    override val statusOpen = "Open"
    override val statusMerged = "Merged"
    override val statusClosed = "Closed"
    override val unknownError = "Unknown error"
    override val failedLoadDetails = "Failed to load details"
    override val openDetails = "Open details"

    override val startingApp: (String) -> String = { appName -> "Starting $appName..." }
    override val navHome = "Home"
    override val navInbox = "Inbox"
    override val navIssues = "Issues"
    override val navRepos = "Repos"
    override val navSettings = "Settings"

    override val signInSubtitle = "Fast, Material 3 Expressive GitHub client for Android & Linux."
    override val signInClientIdLabel = "GitHub OAuth App Client ID"
    override val signInWithGitHubButton = "Sign in with GitHub"
    override val signInUseCustomClientId = "Use custom OAuth Client ID"
    override val signInHideCustomClientId = "Hide custom OAuth Client ID"
    override val signInClientIdDefaultSupporting: (String) -> String = { id -> "Defaults to $id. Enable Device Flow if overriding." }
    override val signInResetClientId = "Reset to default"
    override val signInRequestingCode = "Requesting device code from GitHub..."
    override val signInEnterCode = "Enter this code on GitHub"
    override val signInWaitingAuth = "Waiting for authorization..."
    override val signInCopyCode = "Copy Code"
    override val signInOpenGitHub = "Open GitHub"
    override val signInCopiedValue: (String) -> String = { value -> "Copied $value" }
    override val signInCodeCopiedOpening: (String) -> String = { code -> "Code $code copied. Opening GitHub..." }
    override val signInCodeCopiedManual: (String, String) -> String = { code, uri -> "Code $code copied. Open $uri in your browser." }
    override val signInWelcome: (String) -> String = { login -> "Welcome, @$login!" }

    override val settingsTitle = "Settings & Appearance"
    override val githubUserFallback = "GitHub User"
    override val unknownUser = "unknown"
    override val signOut = "Sign Out"
    override val apiRateLimit = "GitHub API Rate Limit"
    override val osNotificationsTitle = "GitHub OS Notifications"
    override val enableOsAlerts = "Enable OS Alerts"
    override val enableOsAlertsDesc = "Android notifications with Mark Read / Done actions & Linux notify-send."
    override val urgentOnly = "Urgent Only"
    override val urgentOnlyDesc = "Only alert for Review Requests, Mentions, Assignments, and Security."
    override val foregroundSyncInterval = "Foreground Sync Interval"
    override val pollIntervalMinutes: (Int) -> String = { minutes -> "$minutes min" }
    override val sendTestNotification = "Send Test Notification"
    override val testNotificationSent = "Test notification dispatched"
    override val testNotificationBlocked = "Notifications blocked or notify-send unavailable — check OS permissions"
    override val themeTitle = "Material 3 Expressive Theme"
    override val appearanceLabel = "Appearance"
    override val appearanceSystem = "System"
    override val appearanceLight = "Light"
    override val appearanceDark = "Dark"
    override val seedAccentLabel = "Expressive Seed Accent (SPEC_2025)"
    override val customColorLabel = "Custom"
    override val pickCustomColorDesc = "Pick custom color"
    override val customAccentColorTitle = "Custom Accent Color"
    override val hexColorLabel = "Hex (#RRGGBB)"
    override val dynamicColorTitle = "Android Wallpaper Dynamic Color"
    override val dynamicColorDesc = "Uses Material You system palette on Android 12+, falls back to seed on Linux."
    override val languageSectionTitle = "Language"
    override val languageLabel = "Display language"
    override val languageDesc = "Switch the UI display language immediately."
    override val linkHandlingTitle = "GitHub Link Handling"
    override val linkHandlingEnabledDesc = "github.com links are set to open in github-mobile-maxxing."
    override val linkHandlingOfficialClaimsDesc = "Official GitHub Mobile currently claims github.com links. Disable \"Open supported links\" there first, then enable github.com here."
    override val linkHandlingOfficialInstalledDesc = "Official GitHub Mobile is installed. Unlink it if it intercepts github.com links, then enable github.com for this app."
    override val linkHandlingDefaultDesc = "Enable github.com under Open by default so web links launch directly in this app."
    override val unlinkGitHubMobile = "Unlink GitHub Mobile"
    override val manageAppLinks = "Manage App Links"
    override val enableGitHubLinks = "Enable github.com Links"
    override val failedOpenGitHubMobileLinkSettings = "Could not open GitHub Mobile app link settings"
    override val failedOpenAppLinkSettings = "Could not open app link settings"
    override val oauthConfigTitle = "OAuth Client Configuration"
    override val oauthClientIdLabel = "GitHub OAuth Client ID"
    override val savedClientId = "Saved Client ID"

    override val loadingHome = "Loading GitHub home..."
    override val failedLoadActivity = "Failed to load activity feed"
    override val pinnedBookmarks = "Pinned Bookmarks"
    override val recentRepositories = "Recent Repositories"
    override val activityFeed = "Activity Feed"
    override val feedTabFollowing = "Following"
    override val feedTabMyActivity = "My Activity"
    override val noRecentActivity = "No recent activity events yet."
    override val contributionsTitle = "Contributions"
    override val contributionsInLastYear: (Int) -> String = { count -> "$count in the last year" }
    override val contributionsLess = "Less"
    override val contributionsMore = "More"

    override val inboxTitle = "Inbox"
    override val inboxFilterUnread = "Unread"
    override val inboxFilterParticipating = "Participating"
    override val inboxFilterAll = "All"
    override val reasonAll = "All Reasons"
    override val reasonReviews = "Review Req"
    override val reasonMentions = "Mentions"
    override val reasonAssigned = "Assigned"
    override val reasonCi = "CI"
    override val filterNotificationsPlaceholder = "Filter by repo or title..."
    override val syncingNotifications = "Syncing notifications..."
    override val failedLoadNotifications = "Failed to load notifications"
    override val inboxZeroTitle = "Inbox Zero!"
    override val inboxZeroUnreadHint = "No unread notifications. Check 'All' for already-read threads or tap Refresh."
    override val inboxZeroFilteredHint: (String) -> String = { filter -> "No notifications matching '$filter'." }
    override val inboxUpToDate = "Inbox is up to date (0 unread)"
    override val syncedUnreadCount: (Int) -> String = { count -> "Synced $count unread notifications" }
    override val syncedCount: (Int) -> String = { count -> "Synced $count notifications" }
    override val markedDone = "Marked done"
    override val doneAction = "Done"
    override val markDoneAction = "Mark done"
    override val markAllReadAction = "Mark all read"
    override val allNotificationsMarkedRead = "All notifications marked read"
    override val selectNotificationToTriage = "Select a notification to triage"

    override val issuesAndPrsTitle = "Issues & PRs"
    override val fetchingIssuesAndPrs = "Fetching your issues & PRs..."
    override val failedLoadIssuesAndPrs = "Failed to load issues and PRs"
    override val presetCreated = "Created"
    override val presetAssigned = "Assigned"
    override val presetReviews = "Reviews"
    override val presetMentioned = "Mentioned"
    override val stateOpen = "Open"
    override val stateClosed = "Closed"
    override val stateAll = "All"
    override val selectIssueOrPr = "Select an Issue or Pull Request"

    override val exploreTitle = "Explore"
    override val repositoriesTitle = "Repositories"
    override val repoTabMine = "My Repos"
    override val repoTabStarred = "Starred"
    override val repoTabExplore = "Explore"
    override val searchInLabel = "Search in"
    override val searchKindRepos = "Repos"
    override val searchKindIssuesPrs = "Issues/PRs"
    override val searchKindCode = "Code"
    override val searchKindUsers = "Users"
    override val searchReposPlaceholder = "Search repositories..."
    override val searchIssuesPlaceholder = "Search issues & PRs..."
    override val searchCodePlaceholder = "Search code (e.g. remember repo:foo/bar)..."
    override val searchUsersPlaceholder = "Search GitHub users & orgs..."
    override val trendingAll = "Trending All"
    override val loadingTrending = "Loading trending repositories..."
    override val loadingResults = "Loading results..."
    override val noTrendingFound = "No trending repositories found."
    override val noSearchMatches: (String, String) -> String = { kind, query -> "No $kind matched \"$query\"." }
    override val noStarredRepos = "No starred repositories found."
    override val noReposFound = "No repositories found."
    override val failedLoadTrending = "Failed to load trending repositories"
    override val failedLoadRepos = "Failed to load repositories"
    override val failedLoadStarredRepos = "Failed to load starred repositories"
    override val repoSearchFailed = "Repository search failed"
    override val issueSearchFailed = "Issue search failed"
    override val codeSearchFailed = "Code search failed"
    override val userSearchFailed = "User search failed"
    override val createRepoTitle = "Create repository"
    override val repoNameLabel = "Repository name"
    override val repoDescLabel = "Description (optional)"
    override val initWithReadme = "Initialize with README"
    override val createdRepoMessage: (String) -> String = { fullName -> "Created $fullName" }
    override val failedCreateRepo = "Failed to create repository"

    override val loadingUser: (String) -> String = { login -> "Loading @$login..." }
    override val failedLoadProfile = "Failed to load profile"
    override val orgBadge = "Org"
    override val organizationsTitle = "Organizations"
    override val profileTabRepos = "Repos"
    override val profileTabStarred = "Starred"
    override val profileTabActivity = "Activity"
    override val profileTabFollowers = "Followers"
    override val profileTabFollowing = "Following"
    override val noPublicRepos = "No public repositories."
    override val noStarredReposProfile = "No starred repositories."
    override val noRecentPublicActivity = "No recent public activity."
    override val noFollowersYet = "No followers yet."
    override val notFollowingAnyoneYet = "Not following anyone yet."
    override val followedUser: (String) -> String = { login -> "Following @$login" }
    override val unfollowedUser: (String) -> String = { login -> "Unfollowed @$login" }
    override val followUpdateFailed = "Follow update failed"

    override val gistsTitle = "Gists"
    override val userGistsTitle: (String) -> String = { user -> "@$user Gists" }
    override val gistsTabMine = "My Gists"
    override val gistsTabStarred = "Starred"
    override val missingGistScopeNotice = "Your current sign-in token lacks 'gist' scope. Sign out in Settings and sign in again to create, edit, star, or view secret Gists."
    override val loadingGists = "Loading gists..."
    override val failedLoadGists = "Failed to load gists"
    override val failedLoadGistDetails = "Failed to load gist details"
    override val noGistsFound = "No gists found."
    override val copiedGistLink = "Copied gist link"
    override val starredGist = "Starred gist"
    override val unstarredGist = "Unstarred gist"
    override val gistStarUpdateFailed = "Failed to update star"
    override val gistPublicBadge = "Public"
    override val gistSecretBadge = "Secret"
    override val gistFileCount: (Int) -> String = { count -> "$count file${if (count == 1) "" else "s"}" }
    override val gistNewTitle = "New Gist"
    override val gistEditTitle = "Edit Gist"
    override val gistDeleteTitle = "Delete Gist"
    override val gistDeleteConfirm: (String) -> String = { name -> "Permanently delete this gist ($name)?" }
    override val gistCreatedMessage = "Created gist"
    override val gistUpdatedMessage = "Updated gist"
    override val gistDeletedMessage = "Deleted gist"
    override val gistSaveFailed = "Failed to save gist"
    override val gistDeleteFailed = "Failed to delete gist"
    override val gistFilenameLabel = "Filename (e.g. notes.md)"
    override val gistContentsLabel = "Contents"
    override val gistPublicToggle = "Public Gist"
    override val gistPublicDesc = "Visible to everyone"
    override val gistSecretDesc = "Secret (link only)"
    override val gistEditAction = "Edit gist"
    override val gistDeleteAction = "Delete gist"
    override val deleteAction = "Delete"
    override val deletingAction = "Deleting..."
    override val savingAction = "Saving..."

    override val repoDetailTabCode = "Code"
    override val repoDetailTabIssues = "Issues"
    override val repoDetailTabPulls = "PRs"
    override val repoDetailTabActions = "Actions"
    override val repoDetailTabReleases = "Releases"
    override val repoDetailTabDiscussions = "Discussions"
    override val repoDetailTabSecurity = "Security"
    override val repoDetailTabInsights = "Insights"
    override val repoPinnedMessage: (String) -> String = { fullName -> "Pinned $fullName to Home" }
    override val repoUnpinnedMessage: (String) -> String = { fullName -> "Unpinned $fullName" }
    override val repoPinAction = "Pin repository"
    override val repoUnpinAction = "Unpin repository"
    override val repoForkedFrom: (String) -> String = { parent -> "Forked from $parent" }
    override val repoForkLabel = "Fork repository"
    override val repoStarredMessage: (String) -> String = { fullName -> "Starred $fullName" }
    override val repoUnstarredMessage: (String) -> String = { fullName -> "Unstarred $fullName" }
    override val repoWatching = "Watching"
    override val repoWatch = "Watch"
    override val repoWatchingMessage: (String) -> String = { fullName -> "Watching $fullName" }
    override val repoUnwatchedMessage: (String) -> String = { fullName -> "Unwatched $fullName" }
    override val repoWatchUpdateFailed = "Watch update failed"
    override val repoForking = "Forking..."
    override val repoForkedMessage: (String) -> String = { fullName -> "Forked to $fullName" }
    override val repoForkFailed = "Fork failed"
    override val repoNetwork = "Network"
    override val repoNetworkTitle: (String) -> String = { name -> "$name Network" }
    override val repoCompare = "Compare"
    override val repoCompareUpstream = "Compare upstream"
    override val repoCommitsButton = "Commits"
    override val repoEditFile = "Edit file"
    override val repoDeleteFile = "Delete file"
    override val repoBinaryFile: (Long) -> String = { size -> "Binary file ($size bytes)" }
    override val repoBranchOrTag = "Branch or tag"
    override val repoSwitchBranchOrTag = "Switch branch or tag"
    override val repoCheckingDivergence: (String) -> String = { label -> "Checking divergence against $label..." }
    override val repoEvenWithUpstream: (String, String) -> String = { label, ref -> "Even with $label (no unique fork commits on $ref)" }
    override val repoZeroAheadBehind: (Int, String) -> String = { behind, label -> "0 commits ahead · $behind behind $label (shares ancestor history)" }
    override val repoAheadBehind: (Int, Int, String) -> String = { ahead, behind, label -> "$ahead ahead · $behind behind $label" }
    override val repoAllCommits = "All commits"
    override val repoForkOnlyCommits: (Int) -> String = { ahead -> "Fork only ($ahead)" }
    override val repoLoadingCommits = "Loading commits..."
    override val repoNoCommitsFound: (String) -> String = { ref -> "No commits found for $ref" }
    override val repoForkSortStarred = "Most starred"
    override val repoForkSortNewest = "Newest"
    override val repoNoStargazers = "No stargazers yet."
    override val repoNoForks = "No forks yet."
    override val repoNoContributors = "No contributors listed."
    override val repoPeopleTabStars = "Stars"
    override val repoPeopleTabForks = "Forks"
    override val repoPeopleTabContributors = "Contributors"
    override val repoUpdatedDate: (String) -> String = { date -> "updated $date" }
    override val repoNewIssueTitle = "New Issue"
    override val repoNewPrTitle = "New Pull Request"
    override val repoCreatePrButton = "Create PR"
    override val repoIssueTitleLabel = "Title"
    override val repoPrTitleLabel = "PR Title"
    override val repoDraftPrToggle = "Create as draft PR"
    override val repoDescriptionMarkdown = "Description (Markdown)"
    override val repoCreatedIssueMessage: (Int) -> String = { num -> "Created Issue #$num" }
    override val repoCreatedPrMessage: (Int) -> String = { num -> "Created PR #$num" }
    override val repoCreateIssueFailed = "Failed to create issue"
    override val repoHeadLabel = "Head"
    override val repoBaseLabel = "Base"
    override val repoLoadingIssues = "Loading Issues..."
    override val repoLoadingPulls = "Loading Pull Requests..."
    override val repoRefreshIssues = "Refresh Issues"
    override val repoRefreshPulls = "Refresh Pull Requests"
    override val failedLoadDiscussions = "Failed to load discussions"
    override val failedLoadDiscussion = "Failed to load discussion"
    override val failedLoadSecurity = "Failed to load security overview"
    override val failedLoadInsights = "Failed to load insights"
    override val failedLoadReleases = "Failed to load releases"
    override val failedLoadActions = "Failed to load Actions runs"
    override val failedLoadJobs = "Failed to load jobs"
    override val failedLoadCommits = "Failed to load commits"
    override val failedLoadCommitDiff = "Failed to load commit diff"
    override val compareFailed = "Compare failed"
    override val failedLoadStargazers = "Failed to load stargazers"
    override val failedLoadForks = "Failed to load forks"
    override val failedLoadContributors = "Failed to load contributors"
    override val failedLoadDirectory = "Failed to load directory"
    override val failedLoadFile = "Failed to load file"
    override val failedLoadIssues = "Failed to load issues"
    override val failedLoadPulls = "Failed to load pull requests"
    override val failedCreateRelease = "Failed to create release"
    override val repoMetadataNotReady = "Repository metadata not ready yet"
    override val failedCreateDiscussion = "Failed to create discussion"
    override val failedCreatePr = "Failed to create pull request"
    override val failedPostComment = "Failed to post comment"

    override val commitVerifiedBadge = "Verified"
    override val commitVerifiedDesc = "Verified commit"
    override val commitFilesChangedSummary: (Int, Int, Int) -> String = { add, del, files -> "+$add / -$del ($files files)" }
    override val commitLoading: (String) -> String = { shortSha -> "Loading commit $shortSha..." }
    override val commitDetailsUnavailable = "Commit details unavailable."
    override val commitParentLabel: (String) -> String = { shortSha -> "Parent $shortSha" }
    override val compareLoading: (String, String) -> String = { base, head -> "Comparing $base...$head..." }
    override val compareSelectRefs = "Select two refs to compare."
    override val compareAheadBehind: (Int, Int) -> String = { ahead, behind -> "Ahead $ahead · Behind $behind" }
    override val compareSummaryCounts: (Int, Int) -> String = { commits, files -> "$commits commits · $files changed files" }
    override val compareCommitsHeader: (Int) -> String = { count -> "Commits ($count)" }
    override val compareChangedFilesHeader: (Int) -> String = { count -> "Changed Files ($count)" }
    override val actionsAllRuns = "All runs"
    override val actionsRunButton = "Run"
    override val actionsRefreshRuns = "Refresh runs"
    override val actionsLoadingRuns = "Loading workflow runs..."
    override val actionsNoRuns = "No workflow runs yet"
    override val actionsRunsBack = "Runs"
    override val actionsRefreshJobs = "Refresh jobs"
    override val actionsLoadingJobs = "Loading jobs..."
    override val actionsCancelRun = "Cancel Run"
    override val actionsRerunAll = "Re-run All"
    override val actionsRerunFailed = "Re-run Failed"
    override val actionsLogsButton = "Logs"
    override val actionsJobsBack = "Jobs"
    override val actionsLoadingLogs = "Loading logs..."
    override val actionsNoLogs = "No logs returned for this job."
    override val actionsRunWorkflowTitle = "Run Workflow"
    override val actionsSelectWorkflow = "Select workflow"
    override val actionsBranchOrTagLabel: (String) -> String = { defaultBranch -> "Branch or tag ref (e.g. $defaultBranch)" }
    override val actionsInputsLabel = "Inputs (optional, key=value per line)"
    override val actionsStarting = "Starting..."
    override val actionsDispatchedMessage: (String) -> String = { ref -> "Dispatched workflow on $ref" }
    override val actionsDispatchFailed = "Workflow dispatch failed"
    override val actionsRerunFailedError = "Re-run failed"
    override val actionsCancelFailedError = "Cancel failed"
    override val actionsLogsUnavailable = "Logs unavailable (check repo scope / expiration)"
    override val fileUntitledName = "file"
    override val fileCreateTitle = "Create File"
    override val fileEditTitle = "Edit File"
    override val fileCommitting = "Committing..."
    override val fileCommitButton = "Commit"
    override val fileContentsLabel = "File contents"
    override val filePathLabel = "Path (e.g. docs/notes.md)"
    override val fileCommitMessageLabel = "Commit message"
    override val fileBranchLabel = "Branch"
    override val fileDefaultCreateCommitMsg: (String) -> String = { name -> "Create $name" }
    override val fileDefaultUpdateCommitMsg: (String) -> String = { name -> "Update $name" }
    override val fileMetadataSummary: (String, String, String) -> String = { path, branch, msg -> "$path · branch: $branch · $msg" }
    override val fileCommittedMessage: (String, String) -> String = { path, shortSha ->
        if (shortSha.isNotBlank()) "Committed $path ($shortSha)" else "Committed $path"
    }
    override val fileCommitFailed = "Failed to commit file"
    override val fileDeleteTitle = "Delete File"
    override val fileDeleteConfirm: (String, String) -> String = { path, branch ->
        "Delete $path on branch $branch? This creates a new commit removing the file."
    }
    override val fileDeletedMessage: (String, String) -> String = { name, shortSha ->
        if (shortSha.isNotBlank()) "Deleted $name ($shortSha)" else "Deleted $name"
    }
    override val fileDeleteFailed = "Failed to delete file"

    override val insightsLoading = "Loading repository insights..."
    override val insightsHeader = "Pulse & Languages"
    override val insightsComputingNotice = "GitHub is generating commit & contributor analytics. Tap Refresh shortly."
    override val insightsLast4wCommits = "Last 4w commits"
    override val insights52wCommits = "52w commits"
    override val insightsLanguages = "Languages"
    override val insightsNoLanguages = "No language breakdown available for this repository."
    override val insightsWeeklyPulse = "Weekly Commit Pulse (16w)"
    override val insightsComputingPulse = "Commit frequency is currently being computed by GitHub."
    override val insightsNoWeeklyPulse = "No recent weekly commit activity recorded."
    override val insightsPulseSummary: (Int, Int) -> String = { peak, total -> "Peak week: $peak commits • Total (16w): $total commits" }
    override val insightsTopContributors = "Top Contributors"
    override val insightsNoContributors = "No contributor statistics available."
    override val insightsCommitCount: (Int) -> String = { count -> "$count commits" }

    override val releasesCount: (Int) -> String = { count -> "$count releases" }
    override val releasesHeader = "Releases"
    override val releasesNew = "New release"
    override val releasesLoading = "Loading releases..."
    override val releasesNoneTitle = "No releases published yet"
    override val releasesNoneWithTags: (Int) -> String = { count -> "$count tags found. Draft or publish a release from any tag." }
    override val releasesNoneEmpty = "Create a release to package binaries and release notes."
    override val releaseLatest = "Latest"
    override val releasePrerelease = "Pre-release"
    override val releaseShowNotes = "Show release notes"
    override val releaseHideNotes = "Hide release notes"
    override val releaseAssetsCount: (Int) -> String = { count -> "Assets ($count)" }
    override val releaseSourceZip = "Source code (zip)"
    override val releaseSourceTarGz = "Source code (tar.gz)"
    override val releaseDownloadsCount: (String) -> String = { count -> "$count downloads" }
    override val releaseDownloadArchiveDesc = "Download archive"
    override val releaseCreateTitle = "Create Release"
    override val releaseTargetBranchSubtitle: (String) -> String = { branch -> "Target: $branch" }
    override val releasePublishing = "Publishing..."
    override val releaseSaveDraft = "Save Draft"
    override val releasePublish = "Publish"
    override val releaseNotesLabel = "Release notes (Markdown)"
    override val releaseTagLabel = "Tag name (e.g. v1.2.0)"
    override val releaseTitleLabel = "Release title (optional)"
    override val releaseTargetLabel = "Target branch or SHA"
    override val releaseAutoNotes = "Auto-generate release notes"
    override val releaseDraftToggle = "Save as draft"

    override val discussionsCount: (Int) -> String = { count -> "$count discussions" }
    override val discussionsHeader = "Discussions"
    override val discussionsLoading = "Loading discussions..."
    override val discussionsDisabledOrEmpty = "Discussions disabled or empty"
    override val discussionsEmpty = "No discussions yet"
    override val discussionsHint = "Start a Q&A, idea, or announcement thread with the community."
    override val discussionAnswered = "Answered"
    override val discussionAnswer = "Answer"
    override val discussionRepliesCount: (Int) -> String = { count -> "$count replies" }
    override val discussionReplyAction = "Reply"
    override val discussionReplyingTo: (String) -> String = { login -> "Replying to @$login" }
    override val discussionWriteReply = "Write a reply..."
    override val discussionAddComment = "Add a comment..."
    override val discussionUpvoteDesc = "Upvote"
    override val discussionAuthorInCategory: (String, String) -> String = { author, cat -> "@$author in $cat" }
    override val discussionNewTitle = "New Discussion"
    override val discussionCategoryLabel = "Category"
    override val discussionBodyLabel = "Body (Markdown)"
    override val securityHeader = "Security & Dependabot"
    override val securityChecking = "Checking security alerts..."
    override val securityDependabotCount: (Int) -> String = { count -> "Dependabot Alerts ($count)" }
    override val securityAdvisoriesCount: (Int) -> String = { count -> "Security Advisories ($count)" }
    override val securityAllClearTitle = "No open Dependabot alerts or advisories"
    override val securityAllClearDesc = "Dependencies and advisories appear clean for this repository."
    override val securityAffected: (String) -> String = { range -> "Affected: $range" }
    override val securityFix: (String) -> String = { version -> "Fix: $version" }

    override val issueDetailTabChat = "Chat"
    override val issueDetailTabChecks = "Checks"
    override val issueDetailTabFiles = "Files"
    override val issueLoadingNumber: (Int) -> String = { num -> "Loading #$num..." }
    override val issueLoadingChecks = "Loading CI check runs..."
    override val issueNoChecks = "No CI check runs reported"
    override val issueRefreshChecks = "Refresh Checks"
    override val issueReviewAction = "Review"
    override val issueReopenAction = "Reopen"
    override val issueCloseAction = "Close"
    override val issueMergeAction = "Merge"
    override val issueMergeConfirmTitle: (Int) -> String = { num -> "Merge #$num?" }
    override val issueMergeFailed = "Merge failed"
    override val issueReopenConfirmTitle: (Int) -> String = { num -> "Reopen #$num?" }
    override val issueCloseConfirmTitle: (Int) -> String = { num -> "Close #$num?" }
    override val issueStateUpdateFailed = "Failed to update state"
    override val issueCommentTitle: (Int) -> String = { num -> "Comment on #$num" }
    override val issueCommentPost = "Post"
    override val issueCommentLabel = "Markdown comment"
    override val issueCommentPosted = "Comment posted"
    override val issueCommentPostFailed = "Failed to post comment"
    override val issueSubmitReviewTitle = "Submit PR Review"
    override val issueSubmitAction = "Submit"
    override val issueReviewSummaryLabel = "Review summary (optional for Approve)"
    override val issueReviewSubmitted: (String) -> String = { label -> "Review submitted ($label)" }
    override val issueReviewFailed = "Review submission failed"
    override val issueDecisionLabel = "Decision"
    override val labelsTitle = "Labels"
    override val selectLabelsTitle = "Select Labels"
    override val loadingLabels = "Loading labels..."
    override val issueManageLabelsTitle = "Manage Labels"
    override val issueNoLabels = "No labels found in repository."
    override val issueNewLabel = "New label"
    override val createLabelAction = "Create label"
    override val createdLabelMessage: (String) -> String = { name -> "Created label $name" }
    override val failedCreateLabel = "Failed to create label"
    override val failedLoadLabels = "Failed to load labels"
    override val issueLabelsUpdated = "Updated labels"
    override val failedUpdateLabels = "Failed to update labels"
    override val issueManageAssigneesTitle = "Manage Assignees"
    override val issueNoAssignees = "No assignable users returned."
    override val failedLoadAssignees = "Failed to load assignees"
    override val issueAssigneesUpdated = "Updated assignees"
    override val failedUpdateAssignees = "Failed to update assignees"
    override val issueSelectMilestoneTitle = "Select Milestone"
    override val issueNoMilestone = "No milestone"
    override val failedLoadMilestones = "Failed to load milestones"
    override val issueMilestoneUpdated = "Updated milestone"
    override val issueMilestoneCleared = "Cleared milestone"
    override val failedUpdateMilestone = "Failed to update milestone"
    override val milestoneDue: (String) -> String = { due -> "Due $due" }
    override val failedLoadCiChecks = "Failed to load CI checks"
    override val failedUpdateDraftState = "Failed to update draft state"
    override val reactionFailed = "Reaction failed"
    override val addReactionDesc = "Add reaction"
    override val reactedDesc = "Reacted"
    override val noFileDiffs = "No file diffs returned for this pull request."
    override val reviewCommentLineTitle: (String, Int) -> String = { file, line -> "Review Comment: $file:$line" }
    override val fileCommentTitle: (String) -> String = { file -> "File Comment: $file" }
    override val addedInlineDiffComment = "Added inline diff comment"
    override val failedPostDiffComment = "Failed to post diff comment"
    override val issueEditCommentTitle = "Edit Comment"
    override val commentOnIssueSubtitle: (String, Int) -> String = { login, num -> "@$login on #$num" }
    override val issueCommentUpdated = "Updated comment"
    override val failedUpdateComment = "Failed to update comment"
    override val issueDeleteCommentTitle = "Delete Comment?"
    override val issueDeleteCommentConfirm: (String) -> String = { login -> "Are you sure you want to delete this comment by @$login?" }
    override val issueCommentDeleted = "Deleted comment"
    override val failedDeleteComment = "Failed to delete comment"
    override val editCommentDesc = "Edit comment"
    override val deleteCommentDesc = "Delete comment"
    override val issueRequestReviewersTitle = "Request Reviewers"
    override val issueUsernameLabel = "GitHub username"
    override val issueRequestAction = "Request"
    override val addReviewerDesc = "Add reviewer"
    override val requestedReviewersMessage: (String) -> String = { reviewers -> "Requested reviewers: $reviewers" }
    override val failedRequestReviewers = "Failed to request reviewers"
    override val reviewersHeader = "Reviewers:"
    override val systemActorFallback = "system"
    override val issueLabelsCount: (Int) -> String = { count -> "Labels ($count)" }
    override val issueAssigneesCount: (Int) -> String = { count -> "Assignees ($count)" }
    override val issueMilestoneFallback = "Milestone"
    override val issueReviewersCount: (Int) -> String = { count -> "Reviewers ($count)" }
    override val issueReadyForReview = "Ready for review"
    override val issueConvertToDraft = "Convert to draft"
    override val issueReactAction = "React"
    override val mergeSquashLabel = "Squash and merge"
    override val mergeCommitLabel = "Create a merge commit"
    override val mergeRebaseLabel = "Rebase and merge"
    override val reviewApproveLabel = "Approve"
    override val reviewRequestChangesLabel = "Request changes"
    override val reviewCommentLabel = "Comment"

    override val collapseDetails = "Collapse details"
    override val expandDetails = "Expand details"
    override val nothingToPreview = "Nothing to preview yet."
    override val monospaceCodeHint = "Monospace code"
    override val markdownSupportedHint = "Markdown supported"
    override val openFullScreenDiff = "Open full-screen diff"
    override val closeDiffViewer = "Close diff viewer"
    override val commentOnFile = "Comment on file"
    override val diffScrollMode = "Scroll"
    override val diffWrapMode = "Wrap"
    override val diffPrevFile = "Prev file"
    override val diffNextFile = "Next file"
    override val diffTapLineHint = "Tap any diff line to add a review comment."
    override val diffBinaryOrLarge = "Binary file or diff too large to display inline."
    override val diffEmptyPatch = "Empty patch."

    override val notifUpdatedAt: (String) -> String = { time -> "Updated: $time" }
    override val notifOpenOnGitHub = "Open on GitHub"
    override val notifLoadingDetails: (String) -> String = { kind -> "Loading $kind details..." }
    override val notifCheckRunsTitle = "Check Runs"
    override val notifWorkflowRunsTitle = "Recent Workflow Runs"
    override val notifRerunningFailed = "Re-running failed jobs"
    override val notifRerunningWorkflow = "Re-running workflow"
    override val notifCancelRequested = "Cancellation requested"
    override val subjectIssue = "Issue"
    override val subjectPullRequest = "Pull Request"
    override val subjectCommit = "Commit"
    override val subjectRelease = "Release"
    override val subjectDiscussion = "Discussion"
    override val subjectCheckSuite = "CI / Check Suite"
    override val subjectNotification = "Notification"
    override val osNotifUrgentChannelName = "Mentions & Review Requests"
    override val osNotifUrgentChannelDesc = "High-priority GitHub mentions, review requests, and assignments."
    override val osNotifActivityChannelName = "Repository Activity"
    override val osNotifActivityChannelDesc = "General GitHub repository updates and subscribed threads."
    override val osNotifMarkReadAction = "Mark Read"
    override val osNotifBatchTitle: (Int) -> String = { count -> "$count new GitHub notifications" }
    override val osNotifTestReadyTitle: (String) -> String = { appName -> "$appName Notifications Ready" }
    override val osNotifTestReadyBody = "New mentions & review requests will appear here with Mark Read / Done actions."
    override val osNotifDesktopTestTitle: (String) -> String = { appName -> "$appName Test Notification" }
    override val osNotifDesktopTestBody = "Desktop notifications are active and working!"

    override val fontSectionTitle = "Typography & Fonts"
    override val primaryFontLabel = "Primary Font"
    override val primaryFontDesc = "Used first for Latin text, numbers, and symbols"
    override val fallbackFontLabel = "Fallback Font"
    override val fallbackFontDesc = "Used for Japanese / CJK and glyphs missing in primary"
    override val fallbackFontRequiresAndroid10 = "Requires Android 10 or later"
    override val resetFontsToSystemDefault = "Reset to System Default"
    override val fontPreviewTitle = "Preview"
    override val fontPreviewPrimarySample = "The quick brown fox jumps over the lazy dog 0123456789"
    override val fontPreviewSecondarySample = "素早い茶色の狐が怠け者の犬を飛び越える — GitHub Pull Request #42"
    override val selectPrimaryFontTitle = "Select Primary Font"
    override val selectFallbackFontTitle = "Select Fallback Font"
    override val importedFontMessage: (String) -> String = { name -> "Imported font: $name" }
    override val failedImportFont = "Failed to import font file"
    override val importFontFileButton = "Import font file (.ttf / .otf / .ttc)"
    override val searchSystemFontsPlaceholder = "Search system fonts..."
    override val systemDefaultLabel = "System Default"
    override val fontGenericSans = "Sans-serif"
    override val fontGenericSerif = "Serif"
    override val fontGenericMonospace = "Monospace"
    override val importedFontFilesHeader = "Imported Font Files"
    override val genericFontFamiliesHeader = "Generic Families"
    override val loadingSystemFonts = "Loading system fonts..."
    override val systemFontsHeader = "System Fonts"
    override val noMatchingSystemFonts = "No matching system fonts"
    override val deleteImportedFontTitle = "Delete imported font?"
    override val deleteSelectedFontInUseDesc = "This font is currently selected. Text will use System Default instead."
    override val failedDeleteFontFile = "Failed to delete font file"
    override val deleteImportedFontDesc = "Delete imported font"

    override val issueClosedMessage: (Int) -> String = { num -> "Closed #$num" }
    override val issueReopenedMessage: (Int) -> String = { num -> "Reopened #$num" }
    override val prConvertedToDraftMessage = "Converted PR to draft"
    override val prMarkedReadyMessage = "Marked PR ready for review"
    override val diffCommentBodyLabel = "Comment (Markdown)"
    override val editCommentBodyLabel = "Comment body (Markdown)"
    override val discussionCategoryAll = "All"
    override val failedLoadWorkflowRuns = "Failed to load workflow runs"
    override val failedLoadCommit = "Failed to load commit"
    override val failedLoadLogs = "Failed to load logs"
    override val failedRerunWorkflow = "Failed to re-run workflow"
    override val failedCancelRun = "Failed to cancel run"
    override val markAllReadFailed = "Failed"
    override val refreshDiscussionsDesc = "Refresh discussions"
    override val refreshDiscussionDesc = "Refresh discussion"
    override val backToDiscussionsDesc = "Back to discussions"
    override val discussionLoading = "Loading discussion..."
    override val cancelReplyDesc = "Cancel reply"
    override val sendCommentDesc = "Send comment"
    override val refreshSecurityDesc = "Refresh security alerts"
    override val refreshReleasesDesc = "Refresh releases"
    override val openReleaseInBrowserDesc = "Open release in browser"
    override val downloadAssetDesc = "Download asset"
    override val repoNewFileButton = "File"
    override val refreshCommitsDesc = "Refresh Commits"
    override val refreshCompareDesc = "Refresh Compare"
    override val swapBaseHeadDesc = "Swap base and head"
    override val copyGistLinkDesc = "Copy gist link"
    override val gistScopeErrorHint = "Your token may lack the 'gist' scope — sign out and sign in again (or add 'gist' scope)."
    override val followScopeErrorHint = "Your token may lack the 'user:follow' scope — sign out and sign in again (or add 'user:follow' scope)."
    override val authMethodDeviceFlow = "OAuth device flow"
    override val authMethodPat = "Personal access token"
    override val fontFileChooserTitle = "Select Font File (.ttf / .otf / .ttc)"
    override val fontFileFilterLabel = "Font files (*.ttf, *.otf, *.ttc)"
    override val windowTitle: (String) -> String = { appName -> "$appName — GitHub Client" }

    /** English keeps the network layer's detailed message, as before localization. */
    override fun errorMessage(error: Throwable, fallback: String, notFoundHint: String?): String =
        error.message ?: fallback

    override fun jobLogsErrorText(error: Throwable): String = errorMessage(error, actionsLogsUnavailable)

    override fun dependabotAlertsNoticeText(overview: RepoSecurityOverview): String? = overview.alertsAccessNotice

    override fun noIssuesOrPrsTitle(state: IssueStateFilter): String =
        "No ${issueStateFilterLabel(state)} Issues or PRs"

    override fun noIssuesOrPrsBody(state: IssueStateFilter, preset: IssueFilterPreset): String =
        "There are no ${issueStateFilterLabel(state).lowercase()} items for '${preset.name.lowercase().replace('_', ' ')}'."

    override fun repoItemsEmptyTitle(isPullRequests: Boolean, state: IssueStateFilter): String =
        "No ${issueStateFilterLabel(state)} ${if (isPullRequests) "Pull Requests" else "Issues"}"

    override fun repoItemsEmptyBody(isPullRequests: Boolean, state: IssueStateFilter): String =
        "There are no ${issueStateFilterLabel(state).lowercase()} ${if (isPullRequests) "pull requests" else "issues"} in this repository."

    override fun issueMergedMessage(number: Int, method: MergeMethod): String = "Merged #$number (${method.apiValue})"

    override fun mergeConfirmQuestion(baseRef: String?, method: MergeMethod): String =
        "Are you sure you want to merge into ${baseRef ?: "base"} using ${mergeMethodLabel(method)}?"

    override fun stateChangeConfirmQuestion(willOpen: Boolean, isPullRequest: Boolean): String =
        "Are you sure you want to ${if (willOpen) "reopen" else "close"} this ${if (isPullRequest) "pull request" else "issue"}?"

    override fun issueStateBadgeLabel(state: IssueOrPullState, isDraft: Boolean): String =
        super.issueStateBadgeLabel(state, isDraft).uppercase()

    override fun fileStatusBadge(status: FileChangeStatus): String = status.name.take(3)

    override fun deviceFlowWaitingText(state: DeviceCodeState.AwaitingUser): String =
        state.statusMessage ?: signInWaitingAuth

    override fun deviceFlowVerifyingText(state: DeviceCodeState.Verifying): String = state.message

    override fun deviceFlowErrorText(state: DeviceCodeState.Error): String = state.message

    override fun formatEditorMetricsLabel(lines: Int, chars: Int): String {
        val lineUnit = if (lines == 1) "line" else "lines"
        val charUnit = if (chars == 1) "char" else "chars"
        return "$lines $lineUnit · $chars $charUnit"
    }

    override fun notificationReasonLabel(reason: NotificationReason): String =
        reason.name.lowercase().replace('_', ' ')

    override fun formatCheckRollupState(state: String): String = state

    override fun formatCheckConclusionLabel(label: String): String = label.uppercase()

    override fun formatCheckStatusText(status: String): String = status

    override fun formatCompareStatusLabel(status: String): String = status.uppercase()

    override fun formatReviewStateLabel(state: String): String = state.replace('_', ' ')

    override fun formatAuthorAssociationLabel(association: String): String = association

    override fun formatReactionLabel(contentKey: String): String =
        contentKey.lowercase().replace('_', ' ')

    override fun formatSeverityLabel(severity: String): String = severity.uppercase()

    override fun formatTimelineLine(actorLogin: String?, actionLabel: String): String =
        "@${actorLogin ?: systemActorFallback} $actionLabel"

    override fun formatActivityEventLine(event: ActivityEvent): String = "@${event.actor.login} ${event.summaryText}"
}
