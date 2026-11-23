mod git_reader;
mod storage;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use git_reader::GitReadError;
use iroh::endpoint::{presets, Connection};
use iroh::Endpoint;
use iroh_tickets::endpoint::EndpointTicket;
use local_protocol::{
    read_frame, write_frame, HostInfo, PairingPayload, RpcErrorCode, RpcRequest, RpcResponse,
    LOCAL_ALPN,
};
use qrcode::render::unicode;
use qrcode::QrCode;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use storage::{
    clear_runtime_endpoint, default_config_dir, host_display_name, load_live_runtime_endpoint,
    load_or_create_host_secret_key, now_epoch_s, save_runtime_endpoint, PeerAuthManager,
    RuntimeEndpointState,
};
use tokio::io::{AsyncBufReadExt, BufReader};

#[derive(Parser, Debug)]
#[command(
    name = "local-cli",
    about = "iroh P2P/E2EE host exposing local Git repositories to github-mobile-maxxing"
)]
struct Cli {
    /// Root directory exposed to clients (defaults to your OS home directory)
    #[arg(long, global = true)]
    root: Option<PathBuf>,

    /// Optional custom config directory (defaults to OS standard config dir)
    #[arg(long, global = true)]
    config_dir: Option<PathBuf>,

    #[command(subcommand)]
    command: Option<Commands>,
}

#[derive(Subcommand, Debug)]
enum Commands {
    /// Start the iroh P2P daemon, display pairing QR/code, and serve local repositories
    Serve {
        /// Pairing token lifetime in seconds
        #[arg(long, default_value_t = 600)]
        ttl_secs: u64,
    },
    /// Generate a fresh pairing QR code and one-time text code
    Pair {
        /// Pairing token lifetime in seconds
        #[arg(long, default_value_t = 600)]
        ttl_secs: u64,
    },
    /// Manage authorized client peers
    Peers {
        #[command(subcommand)]
        action: PeersAction,
    },
}

#[derive(Subcommand, Debug)]
enum PeersAction {
    /// List authorized mobile clients
    List,
    /// Revoke an authorized client EndpointId
    Revoke { endpoint_id: String },
}

#[derive(Clone)]
struct ServerState {
    canon_root: PathBuf,
    root_name: String,
    host_name: String,
    endpoint_id: String,
    auth: Arc<PeerAuthManager>,
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("warn")),
        )
        .init();

    let cli = Cli::parse();
    let root_dir = cli.root.unwrap_or_else(default_root_dir);
    let config_dir = match cli.config_dir {
        Some(dir) => {
            fs::create_dir_all(&dir)?;
            dir
        }
        None => default_config_dir()?,
    };

    match cli.command {
        None => run_serve(&root_dir, &config_dir, 600).await,
        Some(Commands::Serve { ttl_secs }) => run_serve(&root_dir, &config_dir, ttl_secs).await,
        Some(Commands::Pair { ttl_secs }) => run_pair(&root_dir, &config_dir, ttl_secs).await,
        Some(Commands::Peers { action }) => run_peers(&config_dir, action),
    }
}

fn default_root_dir() -> PathBuf {
    dirs::home_dir().unwrap_or_else(|| PathBuf::from("."))
}

fn run_peers(config_dir: &Path, action: PeersAction) -> Result<()> {
    let auth = PeerAuthManager::new(config_dir);
    match action {
        PeersAction::List => {
            let peers = auth.list_peers()?;
            if peers.is_empty() {
                println!("No authorized peers registered yet.");
            } else {
                for p in peers {
                    println!(
                        "{}  name={}  paired_at={}",
                        p.endpoint_id, p.client_name, p.paired_at_epoch_s
                    );
                }
            }
        }
        PeersAction::Revoke { endpoint_id } => {
            if auth.revoke_peer(&endpoint_id)? {
                println!("Revoked peer {endpoint_id}");
            } else {
                println!("Peer not found: {endpoint_id}");
            }
        }
    }
    Ok(())
}

