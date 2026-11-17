package dev.maxxing.github.ui.i18n

import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.ActivityEventKind
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.DeviceFlowErrorKind
import dev.maxxing.github.model.DeviceFlowWaitReason
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.HttpStatusError
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.RepoSecurityOverview

object JapaneseStrings : AppStrings() {
    override val loading = "読み込み中..."
    override val retry = "再試行"
    override val cancel = "キャンセル"
    override val save = "保存"
    override val apply = "適用"
    override val done = "完了"
    override val refresh = "更新"
    override val back = "戻る"
    override val clear = "クリア"
    override val create = "作成"
    override val creating = "作成中..."
    override val newAction = "新規"
    override val searchAction = "検索"
    override val selectOption = "選択"
    override val selectedOption = "選択済み"
    override val star = "スター"
    override val starred = "スター済み"
    override val stars = "スター"
    override val follow = "フォロー"
    override val followingLabel = "フォロー中"
    override val writeMode = "編集"
    override val previewMode = "プレビュー"
    override val closeEditor = "エディタを閉じる"
    override val closeAction = "閉じる"
    override val openInBrowser = "ブラウザで開く"
    override val copyUrl = "URL をコピー"
    override val share = "共有"
    override val shareEllipsis = "共有..."
    override val shareRepository = "リポジトリを共有"
    override val gistsAction = "Gist"
    override val privateBadge = "非公開"
    override val privateRepo = "非公開リポジトリ"
    override val publicRepo = "公開リポジトリ"
    override val fork = "フォーク"
    override val notStarred = "スターなし"
    override val statusDraft = "下書き"
    override val statusOpen = "オープン"
    override val statusMerged = "マージ済み"
    override val statusClosed = "クローズ"
    override val unknownError = "不明なエラー"
    override val failedLoadDetails = "詳細の読み込みに失敗しました"
    override val openDetails = "詳細を開く"

    override val startingApp: (String) -> String = { appName -> "$appName を起動中..." }
    override val navHome = "ホーム"
    override val navInbox = "受信箱"
    override val navIssues = "Issue"
    override val navRepos = "リポジトリ"
    override val navSettings = "設定"

    override val signInSubtitle = "Android・Linux 向けの高速な Material 3 Expressive GitHub クライアント。"
    override val signInClientIdLabel = "GitHub OAuth App クライアント ID"
    override val signInWithGitHubButton = "GitHub でサインイン"
    override val signInUseCustomClientId = "独自の OAuth クライアント ID を使う"
    override val signInHideCustomClientId = "独自の OAuth クライアント ID を隠す"
    override val signInClientIdDefaultSupporting: (String) -> String = { id -> "既定値は $id です。変更する場合は、その OAuth App で Device Flow を有効にしてください。" }
    override val signInResetClientId = "既定値に戻す"
    override val signInRequestingCode = "GitHub からデバイスコードを取得中..."
    override val signInEnterCode = "GitHub でこのコードを入力してください"
    override val signInWaitingAuth = "認証の完了を待機中..."
    override val signInCopyCode = "コードをコピー"
    override val signInOpenGitHub = "GitHub を開く"
    override val signInCopiedValue: (String) -> String = { value -> "$value をコピーしました" }
    override val signInCodeCopiedOpening: (String) -> String = { code -> "コード $code をコピーしました。GitHub を開きます..." }
    override val signInCodeCopiedManual: (String, String) -> String = { code, uri -> "コード $code をコピーしました。ブラウザで $uri を開いてください。" }
    override val signInWelcome: (String) -> String = { login -> "ようこそ、@$login さん！" }

    override val settingsTitle = "設定と外観"
    override val githubUserFallback = "GitHub ユーザー"
    override val unknownUser = "不明"
    override val signOut = "サインアウト"
    override val apiRateLimit = "GitHub API レート制限"
    override val osNotificationsTitle = "OS 通知設定"
    override val enableOsAlerts = "OS 通知を有効化"
    override val enableOsAlertsDesc = "Android 通知（既読・完了アクション付き）および Linux notify-send を使用します。"
    override val urgentOnly = "重要な通知のみ"
    override val urgentOnlyDesc = "レビュー依頼、メンション、アサイン、セキュリティ警告のみ通知します。"
    override val foregroundSyncInterval = "フォアグラウンド同期間隔"
    override val pollIntervalMinutes: (Int) -> String = { minutes -> "${minutes} 分" }
    override val sendTestNotification = "テスト通知を送信"
    override val testNotificationSent = "テスト通知を送信しました"
    override val testNotificationBlocked = "通知がブロックされているか notify-send を利用できません。OS の権限を確認してください"
    override val themeTitle = "Material 3 Expressive テーマ"
    override val appearanceLabel = "外観モード"
    override val appearanceSystem = "システム"
    override val appearanceLight = "ライト"
    override val appearanceDark = "ダーク"
    override val seedAccentLabel = "アクセントカラー"
    override val customColorLabel = "カスタム"
    override val pickCustomColorDesc = "カスタムカラーを選択"
    override val customAccentColorTitle = "カスタムカラー"
    override val hexColorLabel = "カラーコード (#RRGGBB)"
    override val dynamicColorTitle = "Android 壁紙ダイナミックカラー"
    override val dynamicColorDesc = "Android 12 以降では Material You のシステム配色を使用し、Linux では上のアクセントカラーを使用します。"
    override val languageSectionTitle = "言語"
    override val languageLabel = "表示言語"
    override val languageDesc = "アプリの UI 表示言語を即座に切り替えます。"
    override val linkHandlingTitle = "GitHub リンクの開き方"
    override val linkHandlingEnabledDesc = "github.com のリンクは github-mobile-maxxing で開くよう設定されています。"
    override val linkHandlingOfficialClaimsDesc = "公式 GitHub Mobile アプリが github.com リンクに関連付けられています。先に公式アプリ側で対応リンクを開く設定を無効にしてから、こちらを有効にしてください。"
    override val linkHandlingOfficialInstalledDesc = "公式 GitHub Mobile アプリがインストールされています。リンクが奪われる場合は関連付けを解除し、このアプリで github.com を有効にしてください。"
    override val linkHandlingDefaultDesc = "デフォルトで開く設定から github.com を有効にすると、Web リンクをこのアプリで直接開けます。"
    override val unlinkGitHubMobile = "GitHub Mobile の関連付けを解除"
    override val manageAppLinks = "アプリリンクを管理"
    override val enableGitHubLinks = "github.com リンクを有効化"
    override val failedOpenGitHubMobileLinkSettings = "GitHub Mobile のアプリリンク設定を開けませんでした"
    override val failedOpenAppLinkSettings = "アプリリンク設定を開けませんでした"
    override val oauthConfigTitle = "OAuth クライアント設定"
    override val oauthClientIdLabel = "GitHub OAuth クライアント ID"
    override val savedClientId = "クライアント ID を保存しました"

    override val loadingHome = "GitHub ホームを読み込み中..."
    override val failedLoadActivity = "アクティビティフィードの読み込みに失敗しました"
    override val pinnedBookmarks = "ピン留めブックマーク"
    override val recentRepositories = "最近のリポジトリ"
    override val activityFeed = "アクティビティフィード"
    override val feedTabFollowing = "フォロー中"
    override val feedTabMyActivity = "自分のアクティビティ"
    override val noRecentActivity = "最近のアクティビティはまだありません。"
    override val contributionsTitle = "コントリビューション"
    override val contributionsInLastYear: (Int) -> String = { count -> "過去 1 年間で $count 件" }
    override val contributionsLess = "少"
    override val contributionsMore = "多"

