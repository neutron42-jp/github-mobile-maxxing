use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use serde::{Deserialize, Serialize};
use thiserror::Error;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};

pub const LOCAL_ALPN: &[u8] = b"maxxing/github-mobile-local/1";
pub const PAIRING_URI_PREFIX: &str = "githubmaxxing-local://pair?d=";
pub const MAX_RPC_FRAME_BYTES: usize = 8 * 1024 * 1024;

#[derive(Debug, Error)]
pub enum ProtocolError {
    #[error("Invalid pairing code: {0}")]
    InvalidPairingCode(String),
    #[error("Frame too large ({0} bytes)")]
    FrameTooLarge(usize),
    #[error("Unexpected EOF")]
    Eof,
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Serialization error: {0}")]
    Json(#[from] serde_json::Error),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct PairingPayload {
    pub version: u8,
    pub ticket: String,
    pub token: String,
    pub host_name: String,
    pub root_name: String,
    pub expires_at_epoch_s: u64,
}

impl PairingPayload {
    pub fn encode_uri(&self) -> Result<String, ProtocolError> {
        let json_bytes = serde_json::to_vec(self)?;
        let encoded = URL_SAFE_NO_PAD.encode(json_bytes);
        Ok(format!("{PAIRING_URI_PREFIX}{encoded}"))
    }