async fn run_pair(root: &Path, config_dir: &Path, ttl_secs: u64) -> Result<()> {
    let auth = Arc::new(PeerAuthManager::new(config_dir));
    if let Some(live) = load_live_runtime_endpoint(config_dir) {
        let (token, expires_at) = auth.create_pairing_token(ttl_secs)?;
        let payload = PairingPayload {
            version: 1,
            ticket: live.ticket,
            token,
            host_name: live.host_name,
            root_name: live.root_name,
            expires_at_epoch_s: expires_at,
        };
        print_pairing_banner(&payload)?;
        println!("(Attached to running serve process PID {}; scan or paste while serve stays running.)", live.pid);
        return Ok(());
    }

    // Standalone one-shot pair: start endpoint, wait for one client to pair, then exit.
    let canon_root = fs::canonicalize(root)
        .with_context(|| format!("Root directory not accessible: {}", root.display()))?;
    let secret_key = load_or_create_host_secret_key(config_dir)?;
    let endpoint = Endpoint::builder(presets::N0)
        .secret_key(secret_key)
        .alpns(vec![LOCAL_ALPN.to_vec()])
        .bind()
        .await?;
    endpoint.online().await;

    let ticket = EndpointTicket::new(endpoint.addr()).to_string();
    let (token, expires_at) = auth.create_pairing_token(ttl_secs)?;
    let root_name = root_display_name(&canon_root);
    let host_name = host_display_name();
    let payload = PairingPayload {
        version: 1,
        ticket,
        token,
        host_name: host_name.clone(),
        root_name: root_name.clone(),
        expires_at_epoch_s: expires_at,
    };
    print_pairing_banner(&payload)?;
    println!("Waiting for one device to pair (or Ctrl+C to cancel)...");

    let state = ServerState {
        canon_root,
        root_name,
        host_name,
        endpoint_id: endpoint.id().to_string(),
        auth: auth.clone(),
    };

    while let Some(incoming) = endpoint.accept().await {
        match incoming.await {
            Ok(conn) => {
                let remote = conn.remote_id();
                let before = auth.is_authorized(&remote);
                let _ = handle_connection(conn, state.clone()).await;
                if !before && auth.is_authorized(&remote) {
                    println!("Paired successfully with {remote}");
                    break;
                }
            }
            Err(e) => tracing::warn!("Incoming connection failed: {e}"),
        }
    }
    endpoint.close().await;
    Ok(())
}

