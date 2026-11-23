use gix::bstr::ByteSlice;
use gix::object::tree::diff::ChangeDetached;
use gix::status::Item as StatusItem;
use local_protocol::{
    LocalBrowseResult, LocalCommitDiff, LocalCommitSummary, LocalDiffFile, LocalDiffStatus,
    LocalDirectoryEntry, LocalFileContent, LocalRepoSummary, LocalTreeEntry, LocalTreeEntryKind,
    LocalWorkingDiff,
};
use similar::TextDiff;
use std::collections::BTreeSet;
use std::fs;
use std::path::{Component, Path, PathBuf};

const MAX_DISCOVERY_DEPTH: usize = 4;
const MAX_FILE_PREVIEW_BYTES: usize = 512 * 1024;
const MAX_PATCH_BYTES: usize = 256 * 1024;

#[derive(Debug, thiserror::Error)]
pub enum GitReadError {
    #[error("Invalid path: {0}")]
    InvalidPath(String),
    #[error("Access denied outside workspace root: {0}")]
    ForbiddenPath(String),
    #[error("Not found: {0}")]
    NotFound(String),
    #[error("Git/IO error: {0}")]
    Internal(String),
}

/// Validates and canonicalizes a relative path inside `canon_root`, rejecting `..`,
/// absolute paths, and symlink escapes outside `canon_root`.
pub fn resolve_safe_path(canon_root: &Path, relative: &str) -> Result<PathBuf, GitReadError> {
    let trimmed = relative.trim();
    if trimmed.is_empty() || trimmed == "." {
        return Ok(canon_root.to_path_buf());
    }
    if trimmed.contains('\0') {
        return Err(GitReadError::InvalidPath("null byte in path".into()));
    }
    let rel_path = Path::new(trimmed);
    if rel_path.is_absolute() {
        return Err(GitReadError::ForbiddenPath("absolute paths are not allowed".into()));
    }
    for comp in rel_path.components() {
        match comp {
            Component::Normal(_) | Component::CurDir => {}
            Component::ParentDir | Component::RootDir | Component::Prefix(_) => {
                return Err(GitReadError::ForbiddenPath(
                    "parent directory traversal is not allowed".into(),
                ));
            }
        }
    }
    let joined = canon_root.join(rel_path);
    let canon_target = fs::canonicalize(&joined)
        .map_err(|_| GitReadError::NotFound(format!("path does not exist: {trimmed}")))?;
    if !canon_target.starts_with(canon_root) {
        return Err(GitReadError::ForbiddenPath(format!(
            "path escapes root directory: {trimmed}"
        )));
    }
    Ok(canon_target)
}

pub fn resolve_safe_repo(canon_root: &Path, repo_rel: &str) -> Result<PathBuf, GitReadError> {
    let repo_dir = resolve_safe_path(canon_root, repo_rel)?;
    if !repo_dir.join(".git").exists() {
        return Err(GitReadError::NotFound(format!(
            "not a git repository: {repo_rel}"
        )));
    }
    Ok(repo_dir)
}

/// Checks if a relative path inside a repository is syntactically safe; if the path
/// exists on disk, canonicalizes it and enforces containment within both `canon_repo`
/// and `canon_root` (preventing symlink escapes).
pub fn safe_existing_worktree_file(
    canon_root: &Path,
    canon_repo: &Path,
    rel_file: &str,
) -> Result<Option<PathBuf>, GitReadError> {
    if rel_file.is_empty() || rel_file.contains('\0') {
        return Err(GitReadError::InvalidPath("invalid file path".into()));
    }
    let rel_path = Path::new(rel_file);
    if rel_path.is_absolute() {
        return Err(GitReadError::ForbiddenPath("absolute file path".into()));
    }
    for comp in rel_path.components() {
        match comp {
            Component::Normal(_) | Component::CurDir => {}
            _ => {
                return Err(GitReadError::ForbiddenPath("illegal path component".into()));
            }
        }
    }
    let candidate = canon_repo.join(rel_path);
    if fs::symlink_metadata(&candidate).is_err() {
        return Ok(None);
    }
    let canon_file = fs::canonicalize(&candidate)
        .map_err(|e| GitReadError::Internal(format!("canonicalize failed: {e}")))?;
    if !canon_file.starts_with(canon_repo) || !canon_file.starts_with(canon_root) {
        return Err(GitReadError::ForbiddenPath(format!(
            "symlink escapes repository: {rel_file}"
        )));
    }
    Ok(Some(canon_file))
}

