use iroh::endpoint::{presets, Connection};
use iroh::{Endpoint, EndpointId, SecretKey};
use iroh_tickets::endpoint::EndpointTicket;
use local_protocol::{
    read_frame, write_frame, HostInfo, LocalBrowseResult, LocalCommitDiff, LocalCommitSummary,
    LocalDiffFile, LocalDiffStatus, LocalDirectoryEntry, LocalFileContent, LocalRepoSummary,
    LocalTreeEntry, LocalTreeEntryKind, LocalWorkingDiff, PairingPayload, RpcErrorCode, RpcRequest,
    RpcResponse, LOCAL_ALPN,
};
use std::collections::HashMap;
use std::str::FromStr;
use std::sync::{Mutex, OnceLock};
use tokio::runtime::Runtime;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum LocalClientError {
    #[error("Invalid pairing or ticket: {reason}")]
    InvalidInput { reason: String },
    #[error("Unauthorized peer: {reason}")]
    Unauthorized { reason: String },
    #[error("Connection failed: {reason}")]
    Connection { reason: String },
    #[error("RPC error: {reason}")]
    Rpc { reason: String },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiClientIdentity {
    pub secret_key_hex: String,
    pub endpoint_id: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiParsedPairingCode {
    pub ticket: String,
    pub token: String,
    pub host_name: String,
    pub root_name: String,
    pub expires_at_epoch_s: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiHostInfo {
    pub host_name: String,
    pub root_name: String,
    pub endpoint_id: String,
    pub repo_count: u32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiPairResult {
    pub endpoint_ticket: String,
    pub host: FfiHostInfo,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiCommitSummary {
    pub sha: String,
    pub short_sha: String,
    pub message: String,
    pub author_name: String,
    pub committed_at_epoch_s: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiRepoSummary {
    pub name: String,
    pub relative_path: String,
    pub current_branch: String,
    pub is_dirty: bool,
    pub changed_files_count: u32,
    pub latest_commit: Option<FfiCommitSummary>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiDirectoryEntry {
    pub name: String,
    pub relative_path: String,
    pub is_git_repo: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiBrowseResult {
    pub root_display_name: String,
    pub current_relative_path: String,
    pub current_is_git_repo: bool,
    pub entries: Vec<FfiDirectoryEntry>,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum FfiTreeEntryKind {
    File,
    Directory,
    Submodule,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiTreeEntry {
    pub name: String,
    pub path: String,
    pub kind: FfiTreeEntryKind,
    pub size: u64,
    pub sha: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiFileContent {
    pub name: String,
    pub path: String,
    pub size: u64,
    pub sha: String,
    pub text: Option<String>,
    pub is_binary: bool,
    pub truncated: bool,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum FfiDiffStatus {
    Added,
    Modified,
    Deleted,
    Renamed,
    Untracked,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiDiffFile {
    pub path: String,
    pub previous_path: Option<String>,
    pub status: FfiDiffStatus,
    pub additions: u32,
    pub deletions: u32,
    pub patch: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiWorkingDiff {
    pub branch: String,
    pub files: Vec<FfiDiffFile>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiCommitDiff {
    pub commit: FfiCommitSummary,
    pub files: Vec<FfiDiffFile>,
}

struct ClientPool {
    secret_hex: String,
    endpoint: Endpoint,
    connections: HashMap<EndpointId, Connection>,
}

static RUNTIME: OnceLock<Runtime> = OnceLock::new();
static POOL: OnceLock<Mutex<Option<ClientPool>>> = OnceLock::new();

fn runtime() -> &'static Runtime {
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .worker_threads(2)
            .build()
            .expect("Failed starting tokio runtime")
    })
}

fn pool() -> &'static Mutex<Option<ClientPool>> {
    POOL.get_or_init(|| Mutex::new(None))
}

fn parse_secret_key(secret_hex: &str) -> Result<SecretKey, LocalClientError> {
    let bytes = hex::decode(secret_hex.trim()).map_err(|e| LocalClientError::InvalidInput {
        reason: format!("Invalid client secret hex: {e}"),
    })?;
    if bytes.len() != 32 {
        return Err(LocalClientError::InvalidInput {
            reason: "Client secret must be 32 bytes".into(),
        });
    }
    let arr: [u8; 32] = bytes.try_into().unwrap();
    Ok(SecretKey::from_bytes(&arr))
}

async fn get_or_connect(
    client_secret_hex: &str,
    ticket_str: &str,
) -> Result<(Endpoint, Connection), LocalClientError> {
    let ticket = EndpointTicket::from_str(ticket_str.trim()).map_err(|e| {
        LocalClientError::InvalidInput {
            reason: format!("Invalid iroh ticket: {e}"),
        }
    })?;
    let target_addr = ticket.endpoint_addr().clone();
    let target_id = target_addr.id;

    let cached_ep = {
        let mut guard = pool().lock().map_err(|_| LocalClientError::Connection {
            reason: "Client pool mutex poisoned".into(),
        })?;
        if let Some(existing) = guard.as_mut() {
            if existing.secret_hex == client_secret_hex {
                if let Some(conn) = existing.connections.get(&target_id) {
                    if conn.close_reason().is_none() {
                        return Ok((existing.endpoint.clone(), conn.clone()));
                    }
                }
                Some(existing.endpoint.clone())
            } else {
                None
            }
        } else {
            None
        }
    };

    let endpoint = match cached_ep {
        Some(ep) => ep,
        None => {
            let sk = parse_secret_key(client_secret_hex)?;
            let ep = Endpoint::builder(presets::N0)
                .secret_key(sk)
                .alpns(vec![LOCAL_ALPN.to_vec()])
                .bind()
                .await
                .map_err(|e| LocalClientError::Connection {
                    reason: format!("Failed binding iroh endpoint: {e}"),
                })?;
            ep.online().await;
            let mut guard = pool().lock().map_err(|_| LocalClientError::Connection {
                reason: "Client pool mutex poisoned".into(),
            })?;
            *guard = Some(ClientPool {
                secret_hex: client_secret_hex.to_string(),
                endpoint: ep.clone(),
                connections: HashMap::new(),
            });
            ep
        }
    };

    let conn = endpoint
        .connect(target_addr, LOCAL_ALPN)
        .await
        .map_err(|e| LocalClientError::Connection {
            reason: format!("Failed connecting to host: {e}"),
        })?;

    if let Ok(mut guard) = pool().lock() {
        if let Some(existing) = guard.as_mut() {
            if existing.secret_hex == client_secret_hex {
                existing.connections.insert(target_id, conn.clone());
            }
        }
    }
    Ok((endpoint, conn))
}

fn invalidate_cached_connection(ticket_str: &str) {
    if let Ok(ticket) = EndpointTicket::from_str(ticket_str.trim()) {
        if let Ok(mut guard) = pool().lock() {
            if let Some(existing) = guard.as_mut() {
                existing.connections.remove(&ticket.endpoint_addr().id);
            }
        }
    }
}

async fn send_rpc_once(conn: &Connection, req: &RpcRequest) -> Result<RpcResponse, LocalClientError> {
    let (mut send, mut recv) = conn
        .open_bi()
        .await
        .map_err(|e| LocalClientError::Connection {
            reason: format!("open_bi failed: {e}"),
        })?;
    write_frame(&mut send, req)
        .await
        .map_err(|e| LocalClientError::Connection {
            reason: format!("write_frame failed: {e}"),
        })?;
    let _ = send.finish();

    let resp: RpcResponse = read_frame(&mut recv)
        .await
        .map_err(|e| LocalClientError::Connection {
            reason: format!("read_frame failed: {e}"),
        })?;

    if let RpcResponse::Error { code, message } = resp {
        return Err(match code {
            RpcErrorCode::Unauthorized => LocalClientError::Unauthorized { reason: message },
            RpcErrorCode::InvalidArgument | RpcErrorCode::ForbiddenPath | RpcErrorCode::NotFound => {
                LocalClientError::Rpc { reason: message }
            }
            RpcErrorCode::Internal => LocalClientError::Rpc { reason: message },
        });
    }
    Ok(resp)
}

async fn perform_rpc(
    client_secret_hex: &str,
    ticket_str: &str,
    req: RpcRequest,
) -> Result<RpcResponse, LocalClientError> {
    let (_ep, conn) = get_or_connect(client_secret_hex, ticket_str).await?;
    match send_rpc_once(&conn, &req).await {
        Ok(resp) => Ok(resp),
        Err(LocalClientError::Connection { .. }) => {
            // Stale pooled connection: invalidate and retry once cleanly.
            invalidate_cached_connection(ticket_str);
            let (_ep2, conn2) = get_or_connect(client_secret_hex, ticket_str).await?;
            send_rpc_once(&conn2, &req).await
        }
        Err(e) => Err(e),
    }
}

#[uniffi::export]
pub fn generate_client_identity() -> FfiClientIdentity {
    let sk = SecretKey::generate();
    FfiClientIdentity {
        secret_key_hex: hex::encode(sk.to_bytes()),
        endpoint_id: sk.public().to_string(),
    }
}

#[uniffi::export]
pub fn derive_client_endpoint_id(client_secret_hex: String) -> Result<String, LocalClientError> {
    let sk = parse_secret_key(&client_secret_hex)?;
    Ok(sk.public().to_string())
}

#[uniffi::export]
pub fn parse_pairing_code(code: String) -> Result<FfiParsedPairingCode, LocalClientError> {
    let payload = PairingPayload::decode_code(&code).map_err(|e| LocalClientError::InvalidInput {
        reason: e.to_string(),
    })?;
    Ok(FfiParsedPairingCode {
        ticket: payload.ticket,
        token: payload.token,
        host_name: payload.host_name,
        root_name: payload.root_name,
        expires_at_epoch_s: payload.expires_at_epoch_s,
    })
}

#[uniffi::export]
pub fn pair_with_host(
    client_secret_hex: String,
    pairing_code: String,
    client_name: String,
) -> Result<FfiPairResult, LocalClientError> {
    let payload =
        PairingPayload::decode_code(&pairing_code).map_err(|e| LocalClientError::InvalidInput {
            reason: e.to_string(),
        })?;
    runtime().block_on(async move {
        let resp = perform_rpc(
            &client_secret_hex,
            &payload.ticket,
            RpcRequest::Pair {
                token: payload.token,
                client_name,
            },
        )
        .await?;
        match resp {
            RpcResponse::Paired(host) => Ok(FfiPairResult {
                endpoint_ticket: payload.ticket,
                host: map_host(host),
            }),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to Pair".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn get_host_info(
    client_secret_hex: String,
    endpoint_ticket: String,
) -> Result<FfiHostInfo, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(&client_secret_hex, &endpoint_ticket, RpcRequest::GetHostInfo).await? {
            RpcResponse::HostInfo(host) | RpcResponse::Pong(host) => Ok(map_host(host)),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetHostInfo".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn list_repos(
    client_secret_hex: String,
    endpoint_ticket: String,
) -> Result<Vec<FfiRepoSummary>, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(&client_secret_hex, &endpoint_ticket, RpcRequest::ListRepos).await? {
            RpcResponse::Repos(repos) => Ok(repos.into_iter().map(map_repo).collect()),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to ListRepos".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn list_registered_repos(
    client_secret_hex: String,
    endpoint_ticket: String,
    repo_paths: Vec<String>,
) -> Result<Vec<FfiRepoSummary>, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::GetRepoSummaries { repo_paths },
        )
        .await?
        {
            RpcResponse::Repos(repos) => Ok(repos.into_iter().map(map_repo).collect()),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetRepoSummaries".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn browse_directories(
    client_secret_hex: String,
    endpoint_ticket: String,
    dir_path: String,
) -> Result<FfiBrowseResult, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::BrowseDirectories { dir_path },
        )
        .await?
        {
            RpcResponse::Directories(browse) => Ok(map_browse_result(browse)),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to BrowseDirectories".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn get_tree(
    client_secret_hex: String,
    endpoint_ticket: String,
    repo_path: String,
    git_ref: Option<String>,
    dir_path: String,
) -> Result<Vec<FfiTreeEntry>, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::GetTree {
                repo_path,
                git_ref,
                dir_path,
            },
        )
        .await?
        {
            RpcResponse::Tree(entries) => Ok(entries.into_iter().map(map_tree_entry).collect()),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetTree".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn get_file(
    client_secret_hex: String,
    endpoint_ticket: String,
    repo_path: String,
    git_ref: Option<String>,
    file_path: String,
) -> Result<FfiFileContent, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::GetFile {
                repo_path,
                git_ref,
                file_path,
            },
        )
        .await?
        {
            RpcResponse::File(file) => Ok(map_file(file)),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetFile".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn get_commits(
    client_secret_hex: String,
    endpoint_ticket: String,
    repo_path: String,
    git_ref: Option<String>,
    limit: u32,
) -> Result<Vec<FfiCommitSummary>, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::GetCommits {
                repo_path,
                git_ref,
                limit,
            },
        )
        .await?
        {
            RpcResponse::Commits(commits) => Ok(commits.into_iter().map(map_commit).collect()),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetCommits".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn get_working_diff(
    client_secret_hex: String,
    endpoint_ticket: String,
    repo_path: String,
) -> Result<FfiWorkingDiff, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::GetWorkingDiff { repo_path },
        )
        .await?
        {
            RpcResponse::WorkingDiff(diff) => Ok(map_working_diff(diff)),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetWorkingDiff".into(),
            }),
        }
    })
}

#[uniffi::export]
pub fn get_commit_diff(
    client_secret_hex: String,
    endpoint_ticket: String,
    repo_path: String,
    sha: String,
) -> Result<FfiCommitDiff, LocalClientError> {
    runtime().block_on(async move {
        match perform_rpc(
            &client_secret_hex,
            &endpoint_ticket,
            RpcRequest::GetCommitDiff { repo_path, sha },
        )
        .await?
        {
            RpcResponse::CommitDiff(diff) => Ok(map_commit_diff(diff)),
            _ => Err(LocalClientError::Rpc {
                reason: "Unexpected response to GetCommitDiff".into(),
            }),
        }
    })
}

fn map_host(h: HostInfo) -> FfiHostInfo {
    FfiHostInfo {
        host_name: h.host_name,
        root_name: h.root_name,
        endpoint_id: h.endpoint_id,
        repo_count: h.repo_count,
    }
}

fn map_commit(c: LocalCommitSummary) -> FfiCommitSummary {
    FfiCommitSummary {
        sha: c.sha,
        short_sha: c.short_sha,
        message: c.message,
        author_name: c.author_name,
        committed_at_epoch_s: c.committed_at_epoch_s,
    }
}

fn map_repo(r: LocalRepoSummary) -> FfiRepoSummary {
    FfiRepoSummary {
        name: r.name,
        relative_path: r.relative_path,
        current_branch: r.current_branch,
        is_dirty: r.is_dirty,
        changed_files_count: r.changed_files_count,
        latest_commit: r.latest_commit.map(map_commit),
    }
}

fn map_directory_entry(d: LocalDirectoryEntry) -> FfiDirectoryEntry {
    FfiDirectoryEntry {
        name: d.name,
        relative_path: d.relative_path,
        is_git_repo: d.is_git_repo,
    }
}

fn map_browse_result(b: LocalBrowseResult) -> FfiBrowseResult {
    FfiBrowseResult {
        root_display_name: b.root_display_name,
        current_relative_path: b.current_relative_path,
        current_is_git_repo: b.current_is_git_repo,
        entries: b.entries.into_iter().map(map_directory_entry).collect(),
    }
}

fn map_tree_entry(t: LocalTreeEntry) -> FfiTreeEntry {
    FfiTreeEntry {
        name: t.name,
        path: t.path,
        kind: match t.kind {
            LocalTreeEntryKind::File => FfiTreeEntryKind::File,
            LocalTreeEntryKind::Directory => FfiTreeEntryKind::Directory,
            LocalTreeEntryKind::Submodule => FfiTreeEntryKind::Submodule,
        },
        size: t.size,
        sha: t.sha,
    }
}

fn map_file(f: LocalFileContent) -> FfiFileContent {
    FfiFileContent {
        name: f.name,
        path: f.path,
        size: f.size,
        sha: f.sha,
        text: f.text,
        is_binary: f.is_binary,
        truncated: f.truncated,
    }
}

fn map_diff_file(d: LocalDiffFile) -> FfiDiffFile {
    FfiDiffFile {
        path: d.path,
        previous_path: d.previous_path,
        status: match d.status {
            LocalDiffStatus::Added => FfiDiffStatus::Added,
            LocalDiffStatus::Modified => FfiDiffStatus::Modified,
            LocalDiffStatus::Deleted => FfiDiffStatus::Deleted,
            LocalDiffStatus::Renamed => FfiDiffStatus::Renamed,
            LocalDiffStatus::Untracked => FfiDiffStatus::Untracked,
        },
        additions: d.additions,
        deletions: d.deletions,
        patch: d.patch,
    }
}

fn map_working_diff(w: LocalWorkingDiff) -> FfiWorkingDiff {
    FfiWorkingDiff {
        branch: w.branch,
        files: w.files.into_iter().map(map_diff_file).collect(),
    }
}

fn map_commit_diff(c: LocalCommitDiff) -> FfiCommitDiff {
    FfiCommitDiff {
        commit: map_commit(c.commit),
        files: c.files.into_iter().map(map_diff_file).collect(),
    }
}