    override val inboxTitle = "受信箱"
    override val inboxFilterUnread = "未読"
    override val inboxFilterParticipating = "関与中"
    override val inboxFilterAll = "すべて"
    override val reasonAll = "すべての理由"
    override val reasonReviews = "レビュー依頼"
    override val reasonMentions = "メンション"
    override val reasonAssigned = "アサイン"
    override val reasonCi = "CI"
    override val filterNotificationsPlaceholder = "通知を絞り込み..."
    override val syncingNotifications = "通知を同期中..."
    override val failedLoadNotifications = "通知の読み込みに失敗しました"
    override val inboxZeroTitle = "受信箱は空です！"
    override val inboxZeroUnreadHint = "未読の通知はありません。既読のスレッドは「すべて」で確認できます。新しい通知は更新して確認してください。"
    override val inboxZeroFilteredHint: (String) -> String = { filter -> "「$filter」に一致する通知はありません。" }
    override val inboxUpToDate = "受信箱は最新です（未読 0 件）"
    override val syncedUnreadCount: (Int) -> String = { count -> "${count} 件の未読通知を同期しました" }
    override val syncedCount: (Int) -> String = { count -> "${count} 件の通知を同期しました" }
    override val markedDone = "完了にしました"
    override val doneAction = "完了"
    override val markDoneAction = "完了にする"
    override val markAllReadAction = "すべて既読にする"
    override val allNotificationsMarkedRead = "すべての通知を既読にしました"
    override val selectNotificationToTriage = "確認する通知を選択してください"

    override val issuesAndPrsTitle = "Issue と PR"
    override val fetchingIssuesAndPrs = "Issue と PR を取得中..."
    override val failedLoadIssuesAndPrs = "Issue と PR の読み込みに失敗しました"
    override val presetCreated = "自分が作成"
    override val presetAssigned = "自分にアサイン"
    override val presetReviews = "レビュー依頼"
    override val presetMentioned = "メンション"
    override val stateOpen = "オープン"
    override val stateClosed = "クローズ"
    override val stateAll = "すべて"
    override val selectIssueOrPr = "Issue またはプルリクエストを選択してください"

    override val exploreTitle = "探索"
    override val repositoriesTitle = "リポジトリ"
    override val repoTabMine = "自分"
    override val repoTabStarred = "スター"
    override val repoTabExplore = "探索"
    override val searchInLabel = "検索対象"
    override val searchKindRepos = "リポジトリ"
    override val searchKindIssuesPrs = "Issue / PR"
    override val searchKindCode = "コード"
    override val searchKindUsers = "ユーザー"
    override val searchReposPlaceholder = "リポジトリを検索..."
    override val searchIssuesPlaceholder = "Issue と PR を検索..."
    override val searchCodePlaceholder = "コードを検索（例: remember repo:foo/bar）..."
    override val searchUsersPlaceholder = "GitHub ユーザー・Org を検索..."
    override val trendingAll = "すべての言語"
    override val loadingTrending = "トレンドリポジトリを読み込み中..."
    override val loadingResults = "検索結果を読み込み中..."
    override val noTrendingFound = "トレンドリポジトリが見つかりません。"
    override val noSearchMatches: (String, String) -> String = { kind, query -> "「$query」に一致する結果はありません（検索対象: $kind）。" }
    override val noStarredRepos = "スター済みのリポジトリはありません。"
    override val noReposFound = "リポジトリが見つかりません。"
    override val failedLoadTrending = "トレンドリポジトリの読み込みに失敗しました"
    override val failedLoadRepos = "リポジトリの読み込みに失敗しました"
    override val failedLoadStarredRepos = "スター済みリポジトリの読み込みに失敗しました"
    override val repoSearchFailed = "リポジトリ検索に失敗しました"
    override val issueSearchFailed = "Issue 検索に失敗しました"
    override val codeSearchFailed = "コード検索に失敗しました"
    override val userSearchFailed = "ユーザー検索に失敗しました"
    override val createRepoTitle = "リポジトリを作成"
    override val repoNameLabel = "リポジトリ名"
    override val repoDescLabel = "説明（任意）"
    override val initWithReadme = "README で初期化する"
    override val createdRepoMessage: (String) -> String = { fullName -> "$fullName を作成しました" }
    override val failedCreateRepo = "リポジトリの作成に失敗しました"

    override val loadingUser: (String) -> String = { login -> "@$login を読み込み中..." }
    override val failedLoadProfile = "プロフィールの読み込みに失敗しました"
    override val orgBadge = "Org"
    override val organizationsTitle = "Organization"
    override val profileTabRepos = "リポジトリ"
    override val profileTabStarred = "スター済み"
    override val profileTabActivity = "アクティビティ"
    override val profileTabFollowers = "フォロワー"
    override val profileTabFollowing = "フォロー中"
    override val noPublicRepos = "公開リポジトリはありません。"
    override val noStarredReposProfile = "スター済みリポジトリはありません。"
    override val noRecentPublicActivity = "最近の公開アクティビティはありません。"
    override val noFollowersYet = "フォロワーはまだいません。"
    override val notFollowingAnyoneYet = "まだ誰もフォローしていません。"
    override val followedUser: (String) -> String = { login -> "@$login をフォローしました" }
    override val unfollowedUser: (String) -> String = { login -> "@$login のフォローを解除しました" }
    override val followUpdateFailed = "フォロー状態の更新に失敗しました"

    override val gistsTitle = "Gist"
    override val userGistsTitle: (String) -> String = { user -> "@$user の Gist" }
    override val gistsTabMine = "自分の Gist"
    override val gistsTabStarred = "スター済み"
    override val missingGistScopeNotice = "現在のトークンには 'gist' スコープがありません。Gist の作成・編集・スター、およびシークレット Gist の表示を行うには、設定からサインアウトして再度サインインしてください。"
    override val loadingGists = "Gist を読み込み中..."
    override val failedLoadGists = "Gist の読み込みに失敗しました"
    override val failedLoadGistDetails = "Gist 詳細の読み込みに失敗しました"
    override val noGistsFound = "Gist が見つかりません。"
    override val copiedGistLink = "Gist のリンクをコピーしました"
    override val starredGist = "Gist にスターを付けました"
    override val unstarredGist = "Gist のスターを外しました"
    override val gistStarUpdateFailed = "スター状態の更新に失敗しました"
    override val gistPublicBadge = "公開"
    override val gistSecretBadge = "シークレット"
    override val gistFileCount: (Int) -> String = { count -> "${count} ファイル" }
    override val gistNewTitle = "新規 Gist"
    override val gistEditTitle = "Gist を編集"
    override val gistDeleteTitle = "Gist を削除"
    override val gistDeleteConfirm: (String) -> String = { name -> "この Gist（$name）を完全に削除しますか？" }
    override val gistCreatedMessage = "Gist を作成しました"
    override val gistUpdatedMessage = "Gist を更新しました"
    override val gistDeletedMessage = "Gist を削除しました"
    override val gistSaveFailed = "Gist の保存に失敗しました"
    override val gistDeleteFailed = "Gist の削除に失敗しました"
    override val gistFilenameLabel = "ファイル名（例: notes.md）"
    override val gistContentsLabel = "内容"
    override val gistPublicToggle = "公開 Gist"
    override val gistPublicDesc = "すべてのユーザーに公開されます"
    override val gistSecretDesc = "シークレット（リンクを知っている人のみ）"
    override val gistEditAction = "Gist を編集"
    override val gistDeleteAction = "Gist を削除"
    override val deleteAction = "削除"
    override val deletingAction = "削除中..."
    override val savingAction = "保存中..."

