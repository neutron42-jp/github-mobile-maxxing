package dev.maxxing.github.network

import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.TreeEntry

actual object LocalP2pClient {
    actual val isSupported: Boolean = true

    actual fun generateClientIdentity(): LocalClientIdentity =
        JvmLocalP2pBridge.generateClientIdentity()

    actual fun deriveClientEndpointId(clientSecretHex: String): String =
        JvmLocalP2pBridge.deriveClientEndpointId(clientSecretHex)

    actual fun parsePairingCode(code: String): LocalParsedPairingCode =
        JvmLocalP2pBridge.parsePairingCode(code)

    actual suspend fun pairWithHost(
        clientSecretHex: String,
        pairingCode: String,
        clientName: String
    ): LocalPairResult = JvmLocalP2pBridge.pairWithHost(clientSecretHex, pairingCode, clientName)

    actual suspend fun getHostInfo(
        clientSecretHex: String,
        endpointTicket: String
    ): LocalHostInfo = JvmLocalP2pBridge.getHostInfo(clientSecretHex, endpointTicket)

    actual suspend fun listRepos(
        clientSecretHex: String,
        endpointTicket: String
    ): List<RepoSummary> = JvmLocalP2pBridge.listRepos(clientSecretHex, endpointTicket)

    actual suspend fun listRegisteredRepos(
        clientSecretHex: String,
        endpointTicket: String,
        repoPaths: List<String>
    ): List<RepoSummary> = JvmLocalP2pBridge.listRegisteredRepos(clientSecretHex, endpointTicket, repoPaths)

    actual suspend fun browseDirectories(
        clientSecretHex: String,
        endpointTicket: String,
        dirPath: String
    ): LocalBrowseResult = JvmLocalP2pBridge.browseDirectories(clientSecretHex, endpointTicket, dirPath)

    actual suspend fun getTree(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        dirPath: String
    ): List<TreeEntry> = JvmLocalP2pBridge.getTree(clientSecretHex, endpointTicket, repoPath, gitRef, dirPath)

    actual suspend fun getFile(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        filePath: String
    ): FileContent = JvmLocalP2pBridge.getFile(clientSecretHex, endpointTicket, repoPath, gitRef, filePath)

    actual suspend fun getCommits(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        gitRef: String?,
        limit: Int
    ): List<CommitSummary> = JvmLocalP2pBridge.getCommits(clientSecretHex, endpointTicket, repoPath, gitRef, limit)

    actual suspend fun getWorkingDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String
    ): LocalWorkingDiffResult = JvmLocalP2pBridge.getWorkingDiff(clientSecretHex, endpointTicket, repoPath)

    actual suspend fun getCommitDiff(
        clientSecretHex: String,
        endpointTicket: String,
        repoPath: String,
        sha: String
    ): CommitDetail = JvmLocalP2pBridge.getCommitDiff(clientSecretHex, endpointTicket, repoPath, sha)
}
