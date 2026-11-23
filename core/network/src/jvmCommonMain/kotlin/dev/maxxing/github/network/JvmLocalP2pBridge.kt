package dev.maxxing.github.network

import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.TreeEntry
import dev.maxxing.github.model.TreeEntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.local_client_ffi.FfiBrowseResult
import uniffi.local_client_ffi.FfiCommitSummary
import uniffi.local_client_ffi.FfiDiffFile
import uniffi.local_client_ffi.FfiDiffStatus
import uniffi.local_client_ffi.FfiHostInfo
import uniffi.local_client_ffi.FfiRepoSummary
import uniffi.local_client_ffi.FfiTreeEntryKind
import uniffi.local_client_ffi.browseDirectories as ffiBrowseDirectories
import uniffi.local_client_ffi.deriveClientEndpointId as ffiDeriveEndpointId
import uniffi.local_client_ffi.generateClientIdentity as ffiGenerateIdentity
import uniffi.local_client_ffi.getCommitDiff as ffiGetCommitDiff
import uniffi.local_client_ffi.getCommits as ffiGetCommits
import uniffi.local_client_ffi.getFile as ffiGetFile
import uniffi.local_client_ffi.getHostInfo as ffiGetHostInfo
import uniffi.local_client_ffi.getTree as ffiGetTree
import uniffi.local_client_ffi.getWorkingDiff as ffiGetWorkingDiff
import uniffi.local_client_ffi.listRegisteredRepos as ffiListRegisteredRepos
import uniffi.local_client_ffi.listRepos as ffiListRepos
import uniffi.local_client_ffi.pairWithHost as ffiPairWithHost
import uniffi.local_client_ffi.parsePairingCode as ffiParsePairingCode
import kotlin.time.Instant

internal object JvmLocalP2pBridge {
    fun generateClientIdentity(): LocalClientIdentity {
        val res = ffiGenerateIdentity()
        return LocalClientIdentity(res.secretKeyHex, res.endpointId)
    }

    fun deriveClientEndpointId(clientSecretHex: String): String =
        ffiDeriveEndpointId(clientSecretHex)

    fun parsePairingCode(code: String): LocalParsedPairingCode {
        val parsed = ffiParsePairingCode(code)
        return LocalParsedPairingCode(
            ticket = parsed.ticket,
            token = parsed.token,
            hostName = parsed.hostName,
            rootName = parsed.rootName,
            expiresAtEpochSeconds = parsed.expiresAtEpochS.toLong()
        )
    }

    suspend fun pairWithHost(
        clientSecretHex: String,
        pairingCode: String,
        clientName: String
    ): LocalPairResult = withContext(Dispatchers.IO) {
        val res = ffiPairWithHost(clientSecretHex, pairingCode, clientName)
        LocalPairResult(
            endpointTicket = res.endpointTicket,
            host = res.host.toDomain()
        )
    }

    suspend fun getHostInfo(
        clientSecretHex: String,
        endpointTicket: String
    ): LocalHostInfo = withContext(Dispatchers.IO) {
        ffiGetHostInfo(clientSecretHex, endpointTicket).toDomain()
    }

    suspend fun listRepos(
        clientSecretHex: String,
        endpointTicket: String
    ): List<RepoSummary> = withContext(Dispatchers.IO) {
        ffiListRepos(clientSecretHex, endpointTicket).map { it.toDomain() }
    }

    suspend fun listRegisteredRepos(
        clientSecretHex: String,
        endpointTicket: String,
        repoPaths: List<String>
    ): List<RepoSummary> = withContext(Dispatchers.IO) {
        ffiListRegisteredRepos(clientSecretHex, endpointTicket, repoPaths).map { it.toDomain() }
    }

    suspend fun browseDirectories(
        clientSecretHex: String,
        endpointTicket: String,
        dirPath: String
    ): LocalBrowseResult = withContext(Dispatchers.IO) {
        ffiBrowseDirectories(clientSecretHex, endpointTicket, dirPath).toDomain()
    }

    suspend fun getTree(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        dirPath: String
    ): List<TreeEntry> = withContext(Dispatchers.IO) {
        ffiGetTree(clientSecretHex, endpointTicket, repoPath, gitRef, dirPath).map { e ->
            TreeEntry(
                path = e.path,
                name = e.name,
                type = when (e.kind) {
                    FfiTreeEntryKind.DIRECTORY -> TreeEntryType.TREE
                    FfiTreeEntryKind.FILE, FfiTreeEntryKind.SUBMODULE -> TreeEntryType.BLOB
                },
                sha = e.sha,
                size = e.size.toLong()
            )
        }
    }