    override val repoDetailTabCode = "コード"
    override val repoDetailTabIssues = "Issue"
    override val repoDetailTabPulls = "PR"
    override val repoDetailTabActions = "Actions"
    override val repoDetailTabReleases = "リリース"
    override val repoDetailTabDiscussions = "ディスカッション"
    override val repoDetailTabSecurity = "セキュリティ"
    override val repoDetailTabInsights = "統計"
    override val repoPinnedMessage: (String) -> String = { fullName -> "$fullName をホームにピン留めしました" }
    override val repoUnpinnedMessage: (String) -> String = { fullName -> "$fullName のピン留めを解除しました" }
    override val repoPinAction = "リポジトリをピン留め"
    override val repoUnpinAction = "ピン留めを解除"
    override val repoForkedFrom: (String) -> String = { parent -> "$parent からフォーク" }
    override val repoForkLabel = "フォーク"
    override val repoStarredMessage: (String) -> String = { fullName -> "$fullName にスターを付けました" }
    override val repoUnstarredMessage: (String) -> String = { fullName -> "$fullName のスターを外しました" }
    override val repoWatching = "ウォッチ中"
    override val repoWatch = "ウォッチ"
    override val repoWatchingMessage: (String) -> String = { fullName -> "$fullName をウォッチしました" }
    override val repoUnwatchedMessage: (String) -> String = { fullName -> "$fullName のウォッチを解除しました" }
    override val repoWatchUpdateFailed = "ウォッチ状態の更新に失敗しました"
    override val repoForking = "フォーク中..."
    override val repoForkedMessage: (String) -> String = { fullName -> "$fullName へフォークしました" }
    override val repoForkFailed = "フォークに失敗しました"
    override val repoNetwork = "ネットワーク"
    override val repoNetworkTitle: (String) -> String = { name -> "$name のネットワーク" }
    override val repoCompare = "比較"
    override val repoCompareUpstream = "上流と比較"
    override val repoCommitsButton = "コミット"
    override val repoEditFile = "ファイルを編集"
    override val repoDeleteFile = "ファイルを削除"
    override val repoBinaryFile: (Long) -> String = { size -> "バイナリファイル（$size バイト）" }
    override val repoBranchOrTag = "ブランチまたはタグ"
    override val repoSwitchBranchOrTag = "ブランチ・タグを切り替え"
    override val repoCheckingDivergence: (String) -> String = { label -> "$label との差分を確認中..." }
    override val repoEvenWithUpstream: (String, String) -> String = { label, ref -> "$label と同一です（$ref にフォーク独自のコミットはありません）" }
    override val repoZeroAheadBehind: (Int, String) -> String = { behind, label -> "先行 0 件 · $label より $behind 件遅れ（共通の祖先あり）" }
    override val repoAheadBehind: (Int, Int, String) -> String = { ahead, behind, label -> "$label より $ahead 件先行 · $behind 件遅れ" }
    override val repoAllCommits = "すべてのコミット"
    override val repoForkOnlyCommits: (Int) -> String = { ahead -> "フォーク独自 ($ahead)" }
    override val repoLoadingCommits = "コミットを読み込み中..."
    override val repoNoCommitsFound: (String) -> String = { ref -> "$ref にコミットが見つかりません" }
    override val repoForkSortStarred = "スター順"
    override val repoForkSortNewest = "新しい順"
    override val repoNoStargazers = "スターしたユーザーはまだいません。"
    override val repoNoForks = "フォークはまだありません。"
    override val repoNoContributors = "コントリビューター情報がありません。"
    override val repoPeopleTabStars = "スター"
    override val repoPeopleTabForks = "フォーク"
    override val repoPeopleTabContributors = "コントリビューター"
    override val repoUpdatedDate: (String) -> String = { date -> "更新: $date" }
    override val repoNewIssueTitle = "新規 Issue"
    override val repoNewPrTitle = "新規プルリクエスト"
    override val repoCreatePrButton = "PR を作成"
    override val repoIssueTitleLabel = "タイトル"
    override val repoPrTitleLabel = "PR タイトル"
    override val repoDraftPrToggle = "下書き PR として作成"
    override val repoDescriptionMarkdown = "説明 (Markdown)"
    override val repoCreatedIssueMessage: (Int) -> String = { num -> "Issue #$num を作成しました" }
    override val repoCreatedPrMessage: (Int) -> String = { num -> "PR #$num を作成しました" }
    override val repoCreateIssueFailed = "Issue の作成に失敗しました"
    override val repoHeadLabel = "Head"
    override val repoBaseLabel = "Base"
    override val repoLoadingIssues = "Issue を読み込み中..."
    override val repoLoadingPulls = "プルリクエストを読み込み中..."
    override val repoRefreshIssues = "Issue を更新"
    override val repoRefreshPulls = "プルリクエストを更新"
    override val failedLoadDiscussions = "ディスカッションの読み込みに失敗しました"
    override val failedLoadDiscussion = "ディスカッション詳細の読み込みに失敗しました"
    override val failedLoadSecurity = "セキュリティ情報の読み込みに失敗しました"
    override val failedLoadInsights = "リポジトリ統計の読み込みに失敗しました"
    override val failedLoadReleases = "リリースの読み込みに失敗しました"
    override val failedLoadActions = "Actions 実行履歴の読み込みに失敗しました"
    override val failedLoadJobs = "ジョブの読み込みに失敗しました"
    override val failedLoadCommits = "コミットの読み込みに失敗しました"
    override val failedLoadCommitDiff = "コミット差分の読み込みに失敗しました"
    override val compareFailed = "比較に失敗しました"
    override val failedLoadStargazers = "スター一覧の読み込みに失敗しました"
    override val failedLoadForks = "フォーク一覧の読み込みに失敗しました"
    override val failedLoadContributors = "コントリビューター一覧の読み込みに失敗しました"
    override val failedLoadDirectory = "ディレクトリの読み込みに失敗しました"
    override val failedLoadFile = "ファイルの読み込みに失敗しました"
    override val failedLoadIssues = "Issue の読み込みに失敗しました"
    override val failedLoadPulls = "プルリクエストの読み込みに失敗しました"
    override val failedCreateRelease = "リリースの作成に失敗しました"
    override val repoMetadataNotReady = "リポジトリのメタデータをまだ取得できていません"
    override val failedCreateDiscussion = "ディスカッションの作成に失敗しました"
    override val failedCreatePr = "プルリクエストの作成に失敗しました"
    override val failedPostComment = "コメントの投稿に失敗しました"

    override val commitVerifiedBadge = "検証済み"
    override val commitVerifiedDesc = "検証済みのコミット"
    override val commitFilesChangedSummary: (Int, Int, Int) -> String = { add, del, files -> "+$add / -$del（${files} ファイル）" }
    override val commitLoading: (String) -> String = { shortSha -> "コミット $shortSha を読み込み中..." }
    override val commitDetailsUnavailable = "コミット詳細を取得できません。"
    override val commitParentLabel: (String) -> String = { shortSha -> "親コミット $shortSha" }
    override val compareLoading: (String, String) -> String = { base, head -> "$base...$head を比較中..." }
    override val compareSelectRefs = "比較する 2 つのブランチまたはタグを選択してください。"
    override val compareAheadBehind: (Int, Int) -> String = { ahead, behind -> "先行 $ahead · 遅れ $behind" }
    override val compareSummaryCounts: (Int, Int) -> String = { commits, files -> "コミット ${commits} 件 · 変更ファイル ${files} 件" }
    override val compareCommitsHeader: (Int) -> String = { count -> "コミット ($count)" }
    override val compareChangedFilesHeader: (Int) -> String = { count -> "変更ファイル ($count)" }
    override val actionsAllRuns = "すべて"
    override val actionsRunButton = "実行"
    override val actionsRefreshRuns = "実行履歴を更新"
    override val actionsLoadingRuns = "ワークフロー実行履歴を読み込み中..."
    override val actionsNoRuns = "ワークフローの実行履歴はまだありません"
    override val actionsRunsBack = "実行一覧"
    override val actionsRefreshJobs = "ジョブを更新"
    override val actionsLoadingJobs = "ジョブを読み込み中..."
    override val actionsCancelRun = "実行をキャンセル"
    override val actionsRerunAll = "すべて再実行"
    override val actionsRerunFailed = "失敗ジョブを再実行"
    override val actionsLogsButton = "ログ"
    override val actionsJobsBack = "ジョブ一覧"
    override val actionsLoadingLogs = "ログを読み込み中..."
    override val actionsNoLogs = "このジョブのログはありません。"
    override val actionsRunWorkflowTitle = "ワークフローを実行"
    override val actionsSelectWorkflow = "ワークフローを選択"
    override val actionsBranchOrTagLabel: (String) -> String = { defaultBranch -> "ブランチまたはタグ（例: $defaultBranch）" }
    override val actionsInputsLabel = "入力パラメータ（任意、1 行につき key=value）"
    override val actionsStarting = "開始中..."
    override val actionsDispatchedMessage: (String) -> String = { ref -> "$ref でワークフローを実行開始しました" }
    override val actionsDispatchFailed = "ワークフローの実行開始に失敗しました"
    override val actionsRerunFailedError = "再実行に失敗しました"
    override val actionsCancelFailedError = "キャンセルに失敗しました"
    override val actionsLogsUnavailable = "ログを取得できません"
    override val fileUntitledName = "ファイル"
    override val fileCreateTitle = "ファイルを作成"
    override val fileEditTitle = "ファイルを編集"
    override val fileCommitting = "コミット中..."
    override val fileCommitButton = "コミット"
    override val fileContentsLabel = "ファイル内容"
    override val filePathLabel = "ファイルパス（例: docs/notes.md）"
    override val fileCommitMessageLabel = "コミットメッセージ"
    override val fileBranchLabel = "ブランチ"
    override val fileDefaultCreateCommitMsg: (String) -> String = { name -> "Create $name" }
    override val fileDefaultUpdateCommitMsg: (String) -> String = { name -> "Update $name" }
    override val fileMetadataSummary: (String, String, String) -> String = { path, branch, msg -> "$path · ブランチ: $branch · $msg" }
    override val fileCommittedMessage: (String, String) -> String = { path, shortSha ->
        if (shortSha.isNotBlank()) "$path をコミットしました（$shortSha）" else "$path をコミットしました"
    }
    override val fileCommitFailed = "ファイルのコミットに失敗しました"
    override val fileDeleteTitle = "ファイルを削除"
    override val fileDeleteConfirm: (String, String) -> String = { path, branch ->
        "ブランチ $branch から $path を削除しますか？ファイルを削除する新しいコミットが作成されます。"
    }
    override val fileDeletedMessage: (String, String) -> String = { name, shortSha ->
        if (shortSha.isNotBlank()) "$name を削除しました（$shortSha）" else "$name を削除しました"
    }
    override val fileDeleteFailed = "ファイルの削除に失敗しました"