pub fn discover_repos(canon_root: &Path) -> Result<Vec<LocalRepoSummary>, GitReadError> {
    let mut repo_dirs = Vec::new();
    collect_repo_dirs(canon_root, canon_root, 0, &mut repo_dirs);
    repo_dirs.sort();

    let mut summaries = Vec::with_capacity(repo_dirs.len());
    for dir in repo_dirs {
        if let Ok(summary) = read_repo_summary(canon_root, &dir) {
            summaries.push(summary);
        }
    }
    Ok(summaries)
}

pub fn browse_directories(
    canon_root: &Path,
    dir_path: &str,
) -> Result<LocalBrowseResult, GitReadError> {
    let canon_dir = resolve_safe_path(canon_root, dir_path)?;
    if !canon_dir.is_dir() {
        return Err(GitReadError::InvalidPath(format!(
            "not a directory: {dir_path}"
        )));
    }
    let current_relative_path = if canon_dir == canon_root {
        String::new()
    } else {
        canon_dir
            .strip_prefix(canon_root)
            .unwrap_or(&canon_dir)
            .to_string_lossy()
            .replace('\\', "/")
    };
    let current_is_git_repo = canon_dir.join(".git").exists();
    let root_display_name = canon_root
        .file_name()
        .map(|s| s.to_string_lossy().into_owned())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "Home".to_string());

    let read_entries = fs::read_dir(&canon_dir)
        .map_err(|e| GitReadError::Internal(format!("read_dir failed: {e}")))?;

    let mut entries = Vec::new();
    for item in read_entries.flatten() {
        let name = item.file_name().to_string_lossy().into_owned();
        if name.starts_with('.') {
            continue;
        }
        let Ok(canon_sub) = fs::canonicalize(item.path()) else {
            continue;
        };
        if !canon_sub.is_dir() || !canon_sub.starts_with(canon_root) {
            continue;
        }
        let relative_path = if canon_sub == canon_root {
            ".".to_string()
        } else {
            canon_sub
                .strip_prefix(canon_root)
                .unwrap_or(&canon_sub)
                .to_string_lossy()
                .replace('\\', "/")
        };
        let is_git_repo = canon_sub.join(".git").exists();
        entries.push(LocalDirectoryEntry {
            name,
            relative_path,
            is_git_repo,
        });
    }

    entries.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));

    Ok(LocalBrowseResult {
        root_display_name,
        current_relative_path,
        current_is_git_repo,
        entries,
    })
}

pub fn summarize_repo_paths(canon_root: &Path, repo_paths: &[String]) -> Vec<LocalRepoSummary> {
    let mut seen = BTreeSet::new();
    let mut out = Vec::new();
    for raw in repo_paths {
        let Ok(canon_repo) = resolve_safe_repo(canon_root, raw) else {
            continue;
        };
        if !seen.insert(canon_repo.clone()) {
            continue;
        }
        if let Ok(summary) = read_repo_summary(canon_root, &canon_repo) {
            out.push(summary);
        }
    }
    out
}

fn collect_repo_dirs(canon_root: &Path, dir: &Path, depth: usize, out: &mut Vec<PathBuf>) {
    if depth > MAX_DISCOVERY_DEPTH {
        return;
    }
    let Ok(canon_dir) = fs::canonicalize(dir) else {
        return;
    };
    if !canon_dir.starts_with(canon_root) {
        return;
    }
    if canon_dir.join(".git").exists() {
        out.push(canon_dir);
        return;
    }
    if depth == MAX_DISCOVERY_DEPTH {
        return;
    }
    let Ok(entries) = fs::read_dir(&canon_dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        let name = entry.file_name();
        let name_str = name.to_string_lossy();
        if name_str.starts_with('.')
            || matches!(
                name_str.as_ref(),
                "target" | "build" | "node_modules" | "Pods" | "dist"
            )
        {
            continue;
        }
        collect_repo_dirs(canon_root, &path, depth + 1, out);
    }
}

