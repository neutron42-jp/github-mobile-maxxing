package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
data class GistFile(
    val filename: String,
    val language: String? = null,
    val rawUrl: String = "",
    val size: Long = 0L,
    val content: String = "",
    val truncated: Boolean = false
)

@Serializable
data class GistSummary(
    val id: String,
    val description: String = "",
    val isPublic: Boolean = true,
    val htmlUrl: String = "",
    val ownerLogin: String = "",
    val ownerAvatarUrl: String = "",
    val files: List<GistFile> = emptyList(),
    /**
     * True when GitHub returned only part of the Gist's file list (the top-level `truncated`
     * field, set when the Gist has more than 300 files). Names outside [files] are unknown, so
     * a rename can only be checked against the names that were actually returned.
     */
    val filesTruncated: Boolean = false,
    val commentsCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class GistDetail(
    val summary: GistSummary,
    val files: List<GistFile>,
    val viewerHasStarred: Boolean = false
)