async fn run_serve(root: &Path, config_dir: &Path, ttl_secs: u64) -> Result<()> {
    let canon_root = fs::canonicalize(root)
        .with_context(|| format!("Root directory not accessible: {}", root.display()))?;
    if !canon_root.is_dir() {
        anyhow::bail!("--root must point to a directory: {}", canon_root.display());
    }

    let secret_key = load_or_create_host_secret_key(config_dir)?;
    let auth = Arc::new(PeerAuthManager::new(config_dir));

    let endpoint = Endpoint::builder(presets::N0)
        .secret_key(secret_key)
        .alpns(vec![LOCAL_ALPN.to_vec()])
        .bind()
        .await?;
    endpoint.online().await;

    let ticket = EndpointTicket::new(endpoint.addr()).to_string();
    let root_name = root_display_name(&canon_root);
    let host_name = host_display_name();

    let runtime_state = RuntimeEndpointState {
        pid: std::process::id(),
        ticket: ticket.clone(),
        host_name: host_name.clone(),
        root_name: root_name.clone(),
        updated_at_epoch_s: now_epoch_s(),
    };
    save_runtime_endpoint(config_dir, &runtime_state)?;

    let (token, expires_at) = auth.create_pairing_token(ttl_secs)?;
    let payload = PairingPayload {
        version: 1,
        ticket: ticket.clone(),
        token,
        host_name: host_name.clone(),
        root_name: root_name.clone(),
        expires_at_epoch_s: expires_at,
    };

    println!("============================================================");
    println!("github-mobile-maxxing Local Host Ready (iroh P2P + E2EE)");
    println!("EndpointId : {}", endpoint.id());
    println!("Exposed Root: {} ({})", canon_root.display(), root_name);
    println!("Config Dir  : {}", config_dir.display());
    println!("Authorized  : {} peer(s)", auth.list_peers()?.len());
    println!("============================================================");
    print_pairing_banner(&payload)?;
    println!("Press 'p' + Enter anytime to issue a fresh pairing code. Ctrl+C to stop.\n");

    let state = ServerState {
        canon_root,
        root_name,
        host_name,
        endpoint_id: endpoint.id().to_string(),
        auth: auth.clone(),
    };

    // Interactive stdin listener for generating fresh pairing QR codes on demand
    let ticket_for_stdin = ticket.clone();
    let auth_for_stdin = auth.clone();
    let host_for_stdin = state.host_name.clone();
    let root_for_stdin = state.root_name.clone();
    tokio::spawn(async move {
        let mut lines = BufReader::new(tokio::io::stdin()).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if line.trim().eq_ignore_ascii_case("p") || line.trim().is_empty() {
                if let Ok((new_tok, new_exp)) = auth_for_stdin.create_pairing_token(ttl_secs) {
                    let p = PairingPayload {
                        version: 1,
                        ticket: ticket_for_stdin.clone(),
                        token: new_tok,
                        host_name: host_for_stdin.clone(),
                        root_name: root_for_stdin.clone(),
                        expires_at_epoch_s: new_exp,
                    };
                    let _ = print_pairing_banner(&p);
                }
            }
        }
    });

    loop {
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {
                println!("\nShutting down...");
                break;
            }
            accepted = endpoint.accept() => {
                let Some(incoming) = accepted else { break };
                let st = state.clone();
                tokio::spawn(async move {
                    match incoming.await {
                        Ok(conn) => {
                            if let Err(e) = handle_connection(conn, st).await {
                                tracing::debug!("Connection closed: {e}");
                            }
                        }
                        Err(e) => tracing::debug!("Incoming handshake failed: {e}"),
                    }
                });
            }
        }
    }

    clear_runtime_endpoint(config_dir);
    endpoint.close().await;
    Ok(())
}

async fn handle_connection(conn: Connection, state: ServerState) -> Result<()> {
    let remote_id = conn.remote_id();
    let mut authorized = state.auth.is_authorized(&remote_id);

    loop {
        let (mut send, mut recv) = match conn.accept_bi().await {
            Ok(streams) => streams,
            Err(_) => break,
        };

        let req: RpcRequest = match read_frame(&mut recv).await {
            Ok(r) => r,
            Err(_) => break,
        };

        let response = match req {
            RpcRequest::Pair { token, client_name } => {
                if authorized {
                    RpcResponse::Paired(build_host_info(&state))
                } else {
                    match state.auth.consume_pairing_token(&token) {
                        Ok(true) => {
                            state.auth.authorize_peer(&remote_id, &client_name)?;
                            authorized = true;
                            println!("Paired new peer: {} ({})", remote_id, client_name.trim());
                            RpcResponse::Paired(build_host_info(&state))
                        }
                        _ => {
                            let err = RpcResponse::Error {
                                code: RpcErrorCode::Unauthorized,
                                message: "Invalid or expired pairing token".into(),
                            };
                            let _ = write_frame(&mut send, &err).await;
                            let _ = send.finish();
                            conn.close(1u32.into(), b"unauthorized");
                            return Ok(());
                        }
                    }
                }
            }
            other => {
                if !authorized {
                    let err = RpcResponse::Error {
                        code: RpcErrorCode::Unauthorized,
                        message: "Peer is not authorized. Pair this device first.".into(),
                    };
                    let _ = write_frame(&mut send, &err).await;
                    let _ = send.finish();
                    conn.close(1u32.into(), b"unauthorized");
                    return Ok(());
                }
                dispatch_authorized_rpc(other, &state).await
            }
        };

        write_frame(&mut send, &response).await?;
        let _ = send.finish();
    }
    Ok(())
}