    override val insightsLoading = "リポジトリ統計を読み込み中..."
    override val insightsHeader = "アクティビティと言語"
    override val insightsComputingNotice = "GitHub がコミット・コントリビューター統計を集計中です。しばらくしてから更新してください。"
    override val insightsLast4wCommits = "直近 4 週間の\nコミット"
    override val insights52wCommits = "直近 52 週間の\nコミット"
    override val insightsLanguages = "使用言語"
    override val insightsNoLanguages = "このリポジトリの言語統計はありません。"
    override val insightsWeeklyPulse = "週間コミット推移（16 週間）"
    override val insightsComputingPulse = "コミット頻度を集計中です。"
    override val insightsNoWeeklyPulse = "最近の週間コミット履歴はありません。"
    override val insightsPulseSummary: (Int, Int) -> String = { peak, total -> "最大週: $peak コミット • 16 週間合計: $total コミット" }
    override val insightsTopContributors = "主なコントリビューター"
    override val insightsNoContributors = "コントリビューター統計はありません。"
    override val insightsCommitCount: (Int) -> String = { count -> "$count コミット" }

    override val releasesCount: (Int) -> String = { count -> "${count} 件のリリース" }
    override val releasesHeader = "リリース"
    override val releasesNew = "新規リリース"
    override val releasesLoading = "リリースを読み込み中..."
    override val releasesNoneTitle = "公開されたリリースはまだありません"
    override val releasesNoneWithTags: (Int) -> String = { count -> "${count} 個のタグがあります。タグからリリースを作成・公開できます。" }
    override val releasesNoneEmpty = "バイナリやリリースノートを配布するにはリリースを作成してください。"
    override val releaseLatest = "最新"
    override val releasePrerelease = "プレリリース"
    override val releaseShowNotes = "リリースノートを表示"
    override val releaseHideNotes = "リリースノートを隠す"
    override val releaseAssetsCount: (Int) -> String = { count -> "アセット ($count)" }
    override val releaseSourceZip = "ソースコード (zip)"
    override val releaseSourceTarGz = "ソースコード (tar.gz)"
    override val releaseDownloadsCount: (String) -> String = { count -> "$count 回ダウンロード" }
    override val releaseDownloadArchiveDesc = "アーカイブをダウンロード"
    override val releaseCreateTitle = "リリースを作成"
    override val releaseTargetBranchSubtitle: (String) -> String = { branch -> "対象: $branch" }
    override val releasePublishing = "公開中..."
    override val releaseSaveDraft = "下書き保存"
    override val releasePublish = "公開"
    override val releaseNotesLabel = "リリースノート (Markdown)"
    override val releaseTagLabel = "タグ名（例: v1.2.0）"
    override val releaseTitleLabel = "リリースタイトル（任意）"
    override val releaseTargetLabel = "対象ブランチまたは SHA"
    override val releaseAutoNotes = "リリースノートを自動生成する"
    override val releaseDraftToggle = "下書きとして保存"

    override val discussionsCount: (Int) -> String = { count -> "${count} 件のディスカッション" }
    override val discussionsHeader = "ディスカッション"
    override val discussionsLoading = "ディスカッションを読み込み中..."
    override val discussionsDisabledOrEmpty = "ディスカッションが無効か、まだありません"
    override val discussionsEmpty = "ディスカッションはまだありません"
    override val discussionsHint = "Q&A やアイデア、お知らせスレッドを作成できます。"
    override val discussionAnswered = "解決済み"
    override val discussionAnswer = "ベストアンサー"
    override val discussionRepliesCount: (Int) -> String = { count -> "${count} 件の返信" }
    override val discussionReplyAction = "返信"
    override val discussionReplyingTo: (String) -> String = { login -> "@$login に返信中" }
    override val discussionWriteReply = "返信を入力..."
    override val discussionAddComment = "コメントを追加..."
    override val discussionUpvoteDesc = "賛成票"
    override val discussionAuthorInCategory: (String, String) -> String = { author, cat -> "@$author · カテゴリ: $cat" }
    override val discussionNewTitle = "新規ディスカッション"
    override val discussionCategoryLabel = "カテゴリ"
    override val discussionBodyLabel = "本文 (Markdown)"
    override val securityHeader = "セキュリティと Dependabot"
    override val securityChecking = "セキュリティアラートを確認中..."
    override val securityDependabotCount: (Int) -> String = { count -> "Dependabot アラート ($count)" }
    override val securityAdvisoriesCount: (Int) -> String = { count -> "セキュリティアドバイザリ ($count)" }
    override val securityAllClearTitle = "未対応の Dependabot アラートやアドバイザリはありません"
    override val securityAllClearDesc = "このリポジトリの依存関係とアドバイザリはクリーンな状態です。"
    override val securityAffected: (String) -> String = { range -> "影響範囲: $range" }
    override val securityFix: (String) -> String = { version -> "修正バージョン: $version" }