fn open_gix_repo(repo_dir: &Path) -> Result<gix::Repository, GitReadError> {
    gix::open(repo_dir).map_err(|e| GitReadError::Internal(format!("gix::open failed: {e}")))
}

fn current_branch_name(repo: &gix::Repository) -> String {
    match repo.head_name() {
        Ok(Some(name)) => name.shorten().to_string(),
        _ => repo
            .head_id()
            .ok()
            .map(|id| id.to_hex_with_len(7).to_string())
            .unwrap_or_else(|| "HEAD".to_string()),
    }
}

fn commit_to_summary(commit: &gix::Commit<'_>) -> LocalCommitSummary {
    let sha = commit.id().to_string();
    let short_sha = commit.id().to_hex_with_len(7).to_string();
    let message = commit
        .message()
        .map(|m| m.summary().to_string())
        .unwrap_or_else(|_| commit.message_raw_sloppy().to_str_lossy().trim().to_string());
    let (author_name, committed_at_epoch_s) = match commit.author() {
        Ok(sig) => (
            sig.name.to_str_lossy().into_owned(),
            sig.time().map(|t| t.seconds).unwrap_or(0),
        ),
        Err(_) => ("unknown".to_string(), 0),
    };
    LocalCommitSummary {
        sha,
        short_sha,
        message,
        author_name,
        committed_at_epoch_s,
    }
}

fn read_repo_summary(canon_root: &Path, canon_repo: &Path) -> Result<LocalRepoSummary, GitReadError> {
    let repo = open_gix_repo(canon_repo)?;
    let relative_path = if canon_repo == canon_root {
        ".".to_string()
    } else {
        canon_repo
            .strip_prefix(canon_root)
            .unwrap_or(canon_repo)
            .to_string_lossy()
            .replace('\\', "/")
    };
    let name = canon_repo
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .filter(|n| !n.is_empty())
        .unwrap_or_else(|| relative_path.clone());
    let current_branch = current_branch_name(&repo);
    let latest_commit = repo.head_commit().ok().map(|c| commit_to_summary(&c));

    let changed_files_count = count_changed_paths(&repo).unwrap_or(0);
    let is_dirty = changed_files_count > 0 || repo.is_dirty().unwrap_or(false);

    Ok(LocalRepoSummary {
        name,
        relative_path,
        current_branch,
        is_dirty,
        changed_files_count,
        latest_commit,
    })
}

fn count_changed_paths(repo: &gix::Repository) -> Result<u32, GitReadError> {
    let iter = repo
        .status(gix::progress::Discard)
        .map_err(|e| GitReadError::Internal(format!("status failed: {e}")))?
        .into_iter(Vec::new())
        .map_err(|e| GitReadError::Internal(format!("status iter failed: {e}")))?;

    let mut seen = BTreeSet::new();
    for item in iter.flatten() {
        seen.insert(item.location().to_str_lossy().into_owned());
    }
    Ok(seen.len() as u32)
}

fn resolve_target_tree<'a>(
    repo: &'a gix::Repository,
    git_ref: Option<&str>,
) -> Result<gix::Tree<'a>, GitReadError> {
    let spec = git_ref
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("HEAD");
    let id = repo
        .rev_parse_single(spec)
        .map_err(|e| GitReadError::NotFound(format!("cannot resolve ref '{spec}': {e}")))?;
    let object = id
        .object()
        .map_err(|e| GitReadError::NotFound(format!("cannot load object '{spec}': {e}")))?;
    object
        .peel_to_tree()
        .map_err(|e| GitReadError::NotFound(format!("'{spec}' does not peel to a tree: {e}")))
}