async fn dispatch_authorized_rpc(req: RpcRequest, state: &ServerState) -> RpcResponse {
    let st = state.clone();
    tokio::task::spawn_blocking(move || match req {
        RpcRequest::Pair { .. } => unreachable!(),
        RpcRequest::Ping => RpcResponse::Pong(build_host_info(&st)),
        RpcRequest::GetHostInfo => RpcResponse::HostInfo(build_host_info(&st)),
        RpcRequest::ListRepos => match git_reader::discover_repos(&st.canon_root) {
            Ok(repos) => RpcResponse::Repos(repos),
            Err(e) => map_git_error(e),
        },
        RpcRequest::BrowseDirectories { dir_path } => {
            match git_reader::browse_directories(&st.canon_root, &dir_path) {
                Ok(browse) => RpcResponse::Directories(browse),
                Err(e) => map_git_error(e),
            }
        }
        RpcRequest::GetRepoSummaries { repo_paths } => {
            let repos = git_reader::summarize_repo_paths(&st.canon_root, &repo_paths);
            RpcResponse::Repos(repos)
        }
        RpcRequest::GetTree {
            repo_path,
            git_ref,
            dir_path,
        } => match git_reader::get_tree(&st.canon_root, &repo_path, git_ref.as_deref(), &dir_path) {
            Ok(entries) => RpcResponse::Tree(entries),
            Err(e) => map_git_error(e),
        },
        RpcRequest::GetFile {
            repo_path,
            git_ref,
            file_path,
        } => match git_reader::get_file(&st.canon_root, &repo_path, git_ref.as_deref(), &file_path) {
            Ok(file) => RpcResponse::File(file),
            Err(e) => map_git_error(e),
        },
        RpcRequest::GetCommits {
            repo_path,
            git_ref,
            limit,
        } => match git_reader::get_commits(&st.canon_root, &repo_path, git_ref.as_deref(), limit) {
            Ok(commits) => RpcResponse::Commits(commits),
            Err(e) => map_git_error(e),
        },
        RpcRequest::GetWorkingDiff { repo_path } => {
            match git_reader::get_working_diff(&st.canon_root, &repo_path) {
                Ok(diff) => RpcResponse::WorkingDiff(diff),
                Err(e) => map_git_error(e),
            }
        }
        RpcRequest::GetCommitDiff { repo_path, sha } => {
            match git_reader::get_commit_diff(&st.canon_root, &repo_path, &sha) {
                Ok(diff) => RpcResponse::CommitDiff(diff),
                Err(e) => map_git_error(e),
            }
        }
    })
    .await
    .unwrap_or_else(|e| RpcResponse::Error {
        code: RpcErrorCode::Internal,
        message: format!("Worker task panicked: {e}"),
    })
}

fn build_host_info(state: &ServerState) -> HostInfo {
    HostInfo {
        host_name: state.host_name.clone(),
        root_name: state.root_name.clone(),
        endpoint_id: state.endpoint_id.clone(),
        repo_count: 0,
    }
}

fn map_git_error(err: GitReadError) -> RpcResponse {
    let code = match &err {
        GitReadError::InvalidPath(_) => RpcErrorCode::InvalidArgument,
        GitReadError::ForbiddenPath(_) => RpcErrorCode::ForbiddenPath,
        GitReadError::NotFound(_) => RpcErrorCode::NotFound,
        GitReadError::Internal(_) => RpcErrorCode::Internal,
    };
    RpcResponse::Error {
        code,
        message: err.to_string(),
    }
}

fn root_display_name(canon_root: &Path) -> String {
    canon_root
        .file_name()
        .map(|s| s.to_string_lossy().into_owned())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "root".to_string())
}

fn print_pairing_banner(payload: &PairingPayload) -> Result<()> {
    let uri = payload.encode_uri()?;
    let qr = QrCode::new(uri.as_bytes())
        .context("Failed generating pairing QR code")?
        .render::<unicode::Dense1x2>()
        .dark_color(unicode::Dense1x2::Light)
        .light_color(unicode::Dense1x2::Dark)
        .quiet_zone(true)
        .build();

    println!("\n--- Pairing QR Code (scan in Manage -> Local -> Add) ---");
    println!("{qr}");
    println!("--- Copy-Paste Pairing Code (one-time) ---");
    println!("{uri}\n");
    Ok(())
}
