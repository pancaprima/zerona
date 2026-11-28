//! Read-only export of workspace chats for external status widgets (Hermes).
//!
//! Writes `widget-chats.json` under the profile store root with harness session
//! ids and Zeron deep links keyed by workspace locator.

use std::io;
use std::path::Path;

use chrono::{DateTime, Utc};
use serde::Serialize;
use zeron_notify::links::{conversation_link, workspace_locator_of};
use zeron_proto::Chat;

const INDEX_FILE: &str = "widget-chats.json";

#[derive(Serialize)]
struct ChatsIndex<'a> {
    schema_version: u32,
    generated_at_ms: i64,
    scope: &'a str,
    workspace: &'a str,
    device_id: &'a str,
    chats: Vec<ChatIndexEntry<'a>>,
}

#[derive(Serialize)]
struct ChatIndexEntry<'a> {
    chat_id: &'a str,
    harness_session_id: Option<&'a str>,
    title: Option<&'a str>,
    device_id: &'a str,
    harness: Option<&'static str>,
    last_message_at: Option<i64>,
    created_at: i64,
    archived: bool,
    parent_chat_id: Option<&'a str>,
    deep_link: String,
}

fn workspace_identity(
    device_id: &str,
    user_id: &str,
    org_id: &str,
    local_only: bool,
) -> (&'static str, String) {
    if local_only {
        (
            "Local",
            format!("device:{device_id}"),
        )
    } else {
        let org = if org_id.trim().is_empty() {
            "personal"
        } else {
            org_id
        };
        (
            "Synced",
            format!("user:{user_id}:org:{org}"),
        )
    }
}

fn harness_slug(config: Option<&zeron_proto::ChatConfig>) -> Option<&'static str> {
    config.map(|c| c.harness.as_str())
}

fn dt_ms(at: Option<DateTime<Utc>>) -> Option<i64> {
    at.map(|t| t.timestamp_millis())
}

fn build_index_bytes(
    device_id: &str,
    user_id: &str,
    org_id: &str,
    local_only: bool,
    chats: &[Chat],
    generated_at_ms: i64,
) -> io::Result<Vec<u8>> {
    let (scope, identity) = workspace_identity(device_id, user_id, org_id, local_only);
    let locator = workspace_locator_of(scope, &identity);

    let mut entries: Vec<ChatIndexEntry<'_>> = Vec::new();
    for chat in chats {
        if chat.id.is_empty() {
            continue;
        }
        entries.push(ChatIndexEntry {
            chat_id: &chat.id,
            harness_session_id: chat.harness_session_id.as_deref(),
            title: chat.title.as_deref(),
            device_id: &chat.device_id,
            harness: harness_slug(chat.config.as_ref()),
            last_message_at: dt_ms(chat.last_message_at),
            created_at: chat.created_at.timestamp_millis(),
            archived: chat.archived,
            parent_chat_id: chat.parent_chat_id.as_deref(),
            deep_link: conversation_link(&chat.id, &locator),
        });
    }
    entries.sort_by(|a, b| a.chat_id.cmp(b.chat_id));

    let payload = ChatsIndex {
        schema_version: 1,
        generated_at_ms,
        scope,
        workspace: &locator,
        device_id,
        chats: entries,
    };
    let json =
        serde_json::to_string_pretty(&payload).map_err(|e| io::Error::other(e.to_string()))?;
    let mut bytes = json.into_bytes();
    bytes.push(b'\n');
    Ok(bytes)
}

fn stable_index_content(bytes: &[u8]) -> Option<serde_json::Value> {
    let mut value: serde_json::Value = serde_json::from_slice(bytes).ok()?;
    value.as_object_mut()?.remove("generated_at_ms");
    Some(value)
}

fn index_paths(store_root: &Path) -> (std::path::PathBuf, std::path::PathBuf) {
    let path = store_root.join(INDEX_FILE);
    let tmp = store_root.join(format!("{INDEX_FILE}.tmp"));
    (path, tmp)
}

