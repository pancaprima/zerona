//! Chat-scoped image reads: owner-device paths and bounded HTTPS fetches.

use std::collections::HashSet;
use std::io::{Read, Seek, SeekFrom};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::path::{Component, Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64;
use reqwest::redirect::Policy;
use sha2::{Digest, Sha256};
use zeron_doc::{MessagePart, TAIL_MESSAGE_COUNT};
use zeron_proto::{ChatImageChunk, FetchChatUrlImageRequest, ReadChatImageRequest};

use crate::uploads::{mime_by_ext, raster_signature, Uploads};
use crate::{DocHost, EngineError, Repos, SessionsEngine, WorkspaceHost};

use zeron_proto::MAX_CHAT_IMAGE_BYTES;
const READ_CHUNK_BYTES: u64 = 45_000;
const FETCH_TIMEOUT: Duration = Duration::from_secs(10);
const MAX_REDIRECTS: usize = 3;
const URL_CACHE_TTL: Duration = Duration::from_secs(60 * 60);
const MAX_MENTION_SCAN_PARTS: usize = 512;
static URL_CACHE_PART_COUNTER: AtomicU64 = AtomicU64::new(0);

#[derive(Clone)]
pub struct ChatMedia {
    device_id: String,
    workspace: WorkspaceHost,
    uploads: Uploads,
    repos: Repos,
    doc_host: DocHost,
    sessions: SessionsEngine,
    url_cache_root: PathBuf,
    allow_http: bool,
    temp_root: PathBuf,
}

impl ChatMedia {
    pub fn new(
        device_id: String,
        workspace: WorkspaceHost,
        uploads: Uploads,
        repos: Repos,
        doc_host: DocHost,
        sessions: SessionsEngine,
    ) -> Self {
        let url_cache_root = uploads
            .dir()
            .parent()
            .map(|root| root.join("tmp/chat-url-images"))
            .unwrap_or_else(|| repos.data_dir().join("tmp/chat-url-images"));
        let allow_http = std::env::var_os("ZERONA_CHAT_IMAGE_ALLOW_HTTP")
            .is_some_and(|v| v == "1" || v.eq_ignore_ascii_case("true"));
        Self {
            device_id,
            workspace,
            uploads,
            repos,
            doc_host,
            sessions,
            url_cache_root,
            allow_http,
            temp_root: std::env::temp_dir(),
        }
    }

    #[cfg(test)]
    pub(crate) fn with_temp_root(mut self, root: PathBuf) -> Self {
        self.temp_root = root;
        self
    }

    pub fn read_image(&self, request: &ReadChatImageRequest) -> Result<ChatImageChunk, EngineError> {
        let chat = self.require_owner_chat(&request.chat_id)?;
        let cwd = resolve_chat_cwd(&chat, &self.repos)?;
        let resolved = resolve_chat_path(&request.path, &cwd)?;
        let canonical = std::fs::canonicalize(&resolved).map_err(|_| denied())?;
        if !self.path_allowed(&request.chat_id, &chat, &canonical)? {
            return Err(denied());
        }
        self.read_file_chunk(&canonical, request.offset)
    }

    pub async fn fetch_url_image(
        &self,
        request: &FetchChatUrlImageRequest,
    ) -> Result<ChatImageChunk, EngineError> {
        self.require_owner_chat(&request.chat_id)?;
        let parsed = reqwest::Url::parse(&request.url).map_err(|_| denied())?;
        if parsed.username() != "" || parsed.password().is_some() {
            return Err(denied());
        }
        if parsed.scheme() != "https"
            && !(self.allow_http && parsed.scheme() == "http")
        {
            return Err(denied());
        }
        let cache_path = self.url_cache_path(&request.chat_id, &request.url);
        if !cache_fresh(&cache_path) {
            let bytes = self.fetch_url_bytes(&parsed).await?;
            validate_image_bytes(&bytes)?;
            std::fs::create_dir_all(self.url_cache_root.parent().unwrap_or(&self.url_cache_root))?;
            std::fs::create_dir_all(&self.url_cache_root)?;
            let part_id = URL_CACHE_PART_COUNTER.fetch_add(1, Ordering::Relaxed);
            let nanos = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_nanos())
                .unwrap_or(0);
            let tmp = cache_path.with_extension(format!("part-{part_id}-{nanos}"));
            std::fs::write(&tmp, &bytes)?;
            std::fs::rename(&tmp, &cache_path)?;
        }
        self.read_file_chunk(&cache_path, request.offset)
    }

    fn require_owner_chat(&self, chat_id: &str) -> Result<zeron_proto::Chat, EngineError> {
        let chat = self
            .workspace
            .chat(chat_id)?
            .ok_or_else(|| EngineError::Other("chat not found".into()))?;
        if chat.device_id != self.device_id {
            return Err(EngineError::Other("chat belongs to another device".into()));
        }
        Ok(chat)
    }

    pub(crate) fn path_allowed(
        &self,
        chat_id: &str,
        chat: &zeron_proto::Chat,
        canonical: &Path,
    ) -> Result<bool, EngineError> {
        let mentioned = mentioned_canonical_paths(self, chat_id, chat)?;
        if mentioned.contains(canonical) {
            return Ok(true);
        }
        let roots = self.directory_roots(chat)?;
        Ok(roots.iter().any(|root| path_under_root(canonical, root)))
    }

    fn directory_roots(&self, chat: &zeron_proto::Chat) -> Result<Vec<PathBuf>, EngineError> {
        let mut roots = self.uploads.read_roots();
        roots.push(self.temp_root.clone());
        if let Some(root) = self.sessions.generated_images_allowed_root() {
            roots.push(root);
        }
        let cwd = resolve_chat_cwd(chat, &self.repos)?;
        roots.push(cwd);
        Ok(roots
            .into_iter()
            .filter_map(|root| std::fs::canonicalize(root).ok())
            .collect())
    }

    fn read_file_chunk(&self, path: &Path, offset: u64) -> Result<ChatImageChunk, EngineError> {
        let meta = std::fs::metadata(path).map_err(|_| denied())?;
        if !meta.is_file() || meta.len() > MAX_CHAT_IMAGE_BYTES {
            return Err(denied());
        }
        if !extension_allowed(path) {
            return Err(denied());
        }
        let mut header = [0u8; 12];
        let mut file = std::fs::File::open(path).map_err(|_| denied())?;
        let read = file.read(&mut header)?;
        if !image_bytes_allowed(&header[..read]) {
            return Err(denied());
        }
        let mime_type = mime_by_ext(path)
            .or_else(|| raster_signature(&header[..read]).map(|(_, mime)| mime))
            .unwrap_or("application/octet-stream");
        let size = meta.len();
        let start = offset.min(size);
        let end = (start + READ_CHUNK_BYTES).min(size);
        file.seek(SeekFrom::Start(start))?;
        let mut buf = vec![0u8; (end - start) as usize];
        file.read_exact(&mut buf)?;
        Ok(ChatImageChunk {
            name: path
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_else(|| "image".into()),
            mime_type: mime_type.to_string(),
            data: BASE64.encode(&buf),
            next_offset: end,
            size,
            done: end >= size,
        })
    }

    fn url_cache_path(&self, chat_id: &str, url: &str) -> PathBuf {
        let hash = format!("{:x}", Sha256::digest(url.as_bytes()));
        let chat_hash = format!("{:x}", Sha256::digest(chat_id.as_bytes()));
        self.url_cache_root.join(format!("{chat_hash}-{hash}"))
    }

    async fn fetch_url_bytes(&self, start: &reqwest::Url) -> Result<Vec<u8>, EngineError> {
        let mut url = start.clone();
        for hop in 0..=MAX_REDIRECTS {
            url_allowed(&url, self.allow_http)?;
            let mut response = self.fetch_hop(&url).await?;
            if response.status().is_redirection() {
                if hop >= MAX_REDIRECTS {
                    return Err(denied());
                }
                let location = response
                    .headers()
                    .get(reqwest::header::LOCATION)
                    .and_then(|v| v.to_str().ok())
                    .ok_or_else(denied)?;
                url = reqwest::Url::parse(location)
                    .or_else(|_| url.join(location))
                    .map_err(|_| denied())?;
                continue;
            }
            if !response.status().is_success() {
                return Err(denied());
            }
            let content_type = response
                .headers()
                .get(reqwest::header::CONTENT_TYPE)
                .and_then(|v| v.to_str().ok())
                .unwrap_or("");
            if !content_type.starts_with("image/") {
                return Err(denied());
            }
            if response.content_length().is_some_and(|len| len > MAX_CHAT_IMAGE_BYTES) {
                return Err(denied());
            }
            let mut body = Vec::new();
            while let Some(chunk) = response.chunk().await.map_err(|_| denied())? {
                let cap = MAX_CHAT_IMAGE_BYTES as usize;
                if body.len() + chunk.len() > cap {
                    return Err(denied());
                }
                body.extend_from_slice(&chunk);
            }
            validate_image_bytes(&body)?;
            return Ok(body);
        }
        Err(denied())
    }

    async fn fetch_hop(&self, url: &reqwest::Url) -> Result<reqwest::Response, EngineError> {
        let host = url
            .host_str()
            .ok_or_else(denied)?
            .trim_end_matches('.')
            .to_string();
        let port = url.port_or_known_default().ok_or_else(denied)?;
        let ips = resolve_public_ips(&host).await?;
        let ip = ips.first().copied().ok_or_else(denied)?;
        let addr = SocketAddr::new(ip, port);
        let mut builder = reqwest::Client::builder()
            .timeout(FETCH_TIMEOUT)
            .redirect(Policy::none())
            .no_proxy();
        if host.parse::<IpAddr>().is_err() {
            builder = builder.resolve(&host, addr);
        }
        let client = builder.build().map_err(|_| denied())?;
        client
            .get(url.clone())
            .send()
            .await
            .map_err(|_| denied())
    }
}