    override val issueDetailTabChat = "会話"
    override val issueDetailTabChecks = "チェック"
    override val issueDetailTabFiles = "変更ファイル"
    override val issueLoadingNumber: (Int) -> String = { num -> "#$num を読み込み中..." }
    override val issueLoadingChecks = "CI チェック結果を読み込み中..."
    override val issueNoChecks = "CI チェックの実行結果はありません"
    override val issueRefreshChecks = "チェックを更新"
    override val issueReviewAction = "レビュー"
    override val issueReopenAction = "再開"
    override val issueCloseAction = "クローズ"
    override val issueMergeAction = "マージ"
    override val issueMergeConfirmTitle: (Int) -> String = { num -> "#$num をマージしますか？" }
    override val issueMergeFailed = "マージに失敗しました"
    override val issueReopenConfirmTitle: (Int) -> String = { num -> "#$num を再開しますか？" }
    override val issueCloseConfirmTitle: (Int) -> String = { num -> "#$num をクローズしますか？" }
    override val issueStateUpdateFailed = "状態の更新に失敗しました"
    override val issueCommentTitle: (Int) -> String = { num -> "#$num にコメント" }
    override val issueCommentPost = "投稿"
    override val issueCommentLabel = "コメント (Markdown)"
    override val issueCommentPosted = "コメントを投稿しました"
    override val issueCommentPostFailed = "コメントの投稿に失敗しました"
    override val issueSubmitReviewTitle = "PR レビューを送信"
    override val issueSubmitAction = "送信"
    override val issueReviewSummaryLabel = "レビュー総評（承認時は任意）"
    override val issueReviewSubmitted: (String) -> String = { label -> "レビューを送信しました（$label）" }
    override val issueReviewFailed = "レビューの送信に失敗しました"
    override val issueDecisionLabel = "判定"
    override val labelsTitle = "ラベル"
    override val selectLabelsTitle = "ラベルを選択"
    override val loadingLabels = "ラベルを読み込み中..."
    override val issueManageLabelsTitle = "ラベルを管理"
    override val issueNoLabels = "リポジトリにラベルがありません。"
    override val issueNewLabel = "新しいラベル"
    override val createLabelAction = "ラベルを作成"
    override val createdLabelMessage: (String) -> String = { name -> "ラベル $name を作成しました" }
    override val failedCreateLabel = "ラベルの作成に失敗しました"
    override val failedLoadLabels = "ラベルの読み込みに失敗しました"
    override val issueLabelsUpdated = "ラベルを更新しました"
    override val failedUpdateLabels = "ラベルの更新に失敗しました"
    override val issueManageAssigneesTitle = "アサインを管理"
    override val issueNoAssignees = "アサインできるユーザーがいません。"
    override val failedLoadAssignees = "アサイン候補の読み込みに失敗しました"
    override val issueAssigneesUpdated = "アサインを更新しました"
    override val failedUpdateAssignees = "アサインの更新に失敗しました"
    override val issueSelectMilestoneTitle = "マイルストーンを選択"
    override val issueNoMilestone = "マイルストーンなし"
    override val failedLoadMilestones = "マイルストーンの読み込みに失敗しました"
    override val issueMilestoneUpdated = "マイルストーンを更新しました"
    override val issueMilestoneCleared = "マイルストーンを解除しました"
    override val failedUpdateMilestone = "マイルストーンの更新に失敗しました"
    override val milestoneDue: (String) -> String = { due -> "期限: $due" }
    override val failedLoadCiChecks = "CI チェックの読み込みに失敗しました"
    override val failedUpdateDraftState = "下書き状態の更新に失敗しました"
    override val reactionFailed = "リアクションに失敗しました"
    override val addReactionDesc = "リアクションを追加"
    override val reactedDesc = "リアクション済み"
    override val noFileDiffs = "このプルリクエストのファイル差分はありません。"
    override val reviewCommentLineTitle: (String, Int) -> String = { file, line -> "レビューコメント: $file:$line" }
    override val fileCommentTitle: (String) -> String = { file -> "ファイルコメント: $file" }
    override val addedInlineDiffComment = "差分にインラインコメントを追加しました"
    override val failedPostDiffComment = "差分コメントの投稿に失敗しました"
    override val issueEditCommentTitle = "コメントを編集"
    override val commentOnIssueSubtitle: (String, Int) -> String = { login, num -> "@$login（#$num）" }
    override val issueCommentUpdated = "コメントを更新しました"
    override val failedUpdateComment = "コメントの更新に失敗しました"
    override val issueDeleteCommentTitle = "コメントを削除しますか？"
    override val issueDeleteCommentConfirm: (String) -> String = { login -> "@$login のこのコメントを削除してもよろしいですか？" }
    override val issueCommentDeleted = "コメントを削除しました"
    override val failedDeleteComment = "コメントの削除に失敗しました"
    override val editCommentDesc = "コメントを編集"
    override val deleteCommentDesc = "コメントを削除"
    override val issueRequestReviewersTitle = "レビューを依頼"
    override val issueUsernameLabel = "GitHub ユーザー名"
    override val issueRequestAction = "依頼"
    override val addReviewerDesc = "レビュアーを追加"
    override val requestedReviewersMessage: (String) -> String = { reviewers -> "レビューを依頼しました: $reviewers" }
    override val failedRequestReviewers = "レビューの依頼に失敗しました"
    override val reviewersHeader = "レビュアー:"
    override val systemActorFallback = "システム"
    override val issueLabelsCount: (Int) -> String = { count -> "ラベル ($count)" }
    override val issueAssigneesCount: (Int) -> String = { count -> "アサイン ($count)" }
    override val issueMilestoneFallback = "マイルストーン"
    override val issueReviewersCount: (Int) -> String = { count -> "レビュアー ($count)" }
    override val issueReadyForReview = "レビュー可能にする"
    override val issueConvertToDraft = "下書きに変換"
    override val issueReactAction = "リアクション"
    override val mergeSquashLabel = "スカッシュしてマージ"
    override val mergeCommitLabel = "マージコミットを作成"
    override val mergeRebaseLabel = "リベースしてマージ"
    override val reviewApproveLabel = "承認"
    override val reviewRequestChangesLabel = "変更を要求"
    override val reviewCommentLabel = "コメント"

    override val collapseDetails = "詳細を折りたたむ"
    override val expandDetails = "詳細を展開"
    override val nothingToPreview = "プレビューする内容がありません。"
    override val monospaceCodeHint = "等幅コード"
    override val markdownSupportedHint = "Markdown 対応"
    override val openFullScreenDiff = "全画面で差分を開く"
    override val closeDiffViewer = "差分ビューアを閉じる"
    override val commentOnFile = "ファイルにコメント"
    override val diffScrollMode = "横スクロール"
    override val diffWrapMode = "折り返し"
    override val diffPrevFile = "前のファイル"
    override val diffNextFile = "次のファイル"
    override val diffTapLineHint = "任意の行をタップしてレビューコメントを追加できます。"
    override val diffBinaryOrLarge = "バイナリファイルまたは差分が大きすぎるためインライン表示できません。"
    override val diffEmptyPatch = "変更はありません。"

    override val notifUpdatedAt: (String) -> String = { time -> "更新: $time" }
    override val notifOpenOnGitHub = "GitHub で開く"
    override val notifLoadingDetails: (String) -> String = { kind -> "詳細を読み込み中...（$kind）" }
    override val notifCheckRunsTitle = "チェック実行"
    override val notifWorkflowRunsTitle = "最近のワークフロー実行"
    override val notifRerunningFailed = "失敗したジョブを再実行しています"
    override val notifRerunningWorkflow = "ワークフローを再実行しています"
    override val notifCancelRequested = "キャンセルをリクエストしました"
    override val subjectIssue = "Issue"
    override val subjectPullRequest = "プルリクエスト"
    override val subjectCommit = "コミット"
    override val subjectRelease = "リリース"
    override val subjectDiscussion = "ディスカッション"
    override val subjectCheckSuite = "CI / チェック"
    override val subjectNotification = "通知"
    override val osNotifUrgentChannelName = "メンションとレビュー依頼"
    override val osNotifUrgentChannelDesc = "優先度の高い GitHub のメンション、レビュー依頼、アサイン通知。"
    override val osNotifActivityChannelName = "リポジトリのアクティビティ"
    override val osNotifActivityChannelDesc = "GitHub リポジトリの更新や購読中スレッドの一般通知。"
    override val osNotifMarkReadAction = "既読にする"
    override val osNotifBatchTitle: (Int) -> String = { count -> "新着の GitHub 通知が ${count} 件あります" }
    override val osNotifTestReadyTitle: (String) -> String = { appName -> "$appName の通知が有効です" }
    override val osNotifTestReadyBody = "新しいメンションやレビュー依頼が、既読・完了アクション付きでここに表示されます。"
    override val osNotifDesktopTestTitle: (String) -> String = { appName -> "$appName テスト通知" }
    override val osNotifDesktopTestBody = "デスクトップ通知は正常に動作しています！"