    pub fn decode_code(raw: &str) -> Result<Self, ProtocolError> {
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            return Err(ProtocolError::InvalidPairingCode("empty code".into()));
        }
        let json_bytes = if let Some(b64) = trimmed.strip_prefix(PAIRING_URI_PREFIX) {
            URL_SAFE_NO_PAD
                .decode(b64.trim())
                .map_err(|e| ProtocolError::InvalidPairingCode(format!("bad base64: {e}")))?
        } else if trimmed.starts_with('{') {
            trimmed.as_bytes().to_vec()
        } else {
            URL_SAFE_NO_PAD
                .decode(trimmed)
                .map_err(|e| ProtocolError::InvalidPairingCode(format!("unrecognized format: {e}")))?
        };
        let payload: Self = serde_json::from_slice(&json_bytes)
            .map_err(|e| ProtocolError::InvalidPairingCode(format!("bad payload json: {e}")))?;
        if payload.ticket.trim().is_empty() || payload.token.trim().is_empty() {
            return Err(ProtocolError::InvalidPairingCode(
                "missing ticket or token".into(),
            ));
        }
        Ok(payload)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct HostInfo {
    pub host_name: String,
    pub root_name: String,
    pub endpoint_id: String,
    pub repo_count: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalCommitSummary {
    pub sha: String,
    pub short_sha: String,
    pub message: String,
    pub author_name: String,
    pub committed_at_epoch_s: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalRepoSummary {
    pub name: String,
    pub relative_path: String,
    pub current_branch: String,
    pub is_dirty: bool,
    pub changed_files_count: u32,
    pub latest_commit: Option<LocalCommitSummary>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum LocalTreeEntryKind {
    File,
    Directory,
    Submodule,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalTreeEntry {
    pub name: String,
    pub path: String,
    pub kind: LocalTreeEntryKind,
    pub size: u64,
    pub sha: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalFileContent {
    pub name: String,
    pub path: String,
    pub size: u64,
    pub sha: String,
    pub text: Option<String>,
    pub is_binary: bool,
    pub truncated: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum LocalDiffStatus {
    Added,
    Modified,
    Deleted,
    Renamed,
    Untracked,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalDiffFile {
    pub path: String,
    pub previous_path: Option<String>,
    pub status: LocalDiffStatus,
    pub additions: u32,
    pub deletions: u32,
    pub patch: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalWorkingDiff {
    pub branch: String,
    pub files: Vec<LocalDiffFile>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalCommitDiff {
    pub commit: LocalCommitSummary,
    pub files: Vec<LocalDiffFile>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalDirectoryEntry {
    pub name: String,
    pub relative_path: String,
    pub is_git_repo: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LocalBrowseResult {
    pub root_display_name: String,
    pub current_relative_path: String,
    pub current_is_git_repo: bool,
    pub entries: Vec<LocalDirectoryEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "method", content = "params", rename_all = "snake_case")]
pub enum RpcRequest {
    Pair {
        token: String,
        client_name: String,
    },
    Ping,
    GetHostInfo,
    ListRepos,
    BrowseDirectories {
        dir_path: String,
    },
    GetRepoSummaries {
        repo_paths: Vec<String>,
    },
    GetTree {
        repo_path: String,
        git_ref: Option<String>,
        dir_path: String,
    },
    GetFile {
        repo_path: String,
        git_ref: Option<String>,
        file_path: String,
    },
    GetCommits {
        repo_path: String,
        git_ref: Option<String>,
        limit: u32,
    },
    GetWorkingDiff {
        repo_path: String,
    },
    GetCommitDiff {
        repo_path: String,
        sha: String,
    },
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum RpcErrorCode {
    Unauthorized,
    InvalidArgument,
    ForbiddenPath,
    NotFound,
    Internal,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "status", content = "data", rename_all = "snake_case")]
pub enum RpcResponse {
    Paired(HostInfo),
    Pong(HostInfo),
    HostInfo(HostInfo),
    Repos(Vec<LocalRepoSummary>),
    Directories(LocalBrowseResult),
    Tree(Vec<LocalTreeEntry>),
    File(LocalFileContent),
    Commits(Vec<LocalCommitSummary>),
    WorkingDiff(LocalWorkingDiff),
    CommitDiff(LocalCommitDiff),
    Error {
        code: RpcErrorCode,
        message: String,
    },
}

pub async fn write_frame<W, T>(writer: &mut W, value: &T) -> Result<(), ProtocolError>
where
    W: AsyncWrite + Unpin,
    T: Serialize,
{
    let payload = serde_json::to_vec(value)?;
    if payload.len() > MAX_RPC_FRAME_BYTES {
        return Err(ProtocolError::FrameTooLarge(payload.len()));
    }
    let len = u32::try_from(payload.len())
        .map_err(|_| ProtocolError::FrameTooLarge(payload.len()))?;
    writer.write_all(&len.to_be_bytes()).await?;
    writer.write_all(&payload).await?;
    writer.flush().await?;
    Ok(())
}

pub async fn read_frame<R, T>(reader: &mut R) -> Result<T, ProtocolError>
where
    R: AsyncRead + Unpin,
    T: for<'de> Deserialize<'de>,
{
    let mut len_buf = [0u8; 4];
    match reader.read_exact(&mut len_buf).await {
        Ok(_) => {}
        Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => return Err(ProtocolError::Eof),
        Err(e) => return Err(ProtocolError::Io(e)),
    }
    let len = u32::from_be_bytes(len_buf) as usize;
    if len > MAX_RPC_FRAME_BYTES {
        return Err(ProtocolError::FrameTooLarge(len));
    }
    let mut buf = vec![0u8; len];
    reader.read_exact(&mut buf).await?;
    Ok(serde_json::from_slice(&buf)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pairing_payload_roundtrip_uri_and_json() {
        let payload = PairingPayload {
            version: 1,
            ticket: "endpointabc123".into(),
            token: "secret-one-time-token".into(),
            host_name: "fedora-workstation".into(),
            root_name: "projects".into(),
            expires_at_epoch_s: 1_900_000_000,
        };
        let uri = payload.encode_uri().unwrap();
        assert!(uri.starts_with(PAIRING_URI_PREFIX));
        assert_eq!(PairingPayload::decode_code(&uri).unwrap(), payload);

        let raw_json = serde_json::to_string(&payload).unwrap();
        assert_eq!(PairingPayload::decode_code(&raw_json).unwrap(), payload);
    }

    #[test]
    fn reject_invalid_pairing_codes() {
        assert!(PairingPayload::decode_code("").is_err());
        assert!(PairingPayload::decode_code("not-valid").is_err());
    }
}