fn denied() -> EngineError {
    EngineError::Other("Chat image is not available".into())
}

fn resolve_chat_cwd(chat: &zeron_proto::Chat, repos: &Repos) -> Result<PathBuf, EngineError> {
    let cwd = chat.cwd.as_deref().unwrap_or("~");
    crate::repos::resolve_chat_cwd(cwd, &repos.scratch_root())
        .map(PathBuf::from)
        .map_err(|e| EngineError::Other(e.to_string()))
}

fn resolve_chat_path(path: &str, cwd: &Path) -> Result<PathBuf, EngineError> {
    if is_forbidden_path_syntax(path) {
        return Err(denied());
    }
    let candidate = Path::new(path);
    if candidate.is_absolute() {
        if relative_with_parent(candidate) {
            return Err(denied());
        }
        return Ok(candidate.to_path_buf());
    }
    if path.contains("..") {
        return Err(denied());
    }
    Ok(cwd.join(path))
}

fn relative_with_parent(path: &Path) -> bool {
    path.components().any(|c| matches!(c, Component::ParentDir))
}

fn is_forbidden_path_syntax(path: &str) -> bool {
    let bytes = path.as_bytes();
    let separator = |b: Option<&u8>| matches!(b, Some(b'/' | b'\\'));
    separator(bytes.first()) && separator(bytes.get(1))
        || path.starts_with(r"\??\")
        || (bytes.len() >= 2
            && bytes[0].is_ascii_alphabetic()
            && bytes[1] == b':'
            && !separator(bytes.get(2)))
}

fn path_under_root(path: &Path, root: &Path) -> bool {
    path.starts_with(root) && path != root
}

fn mentioned_canonical_paths(
    media: &ChatMedia,
    chat_id: &str,
    chat: &zeron_proto::Chat,
) -> Result<HashSet<PathBuf>, EngineError> {
    let cwd = resolve_chat_cwd(chat, &media.repos)?;
    let handle = media.doc_host.open(chat_id)?;
    let entries = handle.doc().read_entries()?;
    let mut mentioned = HashSet::new();
    let mut parts_seen = 0usize;
    for entry in entries.iter().rev().take(TAIL_MESSAGE_COUNT) {
        for part in entry.parts.iter().rev() {
            if parts_seen >= MAX_MENTION_SCAN_PARTS {
                break;
            }
            parts_seen += 1;
            let text = part_text(part);
            for token in extract_path_tokens(&text) {
                if is_forbidden_path_syntax(&token) {
                    continue;
                }
                if let Ok(resolved) = resolve_chat_path(&token, &cwd) {
                    if let Ok(canonical) = std::fs::canonicalize(&resolved) {
                        mentioned.insert(canonical);
                    }
                }
            }
            for dest in markdown_image_destinations(&text) {
                if dest.contains("://") {
                    continue;
                }
                if is_forbidden_path_syntax(&dest) {
                    continue;
                }
                if let Ok(resolved) = resolve_chat_path(&dest, &cwd) {
                    if let Ok(canonical) = std::fs::canonicalize(&resolved) {
                        mentioned.insert(canonical);
                    }
                }
            }
            for dest in media_token_destinations(&text) {
                if dest.contains("://") {
                    continue;
                }
                if is_forbidden_path_syntax(&dest) {
                    continue;
                }
                if let Ok(resolved) = resolve_chat_path(&dest, &cwd) {
                    if let Ok(canonical) = std::fs::canonicalize(&resolved) {
                        mentioned.insert(canonical);
                    }
                }
            }
        }
    }
    Ok(mentioned)
}

fn part_text(part: &MessagePart) -> String {
    match part {
        MessagePart::Text { text, .. } => text.clone(),
        MessagePart::Reasoning { text, .. } => text.clone(),
        MessagePart::Tool { output, .. } => output.clone().unwrap_or_default(),
        MessagePart::Error { message, .. } => message.clone(),
        _ => String::new(),
    }
}

const MEDIA_TOKEN_PREFIX: &str = "MEDIA:";
const MEDIA_IMAGE_EXTENSIONS: &[&str] = &["png", "jpg", "jpeg", "gif", "webp"];

fn media_token_boundary(text: &str, start: usize) -> bool {
    start == 0 || text[..start].chars().next_back().is_some_and(|c| c.is_whitespace())
}

fn media_path_has_allowed_image_extension(path: &str) -> bool {
    let path = path.trim_end_matches(|c: char| matches!(c, '.' | ',' | '!' | '?' | ';' | ':'));
    path.rsplit('/')
        .next()
        .and_then(|name| name.rsplit('.').next())
        .map(|ext| {
            let ext = ext.to_ascii_lowercase();
            MEDIA_IMAGE_EXTENSIONS.iter().any(|allowed| *allowed == ext)
        })
        .unwrap_or(false)
}

fn trim_media_trailing_punctuation(path: &str) -> &str {
    path.trim_end_matches(|c: char| {
        matches!(c, ',' | '!' | '?' | ';' | ')' | ']' | '"' | '\'' | '`')
    })
    .trim_end_matches('.')
}

fn parse_media_token_source(tail: &str) -> Option<(&str, usize)> {
    let leading = tail.len() - tail.trim_start().len();
    let tail = tail.trim_start();
    if tail.is_empty() {
        return None;
    }
    let end = if tail.starts_with("https://")
        || tail.starts_with("http://")
        || tail.starts_with("//")
        || tail.starts_with('/')
    {
        tail.find(|c: char| c.is_whitespace()).unwrap_or(tail.len())
    } else {
        let bytes = tail.as_bytes();
        if bytes.len() >= 3
            && bytes[0].is_ascii_alphabetic()
            && bytes[1] == b':'
            && matches!(bytes.get(2), Some(b'/' | b'\\'))
        {
            tail.find(|c: char| c.is_whitespace()).unwrap_or(tail.len())
        } else {
            return None;
        }
    };
    let source = trim_media_trailing_punctuation(&tail[..end]);
    if source.is_empty() || !media_path_has_allowed_image_extension(source) {
        return None;
    }
    Some((source, leading + source.len()))
}

fn media_token_destinations(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut search_at = 0;
    while let Some(rel) = text[search_at..].find(MEDIA_TOKEN_PREFIX) {
        let start = search_at + rel;
        if !media_token_boundary(text, start) {
            search_at = start + MEDIA_TOKEN_PREFIX.len();
            continue;
        }
        let tail_start = start + MEDIA_TOKEN_PREFIX.len();
        let tail = &text[tail_start..];
        if let Some((source, consumed)) = parse_media_token_source(tail) {
            out.push(source.to_string());
            search_at = tail_start + consumed;
        } else {
            search_at = start + MEDIA_TOKEN_PREFIX.len();
        }
    }
    out
}

fn markdown_image_destinations(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut search = text;
    while let Some(ix) = search.find("![") {
        let rest = &search[ix + 2..];
        let Some(close_alt) = rest.find(']') else { break };
        let after = rest[close_alt + 1..].trim_start();
        if !after.starts_with('(') {
            search = &search[ix + 2..];
            continue;
        }
        let inner = after[1..].trim_start();
        let end = inner.find(')').unwrap_or(inner.len());
        let dest = inner[..end].trim();
        if !dest.is_empty() {
            out.push(dest.to_string());
        }
        search = &search[ix + 2 + close_alt..];
    }
    out
}

fn extract_path_tokens(text: &str) -> Vec<String> {
    let separators = |c: char| {
        c.is_whitespace()
            || matches!(
                c,
                '"' | '\'' | '`' | '<' | '>' | '(' | ')' | '[' | ']' | '{' | '}' | ',' | ';' | '|'
                    | '='
            )
    };
    text.split(separators)
        .map(|t| t.trim_end_matches(['.', ':', '!', '?']))
        .filter(|t| {
            !t.is_empty()
                && !t.contains("://")
                && !t.starts_with("data:")
                && t.contains(['/', '\\'])
        })
        .map(|t| t.strip_prefix("file://").unwrap_or(t).to_string())
        .collect()
}

fn extension_allowed(path: &Path) -> bool {
    match path
        .extension()
        .and_then(|s| s.to_str())
        .map(|s| s.to_ascii_lowercase())
    {
        Some(ext) if matches!(ext.as_str(), "png" | "jpg" | "jpeg" | "gif" | "webp") =>
        {
            true
        }
        _ => false,
    }
}

fn image_bytes_allowed(header: &[u8]) -> bool {
    raster_signature(header).is_some()
}

fn validate_image_bytes(bytes: &[u8]) -> Result<(), EngineError> {
    if bytes.is_empty() || bytes.len() as u64 > MAX_CHAT_IMAGE_BYTES {
        return Err(denied());
    }
    let header = bytes.get(..12.min(bytes.len())).unwrap_or(bytes);
    if !image_bytes_allowed(header) {
        return Err(denied());
    }
    Ok(())
}

fn cache_fresh(path: &Path) -> bool {
    let Ok(meta) = std::fs::metadata(path) else {
        return false;
    };
    let Ok(modified) = meta.modified() else {
        return false;
    };
    modified
        .elapsed()
        .is_ok_and(|age| age < URL_CACHE_TTL)
}

fn url_allowed(url: &reqwest::Url, allow_http: bool) -> Result<(), EngineError> {
    if url.username() != "" || url.password().is_some() {
        return Err(denied());
    }
    if url.scheme() != "https" && !(allow_http && url.scheme() == "http") {
        return Err(denied());
    }
    let Some(host) = url.host_str() else {
        return Err(denied());
    };
    if let Ok(ip) = host.parse::<IpAddr>() {
        return if blocked_ip(ip) {
            Err(denied())
        } else {
            Ok(())
        };
    }
    let name = host.trim_end_matches('.').to_ascii_lowercase();
    if name.is_empty()
        || name == "localhost"
        || name.ends_with(".localhost")
        || name.ends_with(".local")
        || name.ends_with(".internal")
    {
        return Err(denied());
    }
    Ok(())
}

async fn resolve_public_ips(host: &str) -> Result<Vec<IpAddr>, EngineError> {
    if let Ok(ip) = host.parse::<IpAddr>() {
        return if blocked_ip(ip) {
            Err(denied())
        } else {
            Ok(vec![ip])
        };
    }
    let addresses: Vec<IpAddr> = tokio::net::lookup_host((host.as_ref(), 0))
        .await
        .map_err(|_| denied())?
        .map(|addr| addr.ip())
        .filter(|ip| !blocked_ip(*ip))
        .collect();
    if addresses.is_empty() {
        Err(denied())
    } else {
        Ok(addresses)
    }
}

/// True when the address must not be reached for chat URL images.
pub(crate) fn blocked_ip(ip: IpAddr) -> bool {
    !public_ip(ip)
}

fn public_ip(ip: IpAddr) -> bool {
    fn public_v4(ip: Ipv4Addr) -> bool {
        let [a, b, c, _] = ip.octets();
        !(ip.is_loopback()
            || ip.is_private()
            || ip.is_link_local()
            || ip.is_unspecified()
            || ip.is_broadcast()
            || ip.is_documentation()
            || ip.is_multicast()
            || a == 0
            || (a == 100 && (64..128).contains(&b))
            || (a == 192 && b == 0 && c == 0)
            || (a == 192 && b == 88 && c == 99)
            || (a == 198 && (18..20).contains(&b))
            || a >= 240)
    }
    match ip {
        IpAddr::V4(ip) => public_v4(ip),
        IpAddr::V6(ip) => {
            let segments = ip.segments();
            let embedded = |high: u16, low: u16| {
                let [a, b] = high.to_be_bytes();
                let [c, d] = low.to_be_bytes();
                Ipv4Addr::new(a, b, c, d)
            };
            if let Some(v4) = ip.to_ipv4() {
                return !ip.is_unspecified() && !ip.is_loopback() && public_v4(v4);
            }
            if segments[..6] == [0x64, 0xff9b, 0, 0, 0, 0] {
                return public_v4(embedded(segments[6], segments[7]));
            }
            if segments[0] == 0x2002 {
                return public_v4(embedded(segments[1], segments[2]));
            }
            let first = segments[0];
            !(ip.is_multicast()
                || (first & 0xfe00) == 0xfc00
                || (first & 0xffc0) == 0xfe80
                || (first & 0xffc0) == 0xfec0
                || (first == 0x2001 && segments[1] == 0))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn fixture() -> (
        tokio::runtime::Runtime,
        tempfile::TempDir,
        ChatMedia,
        zeron_proto::Chat,
    ) {
        let rt = tokio::runtime::Runtime::new().unwrap();
        let (dir, media, chat) = {
            let _guard = rt.enter();
            let dir = tempfile::tempdir().unwrap();
            let registry = std::sync::Arc::new(crate::HarnessRegistry::new());
            let core = crate::EngineCore::assemble_with_profile(
                crate::EngineProfile::local(dir.path()).unwrap(),
                registry,
                zeron_proto::HarnessId::Codex,
                None,
            )
            .unwrap();
            let cwd = dir.path().to_string_lossy().into_owned();
            core.workspace
                .create_chat("chat-1", None, Some(&core.device_id), None, Some(cwd))
                .unwrap();
            let chat = core.workspace.chat("chat-1").unwrap().unwrap();
            let media = ChatMedia::new(
                core.device_id.clone(),
                core.workspace.clone(),
                core.uploads.clone(),
                core.repos.clone(),
                core.doc_host.clone(),
                core.sessions.clone(),
            )
            .with_temp_root(dir.path().join("__no_temp_root"));
            (dir, media, chat)
        };
        (rt, dir, media, chat)
    }

    #[test]
    fn allowlist_hit_and_miss() {
        let (rt, dir, media, chat) = fixture();
        let _enter = rt.enter();
        let allowed = dir.path().join("ok.png");
        std::fs::write(&allowed, b"\x89PNG\r\n\x1a\nx").unwrap();
        let canonical = std::fs::canonicalize(&allowed).unwrap();
        assert!(media.path_allowed("chat-1", &chat, &canonical).unwrap());
        let outside_dir = tempfile::tempdir().unwrap();
        let outside_file = outside_dir.path().join("denied.png");
        std::fs::write(&outside_file, b"\x89PNG\r\n\x1a\nx").unwrap();
        let outside = std::fs::canonicalize(&outside_file).unwrap();
        assert!(!media.path_allowed("chat-1", &chat, &outside).unwrap());
    }

    #[test]
    fn rejects_parent_dir_escape_in_relative_path() {
        let (rt, dir, media, _chat) = fixture();
        let _enter = rt.enter();
        let err = media
            .read_image(&ReadChatImageRequest {
                chat_id: "chat-1".into(),
                path: "../outside.png".into(),
                offset: 0,
            })
            .unwrap_err();
        assert!(err.to_string().contains("not available"));
        let secret = dir.path().join("../secret.png");
        assert!(media
            .read_image(&ReadChatImageRequest {
                chat_id: "chat-1".into(),
                path: secret.to_string_lossy().into_owned(),
                offset: 0,
            })
            .is_err());
    }

    #[test]
    fn symlink_escape_is_rejected() {
        let (rt, dir, media, chat) = fixture();
        let _enter = rt.enter();
        let outside = tempfile::tempdir().unwrap();
        let secret = outside.path().join("secret.png");
        std::fs::write(&secret, b"\x89PNG\r\n\x1a\nx").unwrap();
        let link = dir.path().join("link.png");
        #[cfg(unix)]
        {
            std::os::unix::fs::symlink(&secret, &link).unwrap();
            let canonical = std::fs::canonicalize(&link).unwrap();
            assert!(!media.path_allowed("chat-1", &chat, &canonical).unwrap());
        }
    }

    #[test]
    fn media_token_destinations_parsing() {
        let path = "/tmp/phone_shot.jpg";
        let text = format!("A07 screenshot captured. MEDIA:{path}");
        assert_eq!(media_token_destinations(&text), vec![path.to_string()]);
        assert!(media_token_destinations("MEDIA:/tmp/readme.txt").is_empty());
        assert!(media_token_destinations("prefixMEDIA:/tmp/x.png").is_empty());
        assert!(media_token_destinations("see /tmp/unmentioned.png").is_empty());
        assert_eq!(
            media_token_destinations("Saved MEDIA:/tmp/shot.png."),
            vec!["/tmp/shot.png".to_string()]
        );
    }

    #[test]
    fn mentioned_path_via_media_token() {
        let (rt, _dir, media, chat) = fixture();
        let _enter = rt.enter();
        let outside = tempfile::tempdir().unwrap();
        let file = outside.path().join("mentioned.png");
        std::fs::write(&file, b"\x89PNG\r\n\x1a\nx").unwrap();
        let path = file.to_string_lossy().into_owned();
        let handle = media.doc_host.open("chat-1").unwrap();
        handle
            .write_user_message("m1", &format!("see MEDIA:{path}"), 1)
            .unwrap();
        let canonical = std::fs::canonicalize(&file).unwrap();
        assert!(media.path_allowed("chat-1", &chat, &canonical).unwrap());
        let sibling = outside.path().join("other.png");
        std::fs::write(&sibling, b"\x89PNG\r\n\x1a\nx").unwrap();
        assert!(!media
            .path_allowed("chat-1", &chat, &std::fs::canonicalize(&sibling).unwrap())
            .unwrap());
    }

    #[test]
    fn mentioned_path_exact_match_only() {
        let (rt, _dir, media, chat) = fixture();
        let _enter = rt.enter();
        let outside = tempfile::tempdir().unwrap();
        let file = outside.path().join("mentioned.png");
        std::fs::write(&file, b"\x89PNG\r\n\x1a\nx").unwrap();
        let path = file.to_string_lossy().into_owned();
        let handle = media.doc_host.open("chat-1").unwrap();
        handle
            .write_user_message("m1", &format!("see ![x]({path})"), 1)
            .unwrap();
        let canonical = std::fs::canonicalize(&file).unwrap();
        assert!(media.path_allowed("chat-1", &chat, &canonical).unwrap());
        let sibling = outside.path().join("other.png");
        std::fs::write(&sibling, b"\x89PNG\r\n\x1a\nx").unwrap();
        assert!(!media
            .path_allowed("chat-1", &chat, &std::fs::canonicalize(&sibling).unwrap())
            .unwrap());
    }

    #[test]
    fn fake_png_extension_without_raster_bytes_is_rejected() {
        let (rt, dir, media, _chat) = fixture();
        let _enter = rt.enter();
        let fake = dir.path().join("fake.png");
        std::fs::write(&fake, b"not an image").unwrap();
        assert!(media
            .read_image(&ReadChatImageRequest {
                chat_id: "chat-1".into(),
                path: fake.to_string_lossy().into_owned(),
                offset: 0,
            })
            .is_err());
    }

    #[test]
    fn oversize_file_is_rejected() {
        let (rt, dir, media, _chat) = fixture();
        let _enter = rt.enter();
        let huge = dir.path().join("huge.png");
        let mut file = std::fs::File::create(&huge).unwrap();
        let header = b"\x89PNG\r\n\x1a\n";
        file.write_all(header).unwrap();
        file.set_len(MAX_CHAT_IMAGE_BYTES + 1).unwrap();
        assert!(media
            .read_image(&ReadChatImageRequest {
                chat_id: "chat-1".into(),
                path: huge.to_string_lossy().into_owned(),
                offset: 0,
            })
            .is_err());
    }

    #[test]
    fn unc_paths_are_rejected() {
        let (rt, _dir, media, _chat) = fixture();
        let _enter = rt.enter();
        for path in [r"\\host\share\a.png", "//host/share/a.png", r"C:file.png"] {
            assert!(media
                .read_image(&ReadChatImageRequest {
                    chat_id: "chat-1".into(),
                    path: path.into(),
                    offset: 0,
                })
                .is_err());
        }
    }

    #[test]
    fn ssrf_ip_classification_table() {
        for address in [
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "::1",
            "::ffff:127.0.0.1",
            "::127.0.0.1",
            "fc00::1",
            "fe80::1",
        ] {
            let ip: IpAddr = address.parse().unwrap();
            assert!(blocked_ip(ip), "{address}");
        }
        for address in ["8.8.8.8", "140.82.112.3", "::ffff:8.8.8.8"] {
            let ip: IpAddr = address.parse().unwrap();
            assert!(!blocked_ip(ip), "{address}");
        }
    }

    #[test]
    fn redirect_to_private_is_rejected() {
        let private = reqwest::Url::parse("http://127.0.0.1/private.png").unwrap();
        assert!(url_allowed(&private, true).is_err());
        let metadata = reqwest::Url::parse("http://169.254.169.254/latest").unwrap();
        assert!(url_allowed(&metadata, true).is_err());
    }
}