    override val fontSectionTitle = "タイポグラフィとフォント"
    override val primaryFontLabel = "メインフォント"
    override val primaryFontDesc = "英数字や記号の表示に優先して使われます"
    override val fallbackFontLabel = "代替フォント"
    override val fallbackFontDesc = "日本語・CJK やメインフォントに無い文字に使われます"
    override val fallbackFontRequiresAndroid10 = "Android 10 以降が必要です"
    override val resetFontsToSystemDefault = "システム標準に戻す"
    override val fontPreviewTitle = "プレビュー"
    override val fontPreviewPrimarySample = "The quick brown fox jumps over the lazy dog 0123456789"
    override val fontPreviewSecondarySample = "素早い茶色の狐が怠け者の犬を飛び越える — GitHub Pull Request #42"
    override val selectPrimaryFontTitle = "メインフォントを選択"
    override val selectFallbackFontTitle = "代替フォントを選択"
    override val importedFontMessage: (String) -> String = { name -> "フォントを読み込みました: $name" }
    override val failedImportFont = "フォントファイルの読み込みに失敗しました"
    override val importFontFileButton = "フォントファイルを読み込む (.ttf / .otf / .ttc)"
    override val searchSystemFontsPlaceholder = "フォントを検索..."
    override val systemDefaultLabel = "システム標準"
    override val fontGenericSans = "サンセリフ"
    override val fontGenericSerif = "セリフ"
    override val fontGenericMonospace = "等幅"
    override val importedFontFilesHeader = "読み込み済みフォントファイル"
    override val genericFontFamiliesHeader = "汎用フォントファミリー"
    override val loadingSystemFonts = "システムフォントを読み込み中..."
    override val systemFontsHeader = "システムフォント"
    override val noMatchingSystemFonts = "一致するシステムフォントがありません"
    override val deleteImportedFontTitle = "読み込んだフォントを削除しますか？"
    override val deleteSelectedFontInUseDesc = "このフォントは現在選択されています。削除するとシステム標準に戻ります。"
    override val failedDeleteFontFile = "フォントファイルの削除に失敗しました"
    override val deleteImportedFontDesc = "読み込んだフォントを削除"

    override val issueClosedMessage: (Int) -> String = { num -> "#$num をクローズしました" }
    override val issueReopenedMessage: (Int) -> String = { num -> "#$num を再開しました" }
    override val prConvertedToDraftMessage = "下書きに変換しました"
    override val prMarkedReadyMessage = "レビュー可能にしました"
    override val diffCommentBodyLabel = "コメント (Markdown)"
    override val editCommentBodyLabel = "コメント本文 (Markdown)"
    override val discussionCategoryAll = "すべて"
    override val failedLoadWorkflowRuns = "ワークフロー実行の読み込みに失敗しました"
    override val failedLoadCommit = "コミットの読み込みに失敗しました"
    override val failedLoadLogs = "ログの読み込みに失敗しました"
    override val failedRerunWorkflow = "ワークフローの再実行に失敗しました"
    override val failedCancelRun = "実行のキャンセルに失敗しました"
    override val markAllReadFailed = "すべて既読にできませんでした"
    override val refreshDiscussionsDesc = "ディスカッションを更新"
    override val refreshDiscussionDesc = "ディスカッションを再読み込み"
    override val backToDiscussionsDesc = "ディスカッション一覧に戻る"
    override val discussionLoading = "ディスカッションの詳細を読み込み中..."
    override val cancelReplyDesc = "返信をキャンセル"
    override val sendCommentDesc = "コメントを送信"
    override val refreshSecurityDesc = "セキュリティアラートを更新"
    override val refreshReleasesDesc = "リリースを更新"
    override val openReleaseInBrowserDesc = "リリースをブラウザで開く"
    override val downloadAssetDesc = "アセットをダウンロード"
    override val repoNewFileButton = "ファイル"
    override val refreshCommitsDesc = "コミットを更新"
    override val refreshCompareDesc = "比較を更新"
    override val swapBaseHeadDesc = "base と head を入れ替え"
    override val copyGistLinkDesc = "Gist のリンクをコピー"
    override val gistScopeErrorHint = "トークンに 'gist' スコープがない可能性があります。設定からサインアウトして、再度サインインしてください。"
    override val followScopeErrorHint = "トークンに 'user:follow' スコープがない可能性があります。設定からサインアウトして、再度サインインしてください。"
    override val authMethodDeviceFlow = "OAuth デバイス認証"
    override val authMethodPat = "パーソナルアクセストークン"
    override val fontFileChooserTitle = "フォントファイルを選択 (.ttf / .otf / .ttc)"
    override val fontFileFilterLabel = "フォントファイル (*.ttf, *.otf, *.ttc)"
    override val windowTitle: (String) -> String = { appName -> "$appName — GitHub クライアント" }

    /**
     * The network layer builds English messages, so Japanese leads with its own description of what failed
     * (plus the HTTP status code when there is one) and keeps the original English text as a detail line.
     */
    override fun errorMessage(error: Throwable, fallback: String, notFoundHint: String?): String {
        val code = httpStatusOf(error)
        val head = buildString {
            append(fallback)
            if (code != null) append(httpStatusSuffix(code))
            when {
                code == 401 -> append("。$reSignInHint")
                code == 404 && notFoundHint != null -> append("。").append(notFoundHint)
            }
        }
        return withDetail(head, error.message, fallback)
    }

    /** "（HTTP 401）" with a no-break space so the status never wraps away from "HTTP". */
    private fun httpStatusSuffix(code: Int): String = "（HTTP\u00A0$code）"

    private const val reSignInHint = "サインインし直してください。"

    /** Appends GitHub's / the client's original English message on its own line, unless it adds nothing. */
    private fun withDetail(head: String, detail: String?, vararg redundant: String): String {
        val text = detail?.trim()?.takeIf { it.isNotEmpty() && it !in redundant } ?: return head
        return "$head\n詳細: $text"
    }

    /** The real HTTP status of [error]; null for network failures and for statuses the client made up (GraphQL). */
    private fun httpStatusOf(error: Throwable): Int? =
        (error as? HttpStatusError)?.takeUnless { it.isSyntheticStatus }?.httpStatusCode

    override fun jobLogsErrorText(error: Throwable): String {
        val code = httpStatusOf(error)
        val head = if (code == null) {
            "$actionsLogsUnavailable。ネットワーク接続を確認してください。"
        } else {
            "$actionsLogsUnavailable${httpStatusSuffix(code)}。repo スコープと、ログの保持期限が切れていないかを確認してください。"
        }
        return withDetail(head, error.message)
    }

    override fun dependabotAlertsNoticeText(overview: RepoSecurityOverview): String? {
        if (overview.alertsAccessNotice == null) return null
        return when (val code = overview.alertsAccessStatusCode) {
            403 -> "Dependabot アラートを表示するには、リポジトリの管理者権限または security_events 権限が必要です。"
            404 -> "このリポジトリでは Dependabot の脆弱性アラートが有効になっていません。"
            null -> withDetail("Dependabot アラートを取得できませんでした。", overview.alertsAccessNotice)
            else -> withDetail("Dependabot アラートを取得できませんでした${httpStatusSuffix(code)}。", overview.alertsAccessNotice)
        }
    }

    override fun noIssuesOrPrsTitle(state: IssueStateFilter): String = when (state) {
        IssueStateFilter.OPEN -> "オープンな Issue / PR はありません"
        IssueStateFilter.CLOSED -> "クローズされた Issue / PR はありません"
        IssueStateFilter.ALL -> "Issue / PR はありません"
    }

    override fun noIssuesOrPrsBody(state: IssueStateFilter, preset: IssueFilterPreset): String {
        val items = when (state) {
            IssueStateFilter.OPEN -> "オープンな項目"
            IssueStateFilter.CLOSED -> "クローズされた項目"
            IssueStateFilter.ALL -> "項目"
        }
        return "「${issueFilterPresetLabel(preset)}」に一致する${items}はありません。"
    }