pub fn get_tree(
    canon_root: &Path,
    repo_path: &str,
    git_ref: Option<&str>,
    dir_path: &str,
) -> Result<Vec<LocalTreeEntry>, GitReadError> {
    let canon_repo = resolve_safe_repo(canon_root, repo_path)?;
    let repo = open_gix_repo(&canon_repo)?;
    let root_tree = match resolve_target_tree(&repo, git_ref) {
        Ok(t) => t,
        Err(_) if git_ref.is_none() => repo.empty_tree(),
        Err(e) => return Err(e),
    };

    let clean_dir = dir_path.trim().trim_matches('/');
    let target_tree = if clean_dir.is_empty() {
        root_tree
    } else {
        let entry = root_tree
            .lookup_entry_by_path(clean_dir)
            .map_err(|e| GitReadError::Internal(format!("tree lookup error: {e}")))?
            .ok_or_else(|| GitReadError::NotFound(format!("directory not found: {clean_dir}")))?;
        if !entry.mode().is_tree() {
            return Err(GitReadError::InvalidPath(format!(
                "path is not a directory: {clean_dir}"
            )));
        }
        entry
            .object()
            .map_err(|e| GitReadError::Internal(format!("tree object error: {e}")))?
            .peel_to_tree()
            .map_err(|e| GitReadError::Internal(format!("peel tree error: {e}")))?
    };

    let mut entries = Vec::new();
    for entry_res in target_tree.iter() {
        let entry = entry_res.map_err(|e| GitReadError::Internal(format!("tree entry: {e}")))?;
        let name = entry.filename().to_str_lossy().into_owned();
        let full_path = if clean_dir.is_empty() {
            name.clone()
        } else {
            format!("{clean_dir}/{name}")
        };
        let mode = entry.mode();
        let (kind, size) = if mode.is_tree() {
            (LocalTreeEntryKind::Directory, 0)
        } else if mode.is_commit() {
            (LocalTreeEntryKind::Submodule, 0)
        } else {
            let blob_size = entry
                .object()
                .ok()
                .map(|o| o.data.len() as u64)
                .unwrap_or(0);
            (LocalTreeEntryKind::File, blob_size)
        };
        entries.push(LocalTreeEntry {
            name,
            path: full_path,
            kind,
            size,
            sha: entry.object_id().to_string(),
        });
    }

    entries.sort_by(|a, b| {
        let rank = |k: &LocalTreeEntryKind| match k {
            LocalTreeEntryKind::Directory => 0,
            LocalTreeEntryKind::Submodule => 1,
            LocalTreeEntryKind::File => 2,
        };
        rank(&a.kind)
            .cmp(&rank(&b.kind))
            .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });
    Ok(entries)
}

pub fn get_file(
    canon_root: &Path,
    repo_path: &str,
    git_ref: Option<&str>,
    file_path: &str,
) -> Result<LocalFileContent, GitReadError> {
    let clean_file = file_path.trim().trim_matches('/');
    if clean_file.is_empty() {
        return Err(GitReadError::InvalidPath("file_path is empty".into()));
    }
    let canon_repo = resolve_safe_repo(canon_root, repo_path)?;
    let repo = open_gix_repo(&canon_repo)?;
    let root_tree = resolve_target_tree(&repo, git_ref)?;
    let entry = root_tree
        .lookup_entry_by_path(clean_file)
        .map_err(|e| GitReadError::Internal(format!("lookup failed: {e}")))?
        .ok_or_else(|| GitReadError::NotFound(format!("file not found: {clean_file}")))?;
    if !entry.mode().is_blob_or_symlink() {
        return Err(GitReadError::InvalidPath(format!(
            "path is not a file: {clean_file}"
        )));
    }
    let sha = entry.object_id().to_string();
    let obj = entry
        .object()
        .map_err(|e| GitReadError::Internal(format!("object read failed: {e}")))?;
    let total_size = obj.data.len() as u64;
    let name = Path::new(clean_file)
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| clean_file.to_string());
    let truncated = obj.data.len() > MAX_FILE_PREVIEW_BYTES;
    let slice = if truncated {
        &obj.data[..MAX_FILE_PREVIEW_BYTES]
    } else {
        obj.data.as_slice()
    };
    let is_binary = slice.contains(&0) || std::str::from_utf8(slice).is_err();
    let text = if is_binary {
        None
    } else {
        Some(String::from_utf8_lossy(slice).into_owned())
    };
    Ok(LocalFileContent {
        name,
        path: clean_file.to_string(),
        size: total_size,
        sha,
        text,
        is_binary,
        truncated,
    })
}

