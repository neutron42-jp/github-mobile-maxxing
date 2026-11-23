use anyhow::{Context, Result};
use iroh::{EndpointId, SecretKey};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

pub fn now_epoch_s() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

pub fn default_config_dir() -> Result<PathBuf> {
    let base = dirs::config_dir().context("Unable to determine OS config directory")?;
    let dir = base.join("github-mobile-maxxing-local");
    fs::create_dir_all(&dir)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(&dir, fs::Permissions::from_mode(0o700));
    }
    Ok(dir)
}

fn write_secure_file(path: &Path, contents: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = path.with_extension("tmp");
    fs::write(&tmp, contents)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&tmp, fs::Permissions::from_mode(0o600))?;
    }
    fs::rename(&tmp, path)?;
    Ok(())
}

pub fn load_or_create_host_secret_key(config_dir: &Path) -> Result<SecretKey> {
    let key_path = config_dir.join("host.key");
    if key_path.exists() {
        let raw = fs::read_to_string(&key_path)
            .with_context(|| format!("Failed reading {}", key_path.display()))?;
        let bytes = hex::decode(raw.trim()).context("Corrupt host.key hex")?;
        if bytes.len() != 32 {
            anyhow::bail!("host.key must be 32 bytes");
        }
        let arr: [u8; 32] = bytes.try_into().unwrap();
        return Ok(SecretKey::from_bytes(&arr));
    }
    let sk = SecretKey::generate();
    write_secure_file(&key_path, hex::encode(sk.to_bytes()).as_bytes())?;
    Ok(sk)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuthorizedPeer {
    pub endpoint_id: String,
    pub client_name: String,
    pub paired_at_epoch_s: u64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
struct AuthorizedPeersFile {
    peers: Vec<AuthorizedPeer>,
}

pub struct PeerAuthManager {
    peers_path: PathBuf,
    tokens_path: PathBuf,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct PendingTokenEntry {
    token: String,
    expires_at_epoch_s: u64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
struct PendingTokensFile {
    tokens: Vec<PendingTokenEntry>,
}

impl PeerAuthManager {
    pub fn new(config_dir: &Path) -> Self {
        Self {
            peers_path: config_dir.join("authorized_peers.json"),
            tokens_path: config_dir.join("pending_tokens.json"),
        }
    }

    pub fn list_peers(&self) -> Result<Vec<AuthorizedPeer>> {
        if !self.peers_path.exists() {
            return Ok(Vec::new());
        }
        let raw = fs::read_to_string(&self.peers_path)?;
        let parsed: AuthorizedPeersFile = serde_json::from_str(&raw).unwrap_or_default();
        Ok(parsed.peers)
    }

    pub fn is_authorized(&self, endpoint_id: &EndpointId) -> bool {
        let needle = endpoint_id.to_string();
        self.list_peers()
            .map(|peers| peers.iter().any(|p| p.endpoint_id == needle))
            .unwrap_or(false)
    }

    pub fn authorize_peer(&self, endpoint_id: &EndpointId, client_name: &str) -> Result<()> {
        let id_str = endpoint_id.to_string();
        let mut peers = self.list_peers()?;
        let clean_name = if client_name.trim().is_empty() {
            "Mobile Client".to_string()
        } else {
            client_name.trim().chars().take(64).collect()
        };
        if let Some(existing) = peers.iter_mut().find(|p| p.endpoint_id == id_str) {
            existing.client_name = clean_name;
            existing.paired_at_epoch_s = now_epoch_s();
        } else {
            peers.push(AuthorizedPeer {
                endpoint_id: id_str,
                client_name: clean_name,
                paired_at_epoch_s: now_epoch_s(),
            });
        }
        let encoded = serde_json::to_vec_pretty(&AuthorizedPeersFile { peers })?;
        write_secure_file(&self.peers_path, &encoded)
    }

    pub fn revoke_peer(&self, endpoint_id: &str) -> Result<bool> {
        let mut peers = self.list_peers()?;
        let before = peers.len();
        peers.retain(|p| p.endpoint_id != endpoint_id.trim());
        let removed = peers.len() < before;
        if removed {
            let encoded = serde_json::to_vec_pretty(&AuthorizedPeersFile { peers })?;
            write_secure_file(&self.peers_path, &encoded)?;
        }
        Ok(removed)
    }

    pub fn create_pairing_token(&self, ttl_secs: u64) -> Result<(String, u64)> {
        let mut raw_bytes = [0u8; 24];
        rand::rng().fill_bytes(&mut raw_bytes);
        let token = hex::encode(raw_bytes);
        let expires_at = now_epoch_s().saturating_add(ttl_secs.max(30));

        let now = now_epoch_s();
        let mut entries = self.load_tokens()?;
        entries.retain(|e| e.expires_at_epoch_s > now);
        entries.push(PendingTokenEntry {
            token: token.clone(),
            expires_at_epoch_s: expires_at,
        });
        let encoded = serde_json::to_vec_pretty(&PendingTokensFile { tokens: entries })?;
        write_secure_file(&self.tokens_path, &encoded)?;
        Ok((token, expires_at))
    }

    /// Validates a one-time pairing token and consumes it immediately so it cannot be reused.
    pub fn consume_pairing_token(&self, candidate: &str) -> Result<bool> {
        let now = now_epoch_s();
        let mut entries = self.load_tokens()?;
        let mut matched_idx = None;
        for (i, entry) in entries.iter().enumerate() {
            if entry.expires_at_epoch_s > now && constant_time_eq(entry.token.as_bytes(), candidate.trim().as_bytes()) {
                matched_idx = Some(i);
            }
        }
        let valid = if let Some(idx) = matched_idx {
            entries.remove(idx);
            true
        } else {
            false
        };
        entries.retain(|e| e.expires_at_epoch_s > now);
        let encoded = serde_json::to_vec_pretty(&PendingTokensFile { tokens: entries })?;
        write_secure_file(&self.tokens_path, &encoded)?;
        Ok(valid)
    }

    fn load_tokens(&self) -> Result<Vec<PendingTokenEntry>> {
        if !self.tokens_path.exists() {
            return Ok(Vec::new());
        }
        let raw = fs::read_to_string(&self.tokens_path)?;
        let parsed: PendingTokensFile = serde_json::from_str(&raw).unwrap_or_default();
        Ok(parsed.tokens)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeEndpointState {
    pub pid: u32,
    pub ticket: String,
    pub host_name: String,
    pub root_name: String,
    pub updated_at_epoch_s: u64,
}

pub fn save_runtime_endpoint(config_dir: &Path, state: &RuntimeEndpointState) -> Result<()> {
    let encoded = serde_json::to_vec_pretty(state)?;
    write_secure_file(&config_dir.join("runtime_endpoint.json"), &encoded)
}

pub fn load_live_runtime_endpoint(config_dir: &Path) -> Option<RuntimeEndpointState> {
    let path = config_dir.join("runtime_endpoint.json");
    let raw = fs::read_to_string(path).ok()?;
    let state: RuntimeEndpointState = serde_json::from_str(&raw).ok()?;
    // Check PID existence on Linux/Unix via /proc/<pid> or conservatively age <= 1 day
    #[cfg(target_os = "linux")]
    if !Path::new(&format!("/proc/{}", state.pid)).exists() {
        return None;
    }
    Some(state)
}

pub fn clear_runtime_endpoint(config_dir: &Path) {
    let _ = fs::remove_file(config_dir.join("runtime_endpoint.json"));
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (&x, &y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}

#[allow(dead_code)]
pub fn host_display_name() -> String {
    std::env::var("HOSTNAME")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .or_else(|| fs::read_to_string("/etc/hostname").ok())
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "Local PC".to_string())
}

#[allow(dead_code)]
fn _unused_map(_: HashMap<String, String>) {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pairing_token_is_one_time_and_peer_whitelist_persists() {
        let tmp = tempfile::tempdir().unwrap();
        let mgr = PeerAuthManager::new(tmp.path());

        let (tok, _exp) = mgr.create_pairing_token(300).unwrap();
        assert!(mgr.consume_pairing_token(&tok).unwrap());
        // Reusing the same token must fail immediately.
        assert!(!mgr.consume_pairing_token(&tok).unwrap());

        let peer_sk = SecretKey::generate();
        let peer_id = peer_sk.public();
        assert!(!mgr.is_authorized(&peer_id));

        mgr.authorize_peer(&peer_id, "Pixel 9").unwrap();
        assert!(mgr.is_authorized(&peer_id));
        assert_eq!(mgr.list_peers().unwrap().len(), 1);

        assert!(mgr.revoke_peer(&peer_id.to_string()).unwrap());
        assert!(!mgr.is_authorized(&peer_id));
    }
}