    override fun repoItemsEmptyTitle(isPullRequests: Boolean, state: IssueStateFilter): String =
        "${repoItemsNoun(isPullRequests, state)}はありません"

    override fun repoItemsEmptyBody(isPullRequests: Boolean, state: IssueStateFilter): String =
        "このリポジトリには${if (state == IssueStateFilter.ALL && !isPullRequests) " " else ""}${repoItemsNoun(isPullRequests, state)}がありません。"

    private fun repoItemsNoun(isPullRequests: Boolean, state: IssueStateFilter): String {
        val kind = if (isPullRequests) "プルリクエスト" else "Issue "
        return when (state) {
            IssueStateFilter.OPEN -> if (isPullRequests) "オープンな$kind" else "オープンな $kind"
            IssueStateFilter.CLOSED -> if (isPullRequests) "クローズされた$kind" else "クローズされた $kind"
            IssueStateFilter.ALL -> kind
        }
    }

    override fun issueMergedMessage(number: Int, method: MergeMethod): String =
        "#$number をマージしました（${mergeMethodLabel(method)}）"

    override fun mergeConfirmQuestion(baseRef: String?, method: MergeMethod): String =
        "${baseRef?.let { "$it に" } ?: "ベースブランチに"}マージしますか？（${mergeMethodLabel(method)}）"

    override fun stateChangeConfirmQuestion(willOpen: Boolean, isPullRequest: Boolean): String =
        "${if (isPullRequest) "このプルリクエスト" else "この Issue "}を${if (willOpen) "再開" else "クローズ"}しますか？"

    override fun fileStatusBadge(status: FileChangeStatus): String = when (status) {
        FileChangeStatus.ADDED -> "追加"
        FileChangeStatus.MODIFIED, FileChangeStatus.CHANGED -> "変更"
        FileChangeStatus.REMOVED -> "削除"
        FileChangeStatus.RENAMED -> "名前変更"
        FileChangeStatus.COPIED -> "コピー"
    }

    override fun deviceFlowWaitingText(state: DeviceCodeState.AwaitingUser): String = when (state.waitReason) {
        DeviceFlowWaitReason.CODE_ISSUED -> "GitHub でこのコードを入力して、${AppBranding.APP_NAME} を承認してください。"
        DeviceFlowWaitReason.AUTHORIZATION_PENDING -> "ブラウザでの承認を待っています..."
        DeviceFlowWaitReason.NETWORK_RETRY -> "一時的なネットワークエラーです。再試行しています..."
        DeviceFlowWaitReason.SLOW_DOWN -> "GitHub のレート制限のため、${state.intervalSeconds} 秒ごとに確認しています..."
        null -> signInWaitingAuth
    }

    override fun deviceFlowVerifyingText(state: DeviceCodeState.Verifying): String = "サインイン処理中..."

    override fun deviceFlowErrorText(state: DeviceCodeState.Error): String = when (state.kind) {
        DeviceFlowErrorKind.MISSING_CLIENT_ID -> "OAuth クライアント ID を入力してください。"
        DeviceFlowErrorKind.CODE_REQUEST_REJECTED ->
            "GitHub がデバイスコードの発行を拒否しました${state.httpStatusCode?.let(::httpStatusSuffix).orEmpty()}。クライアント ID を確認してください。"
        DeviceFlowErrorKind.NETWORK -> withDetail("GitHub に接続できませんでした。ネットワーク接続を確認してください。", state.message)
        DeviceFlowErrorKind.EXPIRED -> "デバイスコードの有効期限が切れました。新しいコードを取得してください。"
        DeviceFlowErrorKind.DENIED -> "ブラウザで承認が拒否されました。"
        DeviceFlowErrorKind.TIMED_OUT -> "時間内に承認されませんでした。もう一度お試しください。"
        DeviceFlowErrorKind.OTHER -> withDetail(
            when (val code = state.oauthErrorCode) {
                null -> "GitHub での認証に失敗しました。もう一度お試しください。"
                "device_flow_disabled" ->
                    "この OAuth App では Device Flow が有効になっていません。GitHub の OAuth App の設定で「Enable Device Flow」をオンにしてください（エラー: $code）。"
                "incorrect_client_credentials" ->
                    "クライアント ID が正しくありません。入力したクライアント ID を確認してください（エラー: $code）。"
                else -> "GitHub での認証に失敗しました（エラー: $code）。"
            },
            state.message,
            state.oauthErrorCode.orEmpty()
        )
    }

    override fun formatEditorMetricsLabel(lines: Int, chars: Int): String =
        "${lines} 行 · ${chars} 文字"

    override fun notificationReasonLabel(reason: NotificationReason): String = when (reason) {
        NotificationReason.ASSIGN -> "アサイン"
        NotificationReason.AUTHOR -> "作成者"
        NotificationReason.COMMENT -> "コメント"
        NotificationReason.CI_ACTIVITY -> "CI 通知"
        NotificationReason.INVITATION -> "招待"
        NotificationReason.MANUAL -> "手動購読"
        NotificationReason.MENTION -> "メンション"
        NotificationReason.REVIEW_REQUESTED -> "レビュー依頼"
        NotificationReason.SECURITY_ALERT -> "セキュリティ"
        NotificationReason.STATE_CHANGE -> "状態変更"
        NotificationReason.SUBSCRIBED -> "購読中"
        NotificationReason.TEAM_MENTION -> "チームメンション"
        NotificationReason.OTHER -> "その他"
    }

    override fun formatCheckRollupState(state: String): String = when (state.uppercase()) {
        "SUCCESS" -> "成功"
        "FAILURE", "ERROR" -> "失敗"
        "PENDING" -> "保留中"
        "EXPECTED" -> "待機中"
        else -> state
    }

    override fun formatCheckConclusionLabel(label: String): String = when (label.uppercase()) {
        "SUCCESS" -> "成功"
        "FAILURE" -> "失敗"
        "TIMED_OUT" -> "タイムアウト"
        "CANCELLED" -> "キャンセル"
        "SKIPPED" -> "スキップ"
        "NEUTRAL" -> "中立"
        "ACTION_REQUIRED" -> "要対応"
        "STALE" -> "古い結果"
        "QUEUED" -> "待機中"
        "PENDING", "WAITING", "REQUESTED" -> "保留中"
        "IN_PROGRESS" -> "実行中"
        "COMPLETED" -> "完了"
        else -> label.uppercase()
    }

    override fun formatCheckStatusText(status: String): String = formatCheckConclusionLabel(status)

    override fun formatCompareStatusLabel(status: String): String = when (status.lowercase()) {
        "ahead" -> "先行"
        "behind" -> "遅れ"
        "diverged" -> "分岐"
        "identical" -> "同一"
        else -> status.uppercase()
    }

    override fun formatReviewStateLabel(state: String): String = when (state.uppercase()) {
        "APPROVED" -> "承認済み"
        "CHANGES_REQUESTED" -> "変更要求"
        "COMMENTED" -> "コメント"
        "REVIEW_REQUIRED" -> "レビュー待ち"
        "DISMISSED" -> "却下"
        "PENDING" -> "保留中"
        else -> state.replace('_', ' ')
    }

    override fun formatAuthorAssociationLabel(association: String): String = when (association.uppercase()) {
        "OWNER" -> "オーナー"
        "MEMBER" -> "メンバー"
        "COLLABORATOR" -> "コラボレーター"
        "CONTRIBUTOR" -> "コントリビューター"
        "FIRST_TIME_CONTRIBUTOR" -> "初めてのコントリビューター"
        "FIRST_TIMER" -> "GitHub 初参加"
        "MANNEQUIN" -> "マネキン"
        else -> association
    }

    override fun formatReactionLabel(contentKey: String): String = when (contentKey.uppercase()) {
        "THUMBS_UP" -> "いいね"
        "THUMBS_DOWN" -> "よくないね"
        "LAUGH" -> "笑い"
        "HOORAY" -> "お祝い"
        "CONFUSED" -> "困惑"
        "HEART" -> "ハート"
        "ROCKET" -> "ロケット"
        "EYES" -> "注目"
        else -> contentKey.lowercase().replace('_', ' ')
    }

