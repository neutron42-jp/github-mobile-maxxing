package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class GlobalSearchKind {
    REPOSITORIES,
    ISSUES_PRS,
    CODE,
    USERS
}

@Serializable
data class CodeSearchHit(
    val name: String,
    val path: String,
    val sha: String,
    val repoFullName: String,
    val repoOwner: String,
    val repoName: String,
    val htmlUrl: String = "",
    val fragments: List<String> = emptyList()
)
