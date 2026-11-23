package dev.maxxing.github.network

import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.TreeEntry

actual object LocalP2pClient {
    actual val isSupported: Boolean = false

    actual fun generateClientIdentity(): LocalClientIdentity =
        throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual fun deriveClientEndpointId(clientSecretHex: String): String =
        throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual fun parsePairingCode(code: String): LocalParsedPairingCode =
        throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual suspend fun pairWithHost(
        clientSecretHex: String,
        pairingCode: String,
        clientName: String
    ): LocalPairResult = throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual suspend fun getHostInfo(
        clientSecretHex: String,
        endpointTicket: String
    ): LocalHostInfo = throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual suspend fun listRepos(
        clientSecretHex: String,
        endpointTicket: String
    ): List<RepoSummary> = emptyList()

    actual suspend fun listRegisteredRepos(
        clientSecretHex: String,
        endpointTicket: String,
        repoPaths: List<String>
    ): List<RepoSummary> = emptyList()

    actual suspend fun browseDirectories(
        clientSecretHex: String,
        endpointTicket: String,
        dirPath: String
    ): LocalBrowseResult = throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual suspend fun getTree(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        dirPath: String
    ): List<TreeEntry> = emptyList()

    actual suspend fun getFile(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        filePath: String
    ): FileContent = throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")

    actual suspend fun getCommits(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        limit: Int
    ): List<CommitSummary> = emptyList()

    actual suspend fun getWorkingDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String
    ): LocalWorkingDiffResult = LocalWorkingDiffResult(branch = "", files = emptyList())

    actual suspend fun getCommitDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        sha: String
    ): CommitDetail = throw UnsupportedOperationException("Local P2P requires native iOS UniFFI build on macOS.")
}