    override fun formatSeverityLabel(severity: String): String = when (severity.lowercase()) {
        "critical" -> "緊急"
        "high" -> "高"
        "medium", "moderate" -> "中"
        "low" -> "低"
        else -> severity.uppercase()
    }

    // Timeline and activity lines: the network layer builds English phrases (see GitHubGraphQlService
    // timeline parsing and GitHubRestService.parseActivityEvent). They are parsed back here so that
    // Japanese can put the actor first ("@alice が …"). ActivityFeedJapaneseContractTest in composeApp
    // pins every producer format to its Japanese output.

    override fun formatTimelineLine(actorLogin: String?, actionLabel: String): String {
        val predicate = when {
            actionLabel == "closed this" -> "クローズしました"
            actionLabel == "reopened this" -> "再開しました"
            // GitHubGraphQlService writes "merged into base" when the merge ref name is unknown.
            actionLabel == "merged into base" -> "ベースブランチにマージしました"
            actionLabel.startsWith("merged into ") -> "${actionLabel.removePrefix("merged into ")} にマージしました"
            else -> null
        }
        if (actorLogin == null) {
            // "システムがクローズしました" / "システムが main にマージしました": no "@" and no space before "が".
            return if (predicate != null) "$systemActorFallback が$predicate".withSpaceAfterGa().replaceFirst(" が", "が")
            else "$systemActorFallback $actionLabel"
        }
        return if (predicate != null) "@$actorLogin が$predicate".withSpaceAfterGa() else "@$actorLogin $actionLabel"
    }

    override fun formatActivityEventLine(event: ActivityEvent): String {
        val actor = "@${event.actor.login}"
        val predicate = activityPredicate(event)
        return if (predicate != null) {
            "$actor が$predicate".withSpaceAfterGa()
        } else {
            val detail = event.summaryText.takeIf { it.isNotBlank() && it != "activity" }
            if (detail != null) "$actor のアクティビティ（$detail）" else "$actor のアクティビティ"
        }
    }

    /** "@a が" + predicate: keep a space only before Latin text, digits, '#' or '@' (e.g. "が main に", "がリポジトリを"). */
    private fun String.withSpaceAfterGa(): String {
        val idx = indexOf(" が") + 2
        if (idx < 2 || idx >= length) return this
        val next = this[idx]
        val needsSpace = next.isLetterOrDigit() && next.code < 0x3000 || next == '#' || next == '@'
        return if (needsSpace) substring(0, idx) + " " + substring(idx) else this
    }

    /** Japanese predicate (without the actor) for a recognized summary, or null when it is not recognized. */
    private fun activityPredicate(event: ActivityEvent): String? {
        val summary = event.summaryText
        val num = event.targetIssueNumber
        return when (event.kind) {
            ActivityEventKind.PUSH -> {
                val count = PUSH_COUNT_REGEX.find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val branch = event.branchName ?: summary.substringAfterLast(" to ", "").ifBlank { return null }
                "$branch に $count 件のコミットをプッシュしました"
            }
            ActivityEventKind.PULL_REQUEST -> {
                if (!summary.contains(" pull request") && !summary.endsWith("pull request")) return null
                val target = withNumber("プルリクエスト", num)
                when (summary.substringBefore(" pull request")) {
                    "opened" -> "${target}を作成しました"
                    "merged" -> "${target}をマージしました"
                    "closed" -> "${target}をクローズしました"
                    "reopened" -> "${target}を再開しました"
                    "edited" -> "${target}を編集しました"
                    "ready_for_review" -> "${target}をレビュー可能にしました"
                    "converted_to_draft" -> "${target}を下書きに変換しました"
                    "review_requested" -> "${target}のレビューを依頼しました"
                    "assigned" -> "${target}のアサインを変更しました"
                    else -> "${target}を更新しました"
                }
            }
            ActivityEventKind.ISSUES -> {
                if (!summary.contains(" issue") && !summary.endsWith("issue")) return null
                val target = withNumber("Issue", num)
                when (summary.substringBefore(" issue")) {
                    "opened" -> "${target}を作成しました"
                    "closed" -> "${target}をクローズしました"
                    "reopened" -> "${target}を再開しました"
                    "edited" -> "${target}を編集しました"
                    "assigned" -> "${target}のアサインを変更しました"
                    else -> "${target}を更新しました"
                }
            }
            ActivityEventKind.ISSUE_COMMENT -> when {
                summary == "commented on thread" -> "スレッドにコメントしました"
                summary.startsWith("commented on") -> "${num?.let { "#$it " } ?: "スレッド"}にコメントしました"
                else -> null
            }
            ActivityEventKind.PULL_REQUEST_REVIEW -> {
                val target = withNumber("プルリクエスト", num)
                when {
                    summary.startsWith("approved") -> "${target}を承認しました"
                    summary.startsWith("requested changes") -> "${target}に変更を要求しました"
                    summary.startsWith("reviewed") -> "${target}をレビューしました"
                    else -> null
                }
            }
            ActivityEventKind.WATCH -> "リポジトリにスターを付けました"
            ActivityEventKind.FORK -> "リポジトリをフォークしました"
            ActivityEventKind.CREATE -> refPredicate(summary, "created ", "作成しました")
            ActivityEventKind.DELETE -> refPredicate(summary, "deleted ", "削除しました")
            ActivityEventKind.RELEASE -> {
                if (!summary.contains("release")) return null
                val target = withNumber("リリース", null, event.releaseTag)
                when (summary.substringBefore(" release")) {
                    "published", "released" -> "${target}を公開しました"
                    "created" -> "${target}を作成しました"
                    "prereleased" -> "プレリリース${event.releaseTag?.takeIf { it.isNotBlank() }?.let { " $it " }.orEmpty()}を公開しました"
                    "edited" -> "${target}を編集しました"
                    "deleted" -> "${target}を削除しました"
                    "unpublished" -> "${target}の公開を取り消しました"
                    else -> "${target}を更新しました"
                }
            }
            ActivityEventKind.PUBLIC -> "リポジトリを公開しました"
            ActivityEventKind.MEMBER -> {
                val action = summary.substringBefore(' ')
                val member = summary.substringAfter(' ', "").takeIf { it.startsWith("@") }
                when (action) {
                    "added" -> member?.let { "$it をコラボレーターに追加しました" } ?: "コラボレーターを追加しました"
                    "removed" -> member?.let { "$it をコラボレーターから削除しました" } ?: "コラボレーターを削除しました"
                    "edited" -> member?.let { "$it の権限を変更しました" } ?: "コラボレーターの権限を変更しました"
                    else -> null
                }
            }
            ActivityEventKind.OTHER -> when (summary) {
                "CommitComment" -> "コミットにコメントしました"
                "Gollum" -> "Wiki を更新しました"
                else -> null
            }
        }
    }

    /** "プルリクエスト #12 " / "プルリクエスト" (a trailing space only when followed by Latin/digits). */
    private fun withNumber(noun: String, number: Int?, tag: String? = null): String {
        val suffix = number?.let { "#$it" } ?: tag?.takeIf { it.isNotBlank() }
        return when {
            suffix != null -> "$noun $suffix "
            noun.first().code < 0x3000 -> "$noun "
            else -> noun
        }
    }

    private fun refPredicate(summary: String, prefix: String, verb: String): String? {
        if (!summary.startsWith(prefix.trim())) return null
        val rest = summary.removePrefix(prefix).trim()
        val refType = rest.substringBefore(' ')
        val ref = rest.substringAfter(' ', "").trim()
        val noun = when (refType) {
            "branch" -> "ブランチ"
            "tag" -> "タグ"
            "repository" -> "リポジトリ"
            "ref", "" -> "参照"
            else -> return null
        }
        return if (ref.isNotEmpty()) "$noun $ref を$verb" else "${noun}を$verb"
    }

    private val PUSH_COUNT_REGEX = Regex("""^pushed (\d+) commit""")
}