pub fn get_commits(
    canon_root: &Path,
    repo_path: &str,
    git_ref: Option<&str>,
    limit: u32,
) -> Result<Vec<LocalCommitSummary>, GitReadError> {
    let canon_repo = resolve_safe_repo(canon_root, repo_path)?;
    let repo = open_gix_repo(&canon_repo)?;
    let spec = git_ref
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("HEAD");
    let start_id = match repo.rev_parse_single(spec) {
        Ok(id) => id,
        Err(_) if git_ref.is_none() => return Ok(Vec::new()),
        Err(e) => {
            return Err(GitReadError::NotFound(format!(
                "cannot resolve ref '{spec}': {e}"
            )));
        }
    };
    let max_count = (limit as usize).clamp(1, 200);
    let walk = repo
        .rev_walk([start_id.detach()])
        .all()
        .map_err(|e| GitReadError::Internal(format!("rev_walk failed: {e}")))?;

    let mut out = Vec::new();
    for info_res in walk.take(max_count) {
        let info = info_res.map_err(|e| GitReadError::Internal(format!("walk item failed: {e}")))?;
        if let Ok(commit) = info.id().object().and_then(|o| Ok(o.into_commit())) {
            out.push(commit_to_summary(&commit));
        }
    }
    Ok(out)
}

pub fn get_working_diff(
    canon_root: &Path,
    repo_path: &str,
) -> Result<LocalWorkingDiff, GitReadError> {
    let canon_repo = resolve_safe_repo(canon_root, repo_path)?;
    let repo = open_gix_repo(&canon_repo)?;
    let branch = current_branch_name(&repo);
    let head_tree = repo.head_commit().ok().and_then(|c| c.tree().ok());

    let iter = repo
        .status(gix::progress::Discard)
        .map_err(|e| GitReadError::Internal(format!("status failed: {e}")))?
        .into_iter(Vec::new())
        .map_err(|e| GitReadError::Internal(format!("status iter failed: {e}")))?;

    #[derive(Clone, Copy, PartialEq, Eq)]
    enum TrackedState {
        Added,
        Modified,
        Deleted,
        Untracked,
    }

    let mut paths: std::collections::BTreeMap<String, TrackedState> = Default::default();
    for item_res in iter {
        let item = match item_res {
            Ok(i) => i,
            Err(_) => continue,
        };
        let rel = item.location().to_str_lossy().into_owned();
        match item {
            StatusItem::IndexWorktree(iw) => match iw {
                gix::status::index_worktree::Item::DirectoryContents { entry, .. } => {
                    if entry.status == gix::dir::entry::Status::Untracked {
                        paths.entry(rel).or_insert(TrackedState::Untracked);
                    }
                }
                gix::status::index_worktree::Item::Modification { status, .. } => {
                    let st = match status {
                        gix::status::plumbing::index_as_worktree::EntryStatus::Change(
                            gix::status::plumbing::index_as_worktree::Change::Removed,
                        ) => TrackedState::Deleted,
                        _ => TrackedState::Modified,
                    };
                    paths.insert(rel, st);
                }
                gix::status::index_worktree::Item::Rewrite { .. } => {
                    paths.insert(rel, TrackedState::Modified);
                }
            },
            StatusItem::TreeIndex(ti) => match ti {
                gix::diff::index::Change::Addition { .. } => {
                    paths.entry(rel).or_insert(TrackedState::Added);
                }
                gix::diff::index::Change::Deletion { .. } => {
                    paths.entry(rel).or_insert(TrackedState::Deleted);
                }
                gix::diff::index::Change::Modification { .. } => {
                    paths.entry(rel).or_insert(TrackedState::Modified);
                }
                gix::diff::index::Change::Rewrite { .. } => {
                    paths.entry(rel).or_insert(TrackedState::Modified);
                }
            },
        }
    }

    let mut files = Vec::with_capacity(paths.len());
    for (rel, state) in paths {
        let old_bytes = head_tree
            .as_ref()
            .and_then(|t| t.lookup_entry_by_path(&rel).ok().flatten())
            .and_then(|e| e.object().ok())
            .map(|o| o.data.clone())
            .unwrap_or_default();

        let new_bytes = if state == TrackedState::Deleted {
            Vec::new()
        } else {
            match safe_existing_worktree_file(canon_root, &canon_repo, &rel)? {
                Some(path) if path.is_file() => fs::read(&path).unwrap_or_default(),
                _ => Vec::new(),
            }
        };

        let status = match state {
            TrackedState::Added => LocalDiffStatus::Added,
            TrackedState::Modified => LocalDiffStatus::Modified,
            TrackedState::Deleted => LocalDiffStatus::Deleted,
            TrackedState::Untracked => LocalDiffStatus::Untracked,
        };
        let (additions, deletions, patch) = build_patch(&rel, &old_bytes, &new_bytes);
        files.push(LocalDiffFile {
            path: rel,
            previous_path: None,
            status,
            additions,
            deletions,
            patch,
        });
    }

    Ok(LocalWorkingDiff { branch, files })
}