    suspend fun getFile(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        filePath: String
    ): FileContent = withContext(Dispatchers.IO) {
        val f = ffiGetFile(clientSecretHex, endpointTicket, repoPath, gitRef, filePath)
        FileContent(
            path = f.path,
            name = f.name,
            size = f.size.toLong(),
            content = f.text.orEmpty(),
            isBinary = f.isBinary,
            htmlUrl = "",
            sha = f.sha
        )
    }

    suspend fun getCommits(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        limit: Int
    ): List<CommitSummary> = withContext(Dispatchers.IO) {
        ffiGetCommits(clientSecretHex, endpointTicket, repoPath, gitRef, limit.coerceAtLeast(1).toUInt())
            .map { it.toDomain() }
    }

    suspend fun getWorkingDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String
    ): LocalWorkingDiffResult = withContext(Dispatchers.IO) {
        val res = ffiGetWorkingDiff(clientSecretHex, endpointTicket, repoPath)
        LocalWorkingDiffResult(
            branch = res.branch,
            files = res.files.map { it.toDomain() }
        )
    }

    suspend fun getCommitDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        sha: String
    ): CommitDetail = withContext(Dispatchers.IO) {
        val res = ffiGetCommitDiff(clientSecretHex, endpointTicket, repoPath, sha)
        val mappedFiles = res.files.map { it.toDomain() }
        CommitDetail(
            summary = res.commit.toDomain(),
            additions = mappedFiles.sumOf { it.additions },
            deletions = mappedFiles.sumOf { it.deletions },
            changedFilesCount = mappedFiles.size,
            files = mappedFiles,
            htmlUrl = ""
        )
    }

    private fun FfiRepoSummary.toDomain(): RepoSummary {
        val (owner, parsedName) = splitLocalRepoRelativePath(relativePath, name)
        val fullName = if (relativePath == "." || relativePath.isBlank()) name else relativePath
        val latestHeadline = latestCommit?.message?.takeIf { it.isNotBlank() }
        val dirtyTag = if (isDirty) "Dirty ($changedFilesCount)" else "Clean"
        val desc = buildString {
            append(dirtyTag)
            if (latestHeadline != null) {
                append(" · ")
                append(latestHeadline)
            }
        }
        return RepoSummary(
            id = "local:$relativePath",
            name = if (parsedName == ".") name else parsedName,
            owner = owner,
            fullName = fullName,
            description = desc,
            defaultBranch = currentBranch.ifBlank { "main" },
            updatedAt = latestCommit?.committedAtEpochS?.let { formatIsoTimestamp(it) }.orEmpty()
        )
    }

    private fun FfiBrowseResult.toDomain() = LocalBrowseResult(
        rootDisplayName = rootDisplayName,
        currentRelativePath = currentRelativePath,
        currentIsGitRepo = currentIsGitRepo,
        entries = entries.map { e ->
            LocalDirectoryEntry(
                name = e.name,
                relativePath = e.relativePath,
                isGitRepo = e.isGitRepo
            )
        }
    )

    private fun FfiHostInfo.toDomain() = LocalHostInfo(
        hostName = hostName,
        rootName = rootName,
        endpointId = endpointId,
        repoCount = repoCount.toInt()
    )

    private fun FfiCommitSummary.toDomain() = CommitSummary(
        sha = sha,
        shortSha = shortSha,
        headline = message,
        authorName = authorName,
        committedAt = formatIsoTimestamp(committedAtEpochS)
    )

    private fun FfiDiffFile.toDomain() = PullFileChange(
        filename = path,
        previousFilename = previousPath,
        status = when (status) {
            FfiDiffStatus.ADDED, FfiDiffStatus.UNTRACKED -> FileChangeStatus.ADDED
            FfiDiffStatus.MODIFIED -> FileChangeStatus.MODIFIED
            FfiDiffStatus.DELETED -> FileChangeStatus.REMOVED
            FfiDiffStatus.RENAMED -> FileChangeStatus.RENAMED
        },
        additions = additions.toInt(),
        deletions = deletions.toInt(),
        patch = patch,
        hunks = DiffParser.parse(patch)
    )

    private fun formatIsoTimestamp(epochSeconds: Long): String = runCatching {
        Instant.fromEpochSeconds(epochSeconds).toString()
    }.getOrDefault("")
}