pub(crate) fn write_chats_index(
    store_root: &Path,
    device_id: &str,
    user_id: &str,
    org_id: &str,
    local_only: bool,
    chats: &[Chat],
) -> io::Result<bool> {
    if local_only && device_id.is_empty() {
        return Ok(false);
    }

    let generated_at_ms = Utc::now().timestamp_millis();
    let new_bytes = build_index_bytes(
        device_id,
        user_id,
        org_id,
        local_only,
        chats,
        generated_at_ms,
    )?;

    let (path, tmp) = index_paths(store_root);

    if let Ok(existing) = std::fs::read(&path) {
        if existing == new_bytes {
            return Ok(false);
        }
        if let (Some(old), Some(new)) = (
            stable_index_content(&existing),
            stable_index_content(&new_bytes),
        ) {
            if old == new {
                return Ok(false);
            }
        }
    }

    std::fs::create_dir_all(store_root)?;
    std::fs::write(&tmp, &new_bytes)?;
    match std::fs::rename(&tmp, &path) {
        Ok(()) => Ok(true),
        Err(e) => Err(e),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use zeron_proto::{AccessMode, ChatConfig, HarnessId, SandboxLevel};

    static DIR_SEQ: AtomicUsize = AtomicUsize::new(0);

    fn fresh_store_dir() -> PathBuf {
        let n = DIR_SEQ.fetch_add(1, Ordering::Relaxed);
        let dir = std::env::temp_dir().join(format!("zeron-widget-chats-test-{n}"));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("test store dir");
        dir
    }

    fn minimal_chat(id: &str) -> Chat {
        Chat {
            id: id.to_string(),
            device_id: "dev".to_string(),
            title: None,
            archived: false,
            cwd: None,
            branch: None,
            checkout_id: None,
            source_context: None,
            config: None,
            last_message_preview: None,
            last_message_at: None,
            created_at: Utc::now(),
            harness_session_id: None,
            harness_session_cwd: None,
            harness_session_harness: None,
            room_gen: None,
            space_id: None,
            last_seen_at: None,
            parent_chat_id: None,
            child_kind: None,
            sleep: None,
        }
    }

    fn hermes_config() -> ChatConfig {
        ChatConfig {
            harness: HarnessId::Hermes,
            model: None,
            reasoning: None,
            model_options: serde_json::Map::new(),
            sandbox: SandboxLevel::WorkspaceWrite,
            access: AccessMode::FullAccess,
            plan: false,
        }
    }

    #[test]
    fn local_locator_matches_reference_hash() {
        let dir = fresh_store_dir();
        let chats = [minimal_chat("c1")];
        write_chats_index(&dir, "device-a", "u", "", true, &chats).expect("write");
        let bytes = std::fs::read(dir.join(INDEX_FILE)).expect("read index");
        let value: serde_json::Value = serde_json::from_slice(&bytes).expect("json");
        let expected =
            workspace_locator_of("Local", "device:device-a");
        assert_eq!(value["scope"], "Local");
        assert_eq!(value["workspace"].as_str(), Some(expected.as_str()));
    }

    #[test]
    fn synced_locator_uses_user_and_org() {
        let dir = fresh_store_dir();
        let chats = [minimal_chat("c1")];

        write_chats_index(&dir, "d", "u1", "", false, &chats).expect("write");
        let value: serde_json::Value =
            serde_json::from_slice(&std::fs::read(dir.join(INDEX_FILE)).unwrap()).unwrap();
        assert_eq!(
            value["workspace"].as_str(),
            Some(workspace_locator_of("Synced", "user:u1:org:personal").as_str())
        );

        let dir2 = fresh_store_dir();
        write_chats_index(&dir2, "d", "u1", "org_1", false, &chats).expect("write");
        let value2: serde_json::Value =
            serde_json::from_slice(&std::fs::read(dir2.join(INDEX_FILE)).unwrap()).unwrap();
        assert_eq!(
            value2["workspace"].as_str(),
            Some(workspace_locator_of("Synced", "user:u1:org:org_1").as_str())
        );
    }

    #[test]
    fn empty_device_id_skips_write() {
        let dir = fresh_store_dir();
        let chats = [minimal_chat("c1")];
        let wrote = write_chats_index(&dir, "", "u", "", true, &chats).expect("call");
        assert!(!wrote);
        assert!(!dir.join(INDEX_FILE).exists());
    }

    #[test]
    fn payload_shape() {
        let dir = fresh_store_dir();
        let locator = workspace_locator_of("Local", "device:dev-1");
        let mut a = minimal_chat("b-chat");
        a.harness_session_id = Some("sess-1".to_string());
        a.title = Some("With session".to_string());
        a.config = Some(hermes_config());
        let mut b = minimal_chat("a-chat");
        b.parent_chat_id = Some("parent".to_string());
        b.created_at = DateTime::from_timestamp_millis(1_795_832_000_000).unwrap();
        b.last_message_at = DateTime::from_timestamp_millis(1_795_833_000_000);

        write_chats_index(&dir, "dev-1", "", "", true, &[a, b]).expect("write");
        let value: serde_json::Value =
            serde_json::from_slice(&std::fs::read(dir.join(INDEX_FILE)).unwrap()).unwrap();
        let rows = value["chats"].as_array().expect("chats array");
        assert_eq!(rows.len(), 2);
        assert_eq!(rows[0]["chat_id"], "a-chat");
        assert_eq!(rows[1]["chat_id"], "b-chat");
        assert!(rows[0]["harness_session_id"].is_null());
        assert_eq!(rows[1]["harness_session_id"], "sess-1");
        assert!(rows[0]["title"].is_null());
        assert_eq!(rows[1]["title"], "With session");
        assert_eq!(rows[1]["harness"], "hermes");
        assert!(rows[0]["harness"].is_null());
        assert_eq!(rows[0]["parent_chat_id"], "parent");
        assert!(rows[1]["parent_chat_id"].is_null());

        let link = rows[0]["deep_link"].as_str().unwrap();
        assert!(link.starts_with("zeron://open/chat/"));
        assert!(link.ends_with(&format!("?workspace={locator}")));
    }

    #[test]
    fn unchanged_payload_is_not_rewritten() {
        let dir = fresh_store_dir();
        let chats = [minimal_chat("stable-chat")];
        assert!(write_chats_index(&dir, "dev", "", "", true, &chats).unwrap());
        let before = std::fs::read(dir.join(INDEX_FILE)).unwrap();
        let mtime = std::fs::metadata(dir.join(INDEX_FILE))
            .unwrap()
            .modified()
            .ok();
        assert!(!write_chats_index(&dir, "dev", "", "", true, &chats).unwrap());
        let after = std::fs::read(dir.join(INDEX_FILE)).unwrap();
        assert_eq!(before, after);
        if let Some(mtime) = mtime {
            assert_eq!(
                std::fs::metadata(dir.join(INDEX_FILE)).unwrap().modified().ok(),
                Some(mtime)
            );
        }
    }

    #[test]
    fn write_is_atomic_over_existing_file() {
        let dir = fresh_store_dir();
        let path = dir.join(INDEX_FILE);
        std::fs::write(&path, "sentinel\n").expect("seed");
        let chats = [minimal_chat("after-sentinel")];
        assert!(write_chats_index(&dir, "dev", "", "", true, &chats).unwrap());
        let bytes = std::fs::read(&path).expect("read");
        assert_ne!(bytes, b"sentinel\n");
        serde_json::from_slice::<serde_json::Value>(&bytes).expect("valid json");
        assert!(!dir.join(format!("{INDEX_FILE}.tmp")).exists());
    }
}