pub fn get_commit_diff(
    canon_root: &Path,
    repo_path: &str,
    sha: &str,
) -> Result<LocalCommitDiff, GitReadError> {
    let canon_repo = resolve_safe_repo(canon_root, repo_path)?;
    let repo = open_gix_repo(&canon_repo)?;
    let obj = repo
        .rev_parse_single(sha.trim())
        .map_err(|e| GitReadError::NotFound(format!("commit '{sha}' not found: {e}")))?
        .object()
        .map_err(|e| GitReadError::NotFound(format!("cannot read commit '{sha}': {e}")))?;
    let commit = obj.into_commit();
    let summary = commit_to_summary(&commit);
    let new_tree = commit
        .tree()
        .map_err(|e| GitReadError::Internal(format!("commit tree error: {e}")))?;
    let parent_tree = commit
        .parent_ids()
        .next()
        .and_then(|pid| pid.object().ok())
        .and_then(|o| o.into_commit().tree().ok());

    let changes = repo
        .diff_tree_to_tree(parent_tree.as_ref(), Some(&new_tree), None)
        .map_err(|e| GitReadError::Internal(format!("tree diff failed: {e}")))?;

    let mut files = Vec::with_capacity(changes.len());
    for change in changes {
        let (path, previous_path, status, entry_mode, old_id, new_id) = match change {
            ChangeDetached::Addition {
                location,
                entry_mode,
                id,
                ..
            } => (
                location.to_str_lossy().into_owned(),
                None,
                LocalDiffStatus::Added,
                entry_mode,
                None,
                Some(id),
            ),
            ChangeDetached::Deletion {
                location,
                entry_mode,
                id,
                ..
            } => (
                location.to_str_lossy().into_owned(),
                None,
                LocalDiffStatus::Deleted,
                entry_mode,
                Some(id),
                None,
            ),
            ChangeDetached::Modification {
                location,
                previous_id,
                entry_mode,
                id,
                ..
            } => (
                location.to_str_lossy().into_owned(),
                None,
                LocalDiffStatus::Modified,
                entry_mode,
                Some(previous_id),
                Some(id),
            ),
            ChangeDetached::Rewrite {
                source_location,
                location,
                entry_mode,
                source_id,
                id,
                ..
            } => (
                location.to_str_lossy().into_owned(),
                Some(source_location.to_str_lossy().into_owned()),
                LocalDiffStatus::Renamed,
                entry_mode,
                Some(source_id),
                Some(id),
            ),
        };
        if entry_mode.is_tree() {
            continue;
        }
        let read_blob = |oid: Option<gix::ObjectId>| -> Vec<u8> {
            oid.and_then(|id| repo.find_object(id).ok())
                .map(|o| o.data.clone())
                .unwrap_or_default()
        };
        let old_bytes = read_blob(old_id);
        let new_bytes = read_blob(new_id);
        let (additions, deletions, patch) = build_patch(&path, &old_bytes, &new_bytes);
        files.push(LocalDiffFile {
            path,
            previous_path,
            status,
            additions,
            deletions,
            patch,
        });
    }

    Ok(LocalCommitDiff {
        commit: summary,
        files,
    })
}

