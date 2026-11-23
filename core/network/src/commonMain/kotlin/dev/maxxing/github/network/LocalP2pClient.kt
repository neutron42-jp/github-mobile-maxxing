package dev.maxxing.github.network

import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.TreeEntry

data class LocalClientIdentity(
    val secretKeyHex: String,
    val endpointId: String
)

data class LocalParsedPairingCode(
    val ticket: String,
    val token: String,
    val hostName: String,
    val rootName: String,
    val expiresAtEpochSeconds: Long
)

data class LocalHostInfo(
    val hostName: String,
    val rootName: String,
    val endpointId: String,
    val repoCount: Int
)

data class LocalPairResult(
    val endpointTicket: String,
    val host: LocalHostInfo
)

data class LocalWorkingDiffResult(
    val branch: String,
    val files: List<PullFileChange>
)

data class LocalDirectoryEntry(
    val name: String,
    val relativePath: String,
    val isGitRepo: Boolean
)

data class LocalBrowseResult(
    val rootDisplayName: String,
    val currentRelativePath: String,
    val currentIsGitRepo: Boolean,
    val entries: List<LocalDirectoryEntry>
)

expect object LocalP2pClient {
    val isSupported: Boolean

    fun generateClientIdentity(): LocalClientIdentity
    fun deriveClientEndpointId(clientSecretHex: String): String
    fun parsePairingCode(code: String): LocalParsedPairingCode

    suspend fun pairWithHost(
        clientSecretHex: String,
        pairingCode: String,
        clientName: String
    ): LocalPairResult

    suspend fun getHostInfo(
        clientSecretHex: String,
        endpointTicket: String
    ): LocalHostInfo

    suspend fun listRepos(
        clientSecretHex: String,
        endpointTicket: String
    ): List<RepoSummary>

    suspend fun listRegisteredRepos(
        clientSecretHex: String,
        endpointTicket: String,
        repoPaths: List<String>
    ): List<RepoSummary>

    suspend fun browseDirectories(
        clientSecretHex: String,
        endpointTicket: String,
        dirPath: String
    ): LocalBrowseResult

    suspend fun getTree(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        dirPath: String
    ): List<TreeEntry>

    suspend fun getFile(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        filePath: String
    ): FileContent

    suspend fun getCommits(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        limit: Int
    ): List<CommitSummary>

    suspend fun getWorkingDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String
    ): LocalWorkingDiffResult

    suspend fun getCommitDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        sha: String
    ): CommitDetail
}

/** Splits a local repository relative path into lossless `(owner, name)` pair for RepoSummary navigation. */
internal fun splitLocalRepoRelativePath(relativePath: String, displayName: String): Pair<String, String> {
    val clean = relativePath.trim().trimEnd('/').replace('\\', '/')
    if (clean.isEmpty() || clean == ".") return "." to "."
    if ('/' !in clean) return "." to clean
    return clean.substringBeforeLast('/') to clean.substringAfterLast('/')
}

/** Reconstructs the relative repo path inside the host's `--root` directory from `(owner, repo)`. */
internal fun joinLocalRepoRelativePath(owner: String, repo: String): String {
    val cleanOwner = owner.trim().trim('/')
    val cleanRepo = repo.trim().trim('/')
    return when {
        (cleanOwner == "." || cleanOwner.isEmpty()) && (cleanRepo == "." || cleanRepo.isEmpty()) -> "."
        cleanOwner == "." || cleanOwner.isEmpty() -> cleanRepo
        cleanRepo.isEmpty() -> cleanOwner
        else -> "$cleanOwner/$cleanRepo"
    }
}
