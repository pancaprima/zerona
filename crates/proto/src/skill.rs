//! Hermes skill catalog / session load wire types (ACP `_hermes/skills/*`).

use serde::{Deserialize, Serialize};

pub const HERMES_SKILL_URI_PREFIX: &str = "harness-skill:hermes:";
pub const HERMES_SKILL_LOAD_SCHEMA_VERSION: u32 = 1;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HermesSkillLoadsCapability {
    WireV2Acp,
    #[default]
    ObservedToolsV0,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HermesSkillCatalogMode {
    Authoritative,
    FilesystemDegraded,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillIdentity {
    pub kind: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub path: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub plugin_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub qualified_name: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillCatalogEntry {
    pub load_name: String,
    pub display_name: String,
    pub identity: HermesSkillIdentity,
    pub tier: String,
    pub enabled: bool,
    #[serde(default)]
    pub hidden: bool,
    pub readiness: String,
    #[serde(default)]
    pub setup_needed: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub description: Option<String>,
    #[serde(default)]
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillCatalogResponse {
    pub schema_version: u32,
    pub resolver_epoch: String,
    pub profile_key: String,
    pub entries: Vec<HermesSkillCatalogEntry>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub next_cursor: Option<String>,
    #[serde(default)]
    pub partial_failures: Vec<HermesSkillPartialFailure>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillPartialFailure {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub load_name: Option<String>,
    pub code: String,
    pub message: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillInspectResponse {
    pub schema_version: u32,
    pub load_name: String,
    pub identity: HermesSkillIdentity,
    pub readiness: String,
    #[serde(default)]
    pub setup_needed: bool,
    pub markdown_excerpt: String,
    pub excerpt_truncated: bool,
    pub resolver_epoch: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillSelectSelection {
    pub load_name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub file_path: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillSelectResponse {
    pub accepted: bool,
    pub queued_for_prompt: bool,
    pub canonical_selections: Vec<HermesSkillSelectSelection>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HermesSkillLoadConfidence {
    WireAuthoritative,
    ToolObserved,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HermesSkillLoadStatus {
    Selected,
    PendingDelivery,
    Accepted,
    LoadedPrimary,
    LoadedReference,
    LoadedUnchanged,
    LoadedSetupNeeded,
    Failed,
    Abandoned,
    ToolObservedRead,
    Unobservable,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillLoadMeta {
    pub schema_version: u32,
    pub event_id: String,
    pub load_name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub display_name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub file_path: Option<String>,
    pub terminal_identity: String,
    pub outcome: String,
    #[serde(default)]
    pub setup_needed: bool,
    #[serde(default)]
    pub dedup: bool,
    #[serde(default)]
    pub content_returned: bool,
    pub source: String,
    pub resolver_epoch: String,
    pub profile_key: String,
    pub acp_session_id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tool_call_id: Option<String>,
    #[serde(default)]
    pub turn_epoch: u64,
    pub process_epoch: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub child_session_key: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillLoadRecord {
    pub event_id: String,
    pub load_name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub display_name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub file_path: Option<String>,
    pub status: HermesSkillLoadStatus,
    pub confidence: HermesSkillLoadConfidence,
    #[serde(default)]
    pub setup_needed: bool,
    #[serde(default)]
    pub historical: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tool_call_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub profile_key: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub acp_session_id: Option<String>,
    /// Opaque dedup scope (`profileKey` + `acpSessionId` + producer `eventId`).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub record_scope: Option<String>,
}

/// Stable scoped dedup key for ledger rows (survives doc round-trip).
pub fn skill_load_record_scope(
    profile_key: &str,
    acp_session_id: &str,
    event_id: &str,
) -> String {
    format!("{profile_key}\0{acp_session_id}\0{event_id}")
}

pub fn skill_load_record_scope_for_meta(meta: &HermesSkillLoadMeta) -> String {
    skill_load_record_scope(&meta.profile_key, &meta.acp_session_id, &meta.event_id)
}

pub fn skill_load_record_scope_for_record(rec: &HermesSkillLoadRecord) -> String {
    if let Some(scope) = &rec.record_scope {
        return scope.clone();
    }
    skill_load_record_scope(
        rec.profile_key.as_deref().unwrap_or(""),
        rec.acp_session_id.as_deref().unwrap_or(""),
        &rec.event_id,
    )
}

#[derive(Debug, Clone, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HermesSkillSessionLoads {
    pub capability: HermesSkillLoadsCapability,
    pub schema_version: u32,
    #[serde(default)]
    pub generation: u64,
    #[serde(default)]
    pub confirmed: Vec<HermesSkillLoadRecord>,
    #[serde(default)]
    pub failed: Vec<HermesSkillLoadRecord>,
    #[serde(default)]
    pub observed_reads: Vec<HermesSkillLoadRecord>,
    #[serde(default)]
    pub catalog_mode: Option<HermesSkillCatalogMode>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub resolver_epoch: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SkillCatalogEnvelope {
    pub skills: Vec<crate::invocation::Skill>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub catalog_mode: Option<HermesSkillCatalogMode>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub hermes_skill_loads: Option<HermesSkillLoadsCapability>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub resolver_epoch: Option<String>,
}

/// Stable opaque id for a canonical Hermes `load_name` (percent-encoded, reversible).
pub fn encode_hermes_skill_uri(load_name: &str) -> String {
    let encoded: String = load_name
        .bytes()
        .map(|b| {
            if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
                (b as char).to_string()
            } else {
                format!("%{:02X}", b)
            }
        })
        .collect();
    format!("{HERMES_SKILL_URI_PREFIX}{encoded}")
}

pub fn decode_hermes_skill_uri(path: &str) -> Option<String> {
    let rest = path.strip_prefix(HERMES_SKILL_URI_PREFIX)?;
    let mut out = Vec::new();
    let bytes = rest.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let hex = &rest[i + 1..i + 3];
            if let Ok(v) = u8::from_str_radix(hex, 16) {
                out.push(v);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8(out).ok()
}

pub fn is_hermes_native_skill_path(path: &str) -> bool {
    path.starts_with(HERMES_SKILL_URI_PREFIX)
}

/// Map authoritative Hermes load meta to a ledger status (shared by engine fold).
pub fn ledger_status_from_meta(meta: &HermesSkillLoadMeta) -> HermesSkillLoadStatus {
    if meta.outcome == "failed" {
        return HermesSkillLoadStatus::Failed;
    }
    if meta.outcome == "dedup" || meta.dedup {
        return HermesSkillLoadStatus::LoadedUnchanged;
    }
    if meta.setup_needed && meta.terminal_identity == "setup_needed" {
        return HermesSkillLoadStatus::LoadedSetupNeeded;
    }
    match meta.terminal_identity.as_str() {
        "reference" => HermesSkillLoadStatus::LoadedReference,
        "unchanged" => HermesSkillLoadStatus::LoadedUnchanged,
        "setup_needed" => HermesSkillLoadStatus::LoadedSetupNeeded,
        "failed" => HermesSkillLoadStatus::Failed,
        _ => HermesSkillLoadStatus::LoadedPrimary,
    }
}

/// Strict validation before a load record is applied (wire or replay).
pub fn validate_skill_load_meta(
    meta: &HermesSkillLoadMeta,
    wire_session_id: Option<&str>,
    profile_key: Option<&str>,
) -> bool {
    if meta.schema_version != HERMES_SKILL_LOAD_SCHEMA_VERSION {
        return false;
    }
    if meta.event_id.is_empty() || meta.load_name.is_empty() {
        return false;
    }
    if meta.outcome != "success"
        && meta.outcome != "failed"
        && meta.outcome != "dedup"
    {
        return false;
    }
    if meta.source != "skill_view"
        && meta.source != "prompt_select"
        && meta.source != "wire_loaded"
        && meta.source != "auto_load"
    {
        return false;
    }
    let terminal = meta.terminal_identity.as_str();
    if !matches!(
        terminal,
        "primary" | "reference" | "unchanged" | "setup_needed" | "failed"
    ) {
        return false;
    }
    if meta.source != "skill_view" {
        if meta.profile_key.is_empty() {
            return false;
        }
        if let Some(expected) = wire_session_id {
            if expected.is_empty() || meta.acp_session_id.is_empty() {
                return false;
            }
            if meta.acp_session_id != expected {
                return false;
            }
        }
    } else if let Some(expected) = wire_session_id {
        if !meta.acp_session_id.is_empty() && meta.acp_session_id != expected {
            return false;
        }
    }
    if let Some(partition) = profile_key {
        if !meta.profile_key.is_empty() && meta.profile_key != partition {
            if let Some(expected) = wire_session_id.filter(|session| !session.is_empty()) {
                if meta.acp_session_id.is_empty() || meta.acp_session_id != expected {
                    return false;
                }
            } else {
                return false;
            }
        }
    }
    if meta.child_session_key.is_some() && meta.source != "wire_loaded" {
        return false;
    }
    true
}

/// Whether companion-structured load meta is wire-authoritative (not legacy observed).
pub fn skill_load_meta_is_authoritative(
    meta: &HermesSkillLoadMeta,
    wire_session_id: Option<&str>,
) -> bool {
    if meta.schema_version != HERMES_SKILL_LOAD_SCHEMA_VERSION || meta.process_epoch.is_empty() {
        return false;
    }
    if meta.source == "skill_view" {
        if meta.profile_key.is_empty() || meta.acp_session_id.is_empty() {
            return false;
        }
        return wire_session_id.is_none_or(|expected| meta.acp_session_id == expected);
    }
    matches!(
        meta.source.as_str(),
        "wire_loaded" | "prompt_select" | "auto_load"
    )
}

/// Durable `SessionDoc.meta.skillLoads` projection (no SKILL.md bodies).
#[derive(Debug, Clone, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SessionDocSkillLoads {
    pub schema_version: u32,
    pub capability: HermesSkillLoadsCapability,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub profile_key: Option<String>,
    #[serde(default)]
    pub records: Vec<HermesSkillLoadRecord>,
    #[serde(default)]
    pub generation: u64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub catalog_mode: Option<HermesSkillCatalogMode>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub resolver_epoch: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hermes_skill_uri_roundtrip() {
        for name in ["review", "plugin:foo", "cat/nested", "unicode-é"] {
            let uri = encode_hermes_skill_uri(name);
            assert!(is_hermes_native_skill_path(&uri));
            assert_eq!(decode_hermes_skill_uri(&uri).as_deref(), Some(name));
        }
    }

    #[test]
    fn hermes_skill_load_meta_defaults_deserialize() {
        let json = r#"{"schemaVersion":1,"eventId":"e1","loadName":"x","terminalIdentity":"primary","outcome":"success","source":"skill_view","resolverEpoch":"r","profileKey":"p","acpSessionId":"s","processEpoch":"pe"}"#;
        let meta: HermesSkillLoadMeta = serde_json::from_str(json).unwrap();
        assert_eq!(meta.event_id, "e1");
        assert!(!meta.setup_needed);
    }

    fn structured_meta(source: &str) -> HermesSkillLoadMeta {
        HermesSkillLoadMeta {
            schema_version: HERMES_SKILL_LOAD_SCHEMA_VERSION,
            event_id: "e-auth".into(),
            load_name: "demo".into(),
            display_name: None,
            file_path: None,
            terminal_identity: "primary".into(),
            outcome: "success".into(),
            setup_needed: false,
            dedup: false,
            content_returned: true,
            source: source.into(),
            resolver_epoch: "r1".into(),
            profile_key: "p1".into(),
            acp_session_id: "sess-1".into(),
            tool_call_id: None,
            turn_epoch: 0,
            process_epoch: "proc-1".into(),
            child_session_key: None,
        }
    }

    #[test]
    fn structured_skill_view_is_authoritative_for_session() {
        let meta = structured_meta("skill_view");
        assert!(skill_load_meta_is_authoritative(&meta, Some("sess-1")));
        assert!(!skill_load_meta_is_authoritative(&meta, Some("other")));
    }

    #[test]
    fn cross_partition_requires_bound_wire_session() {
        let mut other = structured_meta("prompt_select");
        other.profile_key = "profile-b".into();
        assert!(validate_skill_load_meta(&other, Some("sess-1"), Some("profile-a")));
        other.acp_session_id = "other".into();
        assert!(!validate_skill_load_meta(&other, Some("sess-1"), Some("profile-a")));
    }

    #[test]
    fn skill_load_record_scope_roundtrip() {
        let scope = skill_load_record_scope("p", "s", "e");
        let rec = HermesSkillLoadRecord {
            event_id: "e".into(),
            load_name: "demo".into(),
            display_name: None,
            file_path: None,
            status: HermesSkillLoadStatus::LoadedPrimary,
            confidence: HermesSkillLoadConfidence::WireAuthoritative,
            setup_needed: false,
            historical: true,
            tool_call_id: None,
            profile_key: Some("p".into()),
            acp_session_id: Some("s".into()),
            record_scope: Some(scope.clone()),
        };
        assert_eq!(skill_load_record_scope_for_record(&rec), scope);
    }

    #[test]
    fn auto_load_and_dedup_outcome_validate() {
        let auto = structured_meta("auto_load");
        assert!(validate_skill_load_meta(&auto, Some("sess-1"), None));
        let mut dedup = structured_meta("wire_loaded");
        dedup.outcome = "dedup".into();
        assert!(validate_skill_load_meta(&dedup, Some("sess-1"), None));
        assert_eq!(
            ledger_status_from_meta(&dedup),
            HermesSkillLoadStatus::LoadedUnchanged
        );
    }
}