fn build_patch(path: &str, old_bytes: &[u8], new_bytes: &[u8]) -> (u32, u32, Option<String>) {
    if old_bytes.len() > MAX_PATCH_BYTES || new_bytes.len() > MAX_PATCH_BYTES {
        return (0, 0, None);
    }
    if old_bytes.contains(&0) || new_bytes.contains(&0) {
        return (0, 0, None);
    }
    let Ok(old_str) = std::str::from_utf8(old_bytes) else {
        return (0, 0, None);
    };
    let Ok(new_str) = std::str::from_utf8(new_bytes) else {
        return (0, 0, None);
    };
    let diff = TextDiff::from_lines(old_str, new_str);
    let mut additions = 0u32;
    let mut deletions = 0u32;
    for change in diff.iter_all_changes() {
        match change.tag() {
            similar::ChangeTag::Insert => additions += 1,
            similar::ChangeTag::Delete => deletions += 1,
            similar::ChangeTag::Equal => {}
        }
    }
    let unified = diff
        .unified_diff()
        .context_radius(3)
        .header(&format!("a/{path}"), &format!("b/{path}"))
        .to_string();
    let patch = if unified.trim().is_empty() {
        None
    } else {
        Some(unified)
    };
    (additions, deletions, patch)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::process::Command;

    #[test]
    fn path_traversal_and_symlink_escapes_are_blocked() {
        let tmp = tempfile::tempdir().unwrap();
        let root = tmp.path().join("workspace");
        let outside = tmp.path().join("outside");
        fs::create_dir_all(&root).unwrap();
        fs::create_dir_all(&outside).unwrap();
        fs::write(outside.join("secret.txt"), "top-secret").unwrap();

        let repo = root.join("demo");
        fs::create_dir_all(repo.join(".git")).unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink(outside.join("secret.txt"), repo.join("leak.txt")).unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink(&outside, root.join("escape_repo")).unwrap();

        let canon_root = fs::canonicalize(&root).unwrap();
        let canon_repo = fs::canonicalize(&repo).unwrap();

        assert!(matches!(
            resolve_safe_path(&canon_root, "../outside"),
            Err(GitReadError::ForbiddenPath(_))
        ));
        assert!(matches!(
            resolve_safe_path(&canon_root, "/etc/passwd"),
            Err(GitReadError::ForbiddenPath(_))
        ));
        #[cfg(unix)]
        {
            assert!(matches!(
                resolve_safe_path(&canon_root, "escape_repo"),
                Err(GitReadError::ForbiddenPath(_))
            ));
            assert!(matches!(
                safe_existing_worktree_file(&canon_root, &canon_repo, "leak.txt"),
                Err(GitReadError::ForbiddenPath(_))
            ));
        }
    }

    #[test]
    fn git_reader_lists_repos_tree_commits_and_diffs() {
        let tmp = tempfile::tempdir().unwrap();
        let root = tmp.path().join("projects");
        let repo = root.join("sample");
        fs::create_dir_all(&repo).unwrap();

        let run_git = |args: &[&str]| {
            let status = Command::new("git")
                .args(args)
                .current_dir(&repo)
                .env("GIT_AUTHOR_NAME", "Tester")
                .env("GIT_AUTHOR_EMAIL", "tester@example.com")
                .env("GIT_COMMITTER_NAME", "Tester")
                .env("GIT_COMMITTER_EMAIL", "tester@example.com")
                .status()
                .unwrap();
            assert!(status.success(), "git {args:?} failed");
        };

        run_git(&["init", "-b", "main"]);
        fs::create_dir_all(repo.join("src")).unwrap();
        fs::write(repo.join("README.md"), "# Sample\nhello\n").unwrap();
        fs::write(repo.join("src/main.rs"), "fn main() {}\n").unwrap();
        run_git(&["add", "."]);
        run_git(&["commit", "-m", "initial commit"]);

        // Make working tree dirty
        fs::write(repo.join("README.md"), "# Sample\nhello world\n").unwrap();
        fs::write(repo.join("notes.txt"), "untracked note\n").unwrap();

        let canon_root = fs::canonicalize(&root).unwrap();
        let repos = discover_repos(&canon_root).unwrap();
        assert_eq!(repos.len(), 1);
        assert_eq!(repos[0].name, "sample");
        assert_eq!(repos[0].relative_path, "sample");
        assert_eq!(repos[0].current_branch, "main");
        assert!(repos[0].is_dirty);

        let root_tree = get_tree(&canon_root, "sample", None, "").unwrap();
        assert_eq!(root_tree[0].name, "src");
        assert_eq!(root_tree[0].kind, LocalTreeEntryKind::Directory);
        assert_eq!(root_tree[1].name, "README.md");

        let readme = get_file(&canon_root, "sample", None, "README.md").unwrap();
        assert_eq!(readme.text.as_deref(), Some("# Sample\nhello\n"));

        let commits = get_commits(&canon_root, "sample", None, 10).unwrap();
        assert_eq!(commits.len(), 1);
        assert_eq!(commits[0].message, "initial commit");

        let commit_diff = get_commit_diff(&canon_root, "sample", &commits[0].sha).unwrap();
        assert_eq!(commit_diff.files.len(), 2);

        let working = get_working_diff(&canon_root, "sample").unwrap();
        assert!(working.files.iter().any(|f| f.path == "README.md" && f.status == LocalDiffStatus::Modified));
        assert!(working.files.iter().any(|f| f.path == "notes.txt" && f.status == LocalDiffStatus::Untracked));
    }

    #[test]
    fn browse_directories_and_summarize_registered_repos_enforces_sandbox() {
        let tmp = tempfile::tempdir().unwrap();
        let home = tmp.path().join("home");
        let outside = tmp.path().join("outside");
        let dev = home.join("dev");
        let repo = dev.join("app");
        let plain = dev.join("docs");
        fs::create_dir_all(&repo).unwrap();
        let init_status = Command::new("git")
            .args(["init", "-b", "main"])
            .current_dir(&repo)
            .status()
            .unwrap();
        assert!(init_status.success());
        fs::create_dir_all(&plain).unwrap();
        fs::create_dir_all(home.join(".hidden")).unwrap();
        fs::create_dir_all(&outside).unwrap();

        #[cfg(unix)]
        std::os::unix::fs::symlink(&outside, dev.join("escape_link")).unwrap();

        let canon_home = fs::canonicalize(&home).unwrap();

        let top = browse_directories(&canon_home, "").unwrap();
        assert_eq!(top.current_relative_path, "");
        assert!(!top.current_is_git_repo);
        assert_eq!(top.entries.len(), 1);
        assert_eq!(top.entries[0].name, "dev");
        assert!(!top.entries[0].is_git_repo);

        let dev_browse = browse_directories(&canon_home, "dev").unwrap();
        assert_eq!(dev_browse.current_relative_path, "dev");
        assert_eq!(
            dev_browse
                .entries
                .iter()
                .map(|e| (e.name.as_str(), e.is_git_repo))
                .collect::<Vec<_>>(),
            vec![("app", true), ("docs", false)]
        );

        let repo_browse = browse_directories(&canon_home, "dev/app").unwrap();
        assert!(repo_browse.current_is_git_repo);
        assert!(repo_browse.entries.is_empty());

        assert!(matches!(
            browse_directories(&canon_home, "../outside"),
            Err(GitReadError::ForbiddenPath(_))
        ));

        let summaries = summarize_repo_paths(
            &canon_home,
            &[
                "dev/app".into(),
                "dev/app".into(),
                "dev/docs".into(),
                "../outside".into(),
            ],
        );
        assert_eq!(summaries.len(), 1);
        assert_eq!(summaries[0].relative_path, "dev/app");
    }
}
