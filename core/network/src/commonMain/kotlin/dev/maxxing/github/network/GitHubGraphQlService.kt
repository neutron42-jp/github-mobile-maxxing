package dev.maxxing.github.network

import dev.maxxing.github.model.CheckRollupSummary
import dev.maxxing.github.model.ContributionCalendar
import dev.maxxing.github.model.ContributionDay
import dev.maxxing.github.model.ContributionWeek
import dev.maxxing.github.model.DiscussionCategory
import dev.maxxing.github.model.DiscussionComment
import dev.maxxing.github.model.DiscussionDetail
import dev.maxxing.github.model.DiscussionListData
import dev.maxxing.github.model.DiscussionReply
import dev.maxxing.github.model.DiscussionSummary
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.model.MilestoneSummary
import dev.maxxing.github.model.ReactionSummary
import dev.maxxing.github.model.RepoDetail
import dev.maxxing.github.model.RepoParentSummary
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.TimelineEvent
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.model.ViewerProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class GraphQlRequest(
    val query: String,
    val variables: Map<String, String> = emptyMap()
)

@Serializable
private data class GraphQlResponse(
    val data: JsonObject? = null,
    val errors: List<GraphQlError>? = null
)

@Serializable
private data class GraphQlError(
    val message: String
)

class GitHubGraphQlService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com"
) {
    private suspend fun execute(
        query: String,
        variables: Map<String, String> = emptyMap()
    ): JsonObject {
        val response = httpClient.post("$baseUrl/graphql") {
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query = query, variables = variables))
        }
        if (!response.status.isSuccess()) {
            throw GitHubApiException(response.status.value, "GraphQL HTTP ${response.status.value}")
        }
        val body = response.body<GraphQlResponse>()
        if (!body.errors.isNullOrEmpty() && body.data == null) {
            throw GitHubApiException(422, body.errors.joinToString("; ") { it.message }, isSyntheticStatus = true)
        }
        return body.data ?: throw GitHubApiException(500, "Empty GraphQL response", isSyntheticStatus = true)
    }

    suspend fun fetchViewerProfile(): ViewerProfile {
        val data = execute(
            """
            query {
              viewer {
                login
                name
                avatarUrl
                bio
                company
                location
                url
                followers { totalCount }
                following { totalCount }
                repositories(ownerAffiliations: OWNER) { totalCount }
              }
            }
            """.trimIndent()
        )
        val v = data.obj("viewer") ?: throw GitHubApiException(500, "Missing viewer in GraphQL response", isSyntheticStatus = true)
        return ViewerProfile(
            login = v.str("login") ?: "",
            name = v.str("name"),
            avatarUrl = v.str("avatarUrl") ?: "",
            bio = v.str("bio"),
            company = v.str("company"),
            location = v.str("location"),
            followersCount = v.obj("followers")?.int("totalCount") ?: 0,
            followingCount = v.obj("following")?.int("totalCount") ?: 0,
            publicReposCount = v.obj("repositories")?.int("totalCount") ?: 0,
            htmlUrl = v.str("url") ?: ""
        )
    }

    suspend fun fetchIssuesAndPulls(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary> {
        val qualifier = when (preset) {
            IssueFilterPreset.CREATED -> "author:$viewerLogin"
            IssueFilterPreset.ASSIGNED -> "assignee:$viewerLogin"
            IssueFilterPreset.REVIEW_REQUESTED -> "is:pr review-requested:$viewerLogin"
            IssueFilterPreset.MENTIONED -> "mentions:$viewerLogin"
        }
        val stateQualifier = when (stateFilter) {
            IssueStateFilter.OPEN -> "is:open "
            IssueStateFilter.CLOSED -> "is:closed "
            IssueStateFilter.ALL -> ""
        }
        val searchQuery = "$stateQualifier$qualifier sort:updated-desc"

        val data = execute(
            query = """
                query(${'$'}q: String!) {
                  search(query: ${'$'}q, type: ISSUE, first: 35) {
                    nodes {
                      __typename
                      ... on Issue {
                        id
                        number
                        title
                        state
                        createdAt
                        updatedAt
                        author { login avatarUrl url }
                        repository { nameWithOwner name owner { login } }
                        comments { totalCount }
                        labels(first: 6) { nodes { name color description } }
                      }
                      ... on PullRequest {
                        id
                        number
                        title
                        state
                        isDraft
                        createdAt
                        updatedAt
                        reviewDecision
                        author { login avatarUrl url }
                        repository { nameWithOwner name owner { login } }
                        comments { totalCount }
                        labels(first: 6) { nodes { name color description } }
                        commits(last: 1) {
                          nodes {
                            commit {
                              statusCheckRollup { state }
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("q" to searchQuery)
        )

        val nodes = data.obj("search")?.arr("nodes") ?: return emptyList()
        return nodes.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val typeName = obj.str("__typename") ?: return@mapNotNull null
            if (typeName != "Issue" && typeName != "PullRequest") return@mapNotNull null
            val repoObj = obj.obj("repository") ?: return@mapNotNull null
            val authorObj = obj.obj("author")
            val stateStr = obj.str("state") ?: "OPEN"
            val statusState = (obj.obj("commits")
                ?.arr("nodes")
                ?.firstOrNull() as? JsonObject)
                ?.obj("commit")
                ?.obj("statusCheckRollup")
                ?.str("state")

            IssueOrPullSummary(
                id = obj.str("id") ?: "",
                number = obj.int("number") ?: 0,
                title = obj.str("title") ?: "",
                kind = if (typeName == "PullRequest") IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = parseState(stateStr),
                isDraft = obj.bool("isDraft") ?: false,
                repoFullName = repoObj.str("nameWithOwner") ?: "",
                repoOwner = repoObj.obj("owner")?.str("login") ?: "",
                repoName = repoObj.str("name") ?: "",
                author = parseUser(authorObj),
                createdAt = obj.str("createdAt") ?: "",
                updatedAt = obj.str("updatedAt") ?: "",
                commentCount = obj.obj("comments")?.int("totalCount") ?: 0,
                labels = parseLabels(obj.obj("labels")?.arr("nodes")),
                checkRollup = statusState?.let { CheckRollupSummary(state = it) },
                reviewDecision = obj.str("reviewDecision")
            )
        }
    }

    suspend fun fetchIssueOrPullDetail(
        owner: String,
        repo: String,
        number: Int
    ): IssueOrPullDetail {
        val query = """
            query {
              repository(owner: "$owner", name: "$repo") {
                viewerPermission
                issueOrPullRequest(number: $number) {
                  __typename
                  ... on Issue {
                    id
                    number
                    title
                    body
                    state
                    url
                    viewerCanUpdate
                    createdAt
                    updatedAt
                    author { login avatarUrl url }
                    labels(first: 10) { nodes { name color description } }
                    assignees(first: 6) { nodes { login avatarUrl url } }
                    milestone { number title description state dueOn }
                    reactionGroups { content viewerHasReacted reactors { totalCount } }
                    timelineItems(first: 50, itemTypes: [ISSUE_COMMENT, CLOSED_EVENT, REOPENED_EVENT]) {
                      nodes {
                        __typename
                        ... on IssueComment {
                          id
                          createdAt
                          body
                          authorAssociation
                          viewerCanUpdate
                          viewerCanDelete
                          author { login avatarUrl url }
                          reactionGroups { content viewerHasReacted reactors { totalCount } }
                        }
                        ... on ClosedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                        ... on ReopenedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                      }
                    }
                  }
                  ... on PullRequest {
                    id
                    number
                    title
                    body
                    state
                    isDraft
                    url
                    viewerCanUpdate
                    createdAt
                    updatedAt
                    headRefName
                    headRefOid
                    baseRefName
                    additions
                    deletions
                    changedFiles
                    mergeable
                    mergeStateStatus
                    reviewDecision
                    author { login avatarUrl url }
                    labels(first: 10) { nodes { name color description } }
                    assignees(first: 6) { nodes { login avatarUrl url } }
                    reviewRequests(first: 10) {
                      nodes {
                        requestedReviewer {
                          ... on User { login avatarUrl url }
                        }
                      }
                    }
                    milestone { number title description state dueOn }
                    reactionGroups { content viewerHasReacted reactors { totalCount } }
                    commits(last: 1) {
                      nodes {
                        commit {
                          statusCheckRollup { state }
                        }
                      }
                    }
                    timelineItems(first: 60, itemTypes: [ISSUE_COMMENT, PULL_REQUEST_REVIEW, PULL_REQUEST_COMMIT, MERGED_EVENT, CLOSED_EVENT, REOPENED_EVENT]) {
                      nodes {
                        __typename
                        ... on IssueComment {
                          id
                          createdAt
                          body
                          authorAssociation
                          viewerCanUpdate
                          viewerCanDelete
                          author { login avatarUrl url }
                          reactionGroups { content viewerHasReacted reactors { totalCount } }
                        }
                        ... on PullRequestReview {
                          id
                          createdAt
                          state
                          body
                          author { login avatarUrl url }
                        }
                        ... on PullRequestCommit {
                          id
                          commit {
                            abbreviatedOid
                            messageHeadline
                            committedDate
                            author { name }
                          }
                        }
                        ... on MergedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                          mergeRefName
                        }
                        ... on ClosedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                        ... on ReopenedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val data = execute(query)
        val repoNode = data.obj("repository")
        val viewerPermission = repoNode?.str("viewerPermission")?.uppercase()
        val item = repoNode?.obj("issueOrPullRequest")
            ?: throw GitHubApiException(404, "Issue or PR #$number not found in $owner/$repo", isSyntheticStatus = true)

        val viewerCanUpdate = item.bool("viewerCanUpdate") ?: false
        val canTriage = viewerPermission in TRIAGE_OR_HIGHER_PERMS
        val canWrite = viewerPermission in WRITE_OR_HIGHER_PERMS

        val typeName = item.str("__typename") ?: "Issue"
        val isPr = typeName == "PullRequest"
        val authorObj = item.obj("author")
        val statusState = (item.obj("commits")
            ?.arr("nodes")
            ?.firstOrNull() as? JsonObject)
            ?.obj("commit")
            ?.obj("statusCheckRollup")
            ?.str("state")

        val timelineNodes = item.obj("timelineItems")?.arr("nodes") ?: JsonArray(emptyList())
        val timeline = timelineNodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            when (obj.str("__typename")) {
                "IssueComment" -> TimelineEvent.CommentEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    author = parseUser(obj.obj("author")),
                    body = obj.str("body") ?: "",
                    authorAssociation = obj.str("authorAssociation"),
                    reactions = parseReactionGroups(obj.arr("reactionGroups")),
                    viewerCanUpdate = obj.bool("viewerCanUpdate") ?: false,
                    viewerCanDelete = obj.bool("viewerCanDelete") ?: false
                )
                "PullRequestReview" -> TimelineEvent.ReviewEventItem(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    author = parseUser(obj.obj("author")),
                    state = obj.str("state") ?: "COMMENTED",
                    body = obj.str("body") ?: ""
                )
                "PullRequestCommit" -> {
                    val commit = obj.obj("commit")
                    TimelineEvent.CommitEvent(
                        id = obj.str("id") ?: "",
                        createdAt = commit?.str("committedDate") ?: "",
                        oidShort = commit?.str("abbreviatedOid") ?: "",
                        messageHeadline = commit?.str("messageHeadline") ?: "",
                        authorName = commit?.obj("author")?.str("name") ?: ""
                    )
                }
                "MergedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = parseUser(obj.obj("actor")),
                    actionLabel = "merged into ${obj.str("mergeRefName") ?: "base"}"
                )
                "ClosedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = parseUser(obj.obj("actor")),
                    actionLabel = "closed this"
                )
                "ReopenedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = parseUser(obj.obj("actor")),
                    actionLabel = "reopened this"
                )
                else -> null
            }
        }

        val assignees = item.obj("assignees")?.arr("nodes")
            ?.mapNotNull { (it as? JsonObject)?.let(::parseUser) } ?: emptyList()

        val milestone = item.obj("milestone")?.let { ms ->
            val num = ms.int("number")
            val mTitle = ms.str("title")
            if (num != null && mTitle != null) {
                MilestoneSummary(
                    number = num,
                    title = mTitle,
                    description = ms.str("description"),
                    state = ms.str("state") ?: "OPEN",
                    dueOn = ms.str("dueOn")
                )
            } else null
        }

        val requestedReviewers = item.obj("reviewRequests")?.arr("nodes").orEmpty().mapNotNull { el ->
            val reviewer = (el as? JsonObject)?.obj("requestedReviewer") ?: return@mapNotNull null
            if (reviewer.str("login").isNullOrBlank()) null else parseUser(reviewer)
        }

        return IssueOrPullDetail(
            id = item.str("id") ?: "",
            number = item.int("number") ?: number,
            title = item.str("title") ?: "",
            body = item.str("body") ?: "",
            kind = if (isPr) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
            state = parseState(item.str("state") ?: "OPEN"),
            isDraft = item.bool("isDraft") ?: false,
            repoFullName = "$owner/$repo",
            repoOwner = owner,
            repoName = repo,
            author = parseUser(authorObj),
            createdAt = item.str("createdAt") ?: "",
            updatedAt = item.str("updatedAt") ?: "",
            labels = parseLabels(item.obj("labels")?.arr("nodes")),
            assignees = assignees,
            milestone = milestone,
            headRefName = item.str("headRefName"),
            baseRefName = item.str("baseRefName"),
            additions = item.int("additions") ?: 0,
            deletions = item.int("deletions") ?: 0,
            changedFilesCount = item.int("changedFiles") ?: 0,
            mergeable = item.str("mergeable"),
            mergeStateStatus = item.str("mergeStateStatus"),
            reviewDecision = item.str("reviewDecision"),
            checkRollup = statusState?.let { CheckRollupSummary(state = it) },
            htmlUrl = item.str("url") ?: "",
            timeline = timeline,
            viewerPermission = viewerPermission,
            viewerCanUpdate = viewerCanUpdate,
            viewerCanCloseOrReopen = viewerCanUpdate || canTriage,
            viewerCanMerge = canWrite,
            viewerCanEditMetadata = canTriage,
            headRefOid = item.str("headRefOid"),
            reactions = parseReactionGroups(item.arr("reactionGroups")),
            requestedReviewers = requestedReviewers
        )
    }

    suspend fun toggleReaction(
        subjectId: String,
        content: String,
        currentlyReacted: Boolean
    ): Boolean {
        val safeContent = content.uppercase()
        require(safeContent in VALID_REACTION_CONTENTS) { "Unsupported reaction: $content" }
        val op = if (currentlyReacted) "removeReaction" else "addReaction"
        val mutation = """
            mutation(${"$"}subjectId: ID!) {
              $op(input: { subjectId: ${"$"}subjectId, content: $safeContent }) {
                reaction { content }
              }
            }
        """.trimIndent()
        execute(mutation, mapOf("subjectId" to subjectId))
        return !currentlyReacted
    }

    suspend fun updateIssueComment(commentId: String, body: String) {
        val mutation = """
            mutation(${"$"}id: ID!, ${"$"}body: String!) {
              updateIssueComment(input: { id: ${"$"}id, body: ${"$"}body }) {
                issueComment { id }
              }
            }
        """.trimIndent()
        execute(mutation, mapOf("id" to commentId, "body" to body))
    }

    suspend fun deleteIssueComment(commentId: String) {
        val mutation = """
            mutation(${"$"}id: ID!) {
              deleteIssueComment(input: { id: ${"$"}id }) {
                clientMutationId
              }
            }
        """.trimIndent()
        execute(mutation, mapOf("id" to commentId))
    }

    suspend fun setPullRequestDraftState(pullRequestId: String, draft: Boolean) {
        val op = if (draft) "convertPullRequestToDraft" else "markPullRequestReadyForReview"
        val mutation = """
            mutation(${"$"}pullRequestId: ID!) {
              $op(input: { pullRequestId: ${"$"}pullRequestId }) {
                pullRequest { id isDraft }
              }
            }
        """.trimIndent()
        execute(mutation, mapOf("pullRequestId" to pullRequestId))
    }

    private fun parseReactionGroups(array: JsonArray?): List<ReactionSummary> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val content = obj.str("content") ?: return@mapNotNull null
            val count = obj.obj("reactors")?.int("totalCount") ?: 0
            val viewerReacted = obj.bool("viewerHasReacted") ?: false
            if (count <= 0 && !viewerReacted) return@mapNotNull null
            ReactionSummary(
                content = content,
                count = count,
                viewerHasReacted = viewerReacted
            )
        }
    }

    suspend fun fetchViewerRepositories(): List<RepoSummary> {
        val data = execute(
            """
            query {
              viewer {
                repositories(first: 40, orderBy: { field: UPDATED_AT, direction: DESC }, ownerAffiliations: [OWNER, COLLABORATOR, ORGANIZATION_MEMBER]) {
                  nodes { $REPO_FIELDS }
                }
              }
            }
            """.trimIndent()
        )
        val nodes = data.obj("viewer")?.obj("repositories")?.arr("nodes")
            ?: return emptyList()
        return nodes.mapNotNull { (it as? JsonObject)?.let(::parseRepoSummary) }
    }

    suspend fun fetchStarredRepositories(): List<RepoSummary> {
        val data = execute(
            """
            query {
              viewer {
                starredRepositories(first: 40, orderBy: { field: STARRED_AT, direction: DESC }) {
                  nodes { $REPO_FIELDS }
                }
              }
            }
            """.trimIndent()
        )
        val nodes = data.obj("viewer")?.obj("starredRepositories")?.arr("nodes")
            ?: return emptyList()
        return nodes.mapNotNull { (it as? JsonObject)?.let(::parseRepoSummary) }
    }

    suspend fun searchRepositories(query: String): List<RepoSummary> {
        val data = execute(
            query = """
                query(${'$'}q: String!) {
                  search(query: ${'$'}q, type: REPOSITORY, first: 30) {
                    nodes {
                      ... on Repository { $REPO_FIELDS }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("q" to query)
        )
        val nodes = data.obj("search")?.arr("nodes") ?: return emptyList()
        return nodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            if (obj.str("nameWithOwner").isNullOrBlank()) return@mapNotNull null
            parseRepoSummary(obj)
        }
    }

    suspend fun fetchRepoDetail(owner: String, name: String): RepoDetail {
        val data = execute(
            query = """
                query(${'$'}owner: String!, ${'$'}name: String!) {
                  repository(owner: ${'$'}owner, name: ${'$'}name) {
                    $REPO_FIELDS
                    homepageUrl
                    licenseInfo { name }
                    watchers { totalCount }
                    repositoryTopics(first: 10) {
                      nodes { topic { name } }
                    }
                    parent {
                      name
                      nameWithOwner
                      owner { login avatarUrl }
                      defaultBranchRef { name }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("owner" to owner, "name" to name)
        )
        val repoObj = data.obj("repository")
            ?: throw GitHubApiException(404, "Repository $owner/$name not found", isSyntheticStatus = true)
        val topics = repoObj.obj("repositoryTopics")?.arr("nodes")
            ?.mapNotNull { (it as? JsonObject)?.obj("topic")?.str("name") } ?: emptyList()

        return RepoDetail(
            summary = parseRepoSummary(repoObj),
            homepageUrl = repoObj.str("homepageUrl"),
            licenseName = repoObj.obj("licenseInfo")?.str("name"),
            topics = topics,
            watchersCount = repoObj.obj("watchers")?.int("totalCount") ?: 0
        )
    }

    suspend fun fetchRepoStargazers(owner: String, name: String, first: Int = 40): List<UserSummary> {
        val count = first.coerceIn(1, 100)
        val data = execute(
            query = """
                query(${'$'}owner: String!, ${'$'}name: String!) {
                  repository(owner: ${'$'}owner, name: ${'$'}name) {
                    stargazers(first: $count, orderBy: { field: STARRED_AT, direction: DESC }) {
                      nodes {
                        login
                        avatarUrl
                        url
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("owner" to owner, "name" to name)
        )
        val nodes = data.obj("repository")?.obj("stargazers")?.arr("nodes")
            ?: throw GitHubApiException(404, "Repository $owner/$name not found", isSyntheticStatus = true)
        return nodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            if (obj.str("login").isNullOrBlank()) return@mapNotNull null
            parseUser(obj)
        }
    }

    suspend fun fetchContributionCalendar(login: String): ContributionCalendar {
        val data = execute(
            query = """
                query(${'$'}login: String!) {
                  user(login: ${'$'}login) {
                    contributionsCollection {
                      contributionCalendar {
                        totalContributions
                        weeks {
                          contributionDays {
                            date
                            contributionCount
                            contributionLevel
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("login" to login)
        )
        val calendar = data.obj("user")
            ?.obj("contributionsCollection")
            ?.obj("contributionCalendar")
            ?: throw GitHubApiException(404, "Contribution calendar not found for @$login", isSyntheticStatus = true)

        val total = calendar.int("totalContributions") ?: 0
        val weeks = calendar.arr("weeks")?.mapNotNull { weekEl ->
            val weekObj = weekEl as? JsonObject ?: return@mapNotNull null
            val days = weekObj.arr("contributionDays")?.mapNotNull { dayEl ->
                val dayObj = dayEl as? JsonObject ?: return@mapNotNull null
                ContributionDay(
                    date = dayObj.str("date") ?: return@mapNotNull null,
                    count = dayObj.int("contributionCount") ?: 0,
                    level = parseContributionLevel(dayObj.str("contributionLevel"))
                )
            } ?: emptyList()
            ContributionWeek(days = days)
        } ?: emptyList()

        return ContributionCalendar(totalContributions = total, weeks = weeks)
    }

    private fun parseContributionLevel(raw: String?): Int = when (raw?.uppercase()) {
        "FIRST_QUARTILE" -> 1
        "SECOND_QUARTILE" -> 2
        "THIRD_QUARTILE" -> 3
        "FOURTH_QUARTILE" -> 4
        else -> 0
    }

    suspend fun fetchRepoIssuesAndPulls(
        owner: String,
        name: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary> {
        val field = if (pullRequests) "pullRequests" else "issues"
        val statesClause = when (stateFilter) {
            IssueStateFilter.OPEN -> "[OPEN]"
            IssueStateFilter.CLOSED -> if (pullRequests) "[CLOSED, MERGED]" else "[CLOSED]"
            IssueStateFilter.ALL -> if (pullRequests) "[OPEN, CLOSED, MERGED]" else "[OPEN, CLOSED]"
        }
        val data = execute(
            """
            query {
              repository(owner: "$owner", name: "$name") {
                $field(first: 30, states: $statesClause, orderBy: { field: UPDATED_AT, direction: DESC }) {
                  nodes {
                    id
                    number
                    title
                    state
                    ${if (pullRequests) "isDraft reviewDecision" else ""}
                    createdAt
                    updatedAt
                    author { login avatarUrl url }
                    comments { totalCount }
                    labels(first: 6) { nodes { name color description } }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val nodes = data.obj("repository")?.obj(field)?.arr("nodes")
            ?: return emptyList()
        return nodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            IssueOrPullSummary(
                id = obj.str("id") ?: "",
                number = obj.int("number") ?: 0,
                title = obj.str("title") ?: "",
                kind = if (pullRequests) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = parseState(obj.str("state") ?: "OPEN"),
                isDraft = obj.bool("isDraft") ?: false,
                repoFullName = "$owner/$name",
                repoOwner = owner,
                repoName = name,
                author = parseUser(obj.obj("author")),
                createdAt = obj.str("createdAt") ?: "",
                updatedAt = obj.str("updatedAt") ?: "",
                commentCount = obj.obj("comments")?.int("totalCount") ?: 0,
                labels = parseLabels(obj.obj("labels")?.arr("nodes")),
                reviewDecision = obj.str("reviewDecision")
            )
        }
    }

    private fun parseRepoSummary(obj: JsonObject): RepoSummary {
        val ownerObj = obj.obj("owner")
        val langObj = obj.obj("primaryLanguage")
        val defaultBranch = obj.obj("defaultBranchRef")?.str("name") ?: "main"
        val hasPermField = "viewerPermission" in obj
        val viewerPermission = obj.str("viewerPermission")?.uppercase()
        val parentObj = obj.obj("parent")
        val parentSummary = parentObj?.str("nameWithOwner")?.takeIf { it.isNotBlank() }?.let { parentFullName ->
            val parentOwnerObj = parentObj.obj("owner")
            val parentOwner = parentOwnerObj?.str("login")?.takeIf { it.isNotBlank() }
                ?: parentFullName.substringBefore('/')
            val parentName = parentObj.str("name")?.takeIf { it.isNotBlank() }
                ?: parentFullName.substringAfter('/')
            RepoParentSummary(
                name = parentName,
                owner = parentOwner,
                fullName = parentFullName,
                defaultBranch = parentObj.obj("defaultBranchRef")?.str("name") ?: "main",
                ownerAvatarUrl = parentOwnerObj?.str("avatarUrl").orEmpty()
            )
        }
        return RepoSummary(
            id = obj.str("id") ?: "",
            name = obj.str("name") ?: "",
            owner = ownerObj?.str("login") ?: "",
            fullName = obj.str("nameWithOwner") ?: "",
            description = obj.str("description"),
            language = langObj?.str("name"),
            languageColorHex = langObj?.str("color"),
            stargazersCount = obj.int("stargazerCount") ?: 0,
            forksCount = obj.int("forkCount") ?: 0,
            openIssuesCount = obj.obj("issues")?.int("totalCount") ?: 0,
            openPullRequestsCount = obj.obj("pullRequests")?.int("totalCount"),
            isPrivate = obj.bool("isPrivate") ?: false,
            isFork = obj.bool("isFork") ?: false,
            defaultBranch = defaultBranch,
            updatedAt = obj.str("updatedAt") ?: "",
            ownerAvatarUrl = ownerObj?.str("avatarUrl") ?: "",
            viewerHasStarred = obj.bool("viewerHasStarred") ?: false,
            viewerPermission = viewerPermission,
            hasKnownPermission = hasPermField,
            isArchived = obj.bool("isArchived") ?: false,
            hasIssuesEnabled = obj.bool("hasIssuesEnabled") ?: true,
            parent = parentSummary
        )
    }

    private fun parseUser(obj: JsonObject?): UserSummary = UserSummary(
        login = obj?.str("login") ?: "ghost",
        avatarUrl = obj?.str("avatarUrl") ?: "",
        htmlUrl = obj?.str("url") ?: ""
    )

    private fun parseLabels(array: JsonArray?): List<LabelTag> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            LabelTag(
                name = obj.str("name") ?: return@mapNotNull null,
                colorHex = obj.str("color") ?: "6750A4",
                description = obj.str("description")
            )
        }
    }

    private fun parseState(raw: String): IssueOrPullState = when (raw.uppercase()) {
        "MERGED" -> IssueOrPullState.MERGED
        "CLOSED" -> IssueOrPullState.CLOSED
        else -> IssueOrPullState.OPEN
    }

    suspend fun fetchDiscussions(
        owner: String,
        name: String,
        categoryId: String? = null,
        first: Int = 30
    ): DiscussionListData {
        val hasCategory = !categoryId.isNullOrBlank()
        val query = if (hasCategory) {
            """
                query(${"$"}owner: String!, ${"$"}name: String!, ${"$"}categoryId: ID!) {
                  repository(owner: ${"$"}owner, name: ${"$"}name) {
                    id
                    discussionCategories(first: 25) {
                      nodes { id name emoji description isAnswerable }
                    }
                    discussions(first: $first, categoryId: ${"$"}categoryId, orderBy: { field: UPDATED_AT, direction: DESC }) {
                      nodes { $DISCUSSION_SUMMARY_FIELDS }
                    }
                  }
                }
            """.trimIndent()
        } else {
            """
                query(${"$"}owner: String!, ${"$"}name: String!) {
                  repository(owner: ${"$"}owner, name: ${"$"}name) {
                    id
                    discussionCategories(first: 25) {
                      nodes { id name emoji description isAnswerable }
                    }
                    discussions(first: $first, orderBy: { field: UPDATED_AT, direction: DESC }) {
                      nodes { $DISCUSSION_SUMMARY_FIELDS }
                    }
                  }
                }
            """.trimIndent()
        }
        val vars = buildMap {
            put("owner", owner)
            put("name", name)
            if (hasCategory) put("categoryId", categoryId!!)
        }
        val data = execute(query, vars)
        val repoObj = data.obj("repository")
            ?: throw GitHubApiException(404, "Repository $owner/$name not found", isSyntheticStatus = true)
        val categories = parseDiscussionCategories(repoObj.obj("discussionCategories")?.arr("nodes"))
        val discussions = repoObj.obj("discussions")?.arr("nodes").orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let(::parseDiscussionSummary)
        }
        return DiscussionListData(
            repositoryId = repoObj.str("id") ?: "",
            categories = categories,
            discussions = discussions
        )
    }

    suspend fun fetchDiscussionDetail(
        owner: String,
        name: String,
        number: Int
    ): DiscussionDetail {
        val query = """
            query(${"$"}owner: String!, ${"$"}name: String!) {
              repository(owner: ${"$"}owner, name: ${"$"}name) {
                discussionCategories(first: 25) {
                  nodes { id name emoji description isAnswerable }
                }
                discussion(number: $number) {
                  $DISCUSSION_SUMMARY_FIELDS
                  comments(first: 50) {
                    nodes {
                      id
                      body
                      createdAt
                      isAnswer
                      upvoteCount
                      viewerHasUpvoted
                      author { login avatarUrl }
                      replies(first: 20) {
                        nodes {
                          id
                          body
                          createdAt
                          isAnswer
                          author { login avatarUrl }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val data = execute(query, mapOf("owner" to owner, "name" to name))
        val repoObj = data.obj("repository")
            ?: throw GitHubApiException(404, "Repository $owner/$name not found", isSyntheticStatus = true)
        val discussionObj = repoObj.obj("discussion")
            ?: throw GitHubApiException(404, "Discussion #$number not found", isSyntheticStatus = true)
        val categories = parseDiscussionCategories(repoObj.obj("discussionCategories")?.arr("nodes"))
        val comments = discussionObj.obj("comments")?.arr("nodes").orEmpty().mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val replies = obj.obj("replies")?.arr("nodes").orEmpty().mapNotNull { rEl ->
                val rObj = rEl as? JsonObject ?: return@mapNotNull null
                val rAuthor = rObj.obj("author")
                DiscussionReply(
                    id = rObj.str("id") ?: return@mapNotNull null,
                    body = rObj.str("body") ?: "",
                    authorLogin = rAuthor?.str("login") ?: "ghost",
                    authorAvatarUrl = rAuthor?.str("avatarUrl") ?: "",
                    createdAt = rObj.str("createdAt") ?: "",
                    isAnswer = rObj.bool("isAnswer") ?: false
                )
            }
            val author = obj.obj("author")
            DiscussionComment(
                id = obj.str("id") ?: return@mapNotNull null,
                body = obj.str("body") ?: "",
                authorLogin = author?.str("login") ?: "ghost",
                authorAvatarUrl = author?.str("avatarUrl") ?: "",
                createdAt = obj.str("createdAt") ?: "",
                isAnswer = obj.bool("isAnswer") ?: false,
                upvoteCount = obj.int("upvoteCount") ?: 0,
                viewerHasUpvoted = obj.bool("viewerHasUpvoted") ?: false,
                replies = replies
            )
        }
        return DiscussionDetail(
            summary = parseDiscussionSummary(discussionObj),
            comments = comments,
            categories = categories
        )
    }

    suspend fun createDiscussion(
        repositoryId: String,
        categoryId: String,
        title: String,
        body: String
    ): DiscussionSummary {
        val mutation = """
            mutation(${"$"}repositoryId: ID!, ${"$"}categoryId: ID!, ${"$"}title: String!, ${"$"}body: String!) {
              createDiscussion(input: { repositoryId: ${"$"}repositoryId, categoryId: ${"$"}categoryId, title: ${"$"}title, body: ${"$"}body }) {
                discussion { $DISCUSSION_SUMMARY_FIELDS }
              }
            }
        """.trimIndent()
        val data = execute(
            mutation,
            mapOf(
                "repositoryId" to repositoryId,
                "categoryId" to categoryId,
                "title" to title,
                "body" to body
            )
        )
        val created = data.obj("createDiscussion")?.obj("discussion")
            ?: throw GitHubApiException(422, "Failed to create discussion", isSyntheticStatus = true)
        return parseDiscussionSummary(created)
    }

    suspend fun addDiscussionComment(
        discussionId: String,
        body: String,
        replyToId: String? = null
    ): DiscussionComment {
        val hasReply = !replyToId.isNullOrBlank()
        val mutation = if (hasReply) {
            """
                mutation(${"$"}discussionId: ID!, ${"$"}body: String!, ${"$"}replyToId: ID!) {
                  addDiscussionComment(input: { discussionId: ${"$"}discussionId, body: ${"$"}body, replyToId: ${"$"}replyToId }) {
                    comment {
                      id
                      body
                      createdAt
                      isAnswer
                      upvoteCount
                      viewerHasUpvoted
                      author { login avatarUrl }
                    }
                  }
                }
            """.trimIndent()
        } else {
            """
                mutation(${"$"}discussionId: ID!, ${"$"}body: String!) {
                  addDiscussionComment(input: { discussionId: ${"$"}discussionId, body: ${"$"}body }) {
                    comment {
                      id
                      body
                      createdAt
                      isAnswer
                      upvoteCount
                      viewerHasUpvoted
                      author { login avatarUrl }
                    }
                  }
                }
            """.trimIndent()
        }
        val vars = buildMap {
            put("discussionId", discussionId)
            put("body", body)
            if (hasReply) put("replyToId", replyToId!!)
        }
        val data = execute(mutation, vars)
        val cObj = data.obj("addDiscussionComment")?.obj("comment")
            ?: throw GitHubApiException(422, "Failed to add discussion comment", isSyntheticStatus = true)
        val author = cObj.obj("author")
        return DiscussionComment(
            id = cObj.str("id") ?: "",
            body = cObj.str("body") ?: body,
            authorLogin = author?.str("login") ?: "you",
            authorAvatarUrl = author?.str("avatarUrl") ?: "",
            createdAt = cObj.str("createdAt") ?: "",
            isAnswer = cObj.bool("isAnswer") ?: false,
            upvoteCount = cObj.int("upvoteCount") ?: 0,
            viewerHasUpvoted = cObj.bool("viewerHasUpvoted") ?: false
        )
    }

    suspend fun toggleDiscussionUpvote(
        subjectId: String,
        currentlyUpvoted: Boolean
    ): Pair<Boolean, Int> {
        val op = if (currentlyUpvoted) "removeUpvote" else "addUpvote"
        val mutation = """
            mutation(${"$"}subjectId: ID!) {
              $op(input: { subjectId: ${"$"}subjectId }) {
                subject {
                  upvoteCount
                  viewerHasUpvoted
                }
              }
            }
        """.trimIndent()
        val data = execute(mutation, mapOf("subjectId" to subjectId))
        val subjectObj = data.obj(op)?.obj("subject")
        val upvoted = subjectObj?.bool("viewerHasUpvoted") ?: !currentlyUpvoted
        val count = subjectObj?.int("upvoteCount") ?: 0
        return upvoted to count
    }

    private fun parseDiscussionCategories(array: JsonArray?): List<DiscussionCategory> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            DiscussionCategory(
                id = obj.str("id") ?: return@mapNotNull null,
                name = obj.str("name") ?: return@mapNotNull null,
                emoji = obj.str("emoji") ?: "",
                description = obj.str("description") ?: "",
                isAnswerable = obj.bool("isAnswerable") ?: false
            )
        }
    }

    private fun parseDiscussionSummary(obj: JsonObject): DiscussionSummary {
        val catObj = obj.obj("category")
        val authorObj = obj.obj("author")
        return DiscussionSummary(
            id = obj.str("id") ?: "",
            number = obj.int("number") ?: 0,
            title = obj.str("title") ?: "",
            body = obj.str("body") ?: "",
            category = DiscussionCategory(
                id = catObj?.str("id") ?: "",
                name = catObj?.str("name") ?: "General",
                emoji = catObj?.str("emoji") ?: "",
                description = catObj?.str("description") ?: "",
                isAnswerable = catObj?.bool("isAnswerable") ?: false
            ),
            authorLogin = authorObj?.str("login") ?: "ghost",
            authorAvatarUrl = authorObj?.str("avatarUrl") ?: "",
            createdAt = obj.str("createdAt") ?: "",
            updatedAt = obj.str("updatedAt") ?: "",
            commentsCount = obj.obj("comments")?.int("totalCount") ?: 0,
            upvoteCount = obj.int("upvoteCount") ?: 0,
            viewerHasUpvoted = obj.bool("viewerHasUpvoted") ?: false,
            isAnswered = obj.bool("isAnswered") ?: false,
            isClosed = obj.bool("closed") ?: false,
            htmlUrl = obj.str("url") ?: ""
        )
    }

    internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    internal fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.contentOrNull }

    internal fun JsonObject.int(key: String): Int? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.intOrNull }

    internal fun JsonObject.bool(key: String): Boolean? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.booleanOrNull }

    companion object {
        private val TRIAGE_OR_HIGHER_PERMS = setOf("ADMIN", "MAINTAIN", "WRITE", "TRIAGE")
        private val WRITE_OR_HIGHER_PERMS = setOf("ADMIN", "MAINTAIN", "WRITE")
        private val VALID_REACTION_CONTENTS = setOf(
            "THUMBS_UP", "THUMBS_DOWN", "LAUGH", "HOORAY", "CONFUSED", "HEART", "ROCKET", "EYES"
        )

        private const val REPO_FIELDS = """
            id
            name
            nameWithOwner
            description
            isPrivate
            isFork
            isArchived
            hasIssuesEnabled
            viewerPermission
            stargazerCount
            forkCount
            updatedAt
            viewerHasStarred
            owner { login avatarUrl }
            primaryLanguage { name color }
            defaultBranchRef { name }
            issues(states: OPEN) { totalCount }
            pullRequests(states: OPEN) { totalCount }
        """

        private const val DISCUSSION_SUMMARY_FIELDS = """
            id
            number
            title
            body
            createdAt
            updatedAt
            upvoteCount
            viewerHasUpvoted
            isAnswered
            closed
            url
            author { login avatarUrl }
            category { id name emoji description isAnswerable }
            comments { totalCount }
        """
    }
}
