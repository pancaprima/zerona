//! Hermes ACP `_hermes/skills/*` wire helpers and live-session registry.

use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use serde_json::{Value, json};

use zeron_proto::skill::{
    HermesSkillCatalogResponse, HermesSkillLoadMeta, HermesSkillSelectResponse,
    HermesSkillLoadsCapability, HERMES_SKILL_LOAD_SCHEMA_VERSION, validate_skill_load_meta,
};
use zeron_proto::AgentEvent;

use crate::jsonrpc::RpcClient;
use crate::HarnessError;

static LIVE_WIRE: OnceLock<Mutex<HashMap<String, LiveHermesSkillWire>>> = OnceLock::new();

fn live_wire() -> &'static Mutex<HashMap<String, LiveHermesSkillWire>> {
    LIVE_WIRE.get_or_init(|| Mutex::new(HashMap::new()))
}

pub(crate) fn wire_map_key(client: &RpcClient, acp_session_id: &str) -> String {
    format!("{}:{}", client.transport_key(), acp_session_id)
}

#[derive(Clone)]
pub(crate) struct LiveHermesSkillWire {
    pub client: RpcClient,
    pub capability: HermesSkillLoadsCapability,
    pub resolver_epoch: Option<String>,
    pub profile_key: Option<String>,
    pub generation: u64,
}

/// RAII cleanup for the live wire map — every early return must drop this guard.
pub(crate) struct LiveWireGuard {
    key: String,
    generation: u64,
}

impl Drop for LiveWireGuard {
    fn drop(&mut self) {
        let mut map = live_wire().lock().expect("hermes skill wire");
        if map
            .get(&self.key)
            .is_some_and(|entry| entry.generation == self.generation)
        {
            map.remove(&self.key);
        }
    }
}

pub(crate) fn register_live_wire(
    acp_session_id: &str,
    client: RpcClient,
    capability: HermesSkillLoadsCapability,
    resolver_epoch: Option<String>,
    profile_key: Option<String>,
) -> LiveWireGuard {
    let key = wire_map_key(&client, acp_session_id);
    let generation = if acp_session_id.is_empty() {
        0
    } else {
        live_wire()
            .lock()
            .expect("hermes skill wire")
            .get(&key)
            .map(|e| e.generation + 1)
            .unwrap_or(1)
    };
    if !acp_session_id.is_empty() {
        live_wire()
            .lock()
            .expect("hermes skill wire")
            .insert(
                key.clone(),
                LiveHermesSkillWire {
                    client,
                    capability,
                    resolver_epoch,
                    profile_key,
                    generation,
                },
            );
    }
    LiveWireGuard { key, generation }
}

pub(crate) fn live_wire_for_session(
    client: &RpcClient,
    acp_session_id: &str,
) -> Option<LiveHermesSkillWire> {
    let key = wire_map_key(client, acp_session_id);
    live_wire()
        .lock()
        .expect("hermes skill wire")
        .get(&key)
        .cloned()
}

pub(crate) fn live_wire_for_session_id(
    acp_session_id: &str,
) -> Result<LiveHermesSkillWire, HarnessError> {
    let suffix = format!(":{}", acp_session_id);
    let matches: Vec<LiveHermesSkillWire> = live_wire()
        .lock()
        .expect("hermes skill wire")
        .iter()
        .filter(|(key, _)| key.ends_with(&suffix))
        .map(|(_, wire)| wire.clone())
        .collect();
    match matches.len() {
        0 => Err(HarnessError::Protocol(
            "Hermes ACP session is not connected on this device".into(),
        )),
        1 => Ok(matches.into_iter().next().expect("len checked")),
        _ => Err(HarnessError::Protocol(
            "ambiguous Hermes ACP session wire — multiple child agents share this session id"
                .into(),
        )),
    }
}

pub(crate) fn update_wire_catalog_meta(
    client: &RpcClient,
    acp_session_id: &str,
    resolver_epoch: &str,
    profile_key: &str,
) {
    let key = wire_map_key(client, acp_session_id);
    if let Some(entry) = live_wire().lock().expect("hermes skill wire").get_mut(&key) {
        entry.resolver_epoch = Some(resolver_epoch.to_string());
        entry.profile_key = Some(profile_key.to_string());
    }
}

pub(crate) fn hermes_skill_loads_capability_from_initialize(params: &Value) -> HermesSkillLoadsCapability {
    let skills = params
        .pointer("/agentCapabilities/_meta/hermes/skills")
        .or_else(|| params.pointer("/_meta/hermes/skills"));
    let Some(skills) = skills else {
        return HermesSkillLoadsCapability::ObservedToolsV0;
    };
    let schema_ok = skills
        .get("schemaVersion")
        .and_then(Value::as_u64)
        .is_none_or(|v| v == HERMES_SKILL_LOAD_SCHEMA_VERSION as u64);
    let methods = skills
        .get("methods")
        .and_then(Value::as_array)
        .map(|a| {
            a.iter()
                .filter_map(Value::as_str)
                .collect::<std::collections::HashSet<_>>()
        })
        .unwrap_or_default();
    let has_core = ["catalog", "inspect", "select"]
        .iter()
        .all(|m| methods.contains(*m));
    if schema_ok && has_core {
        HermesSkillLoadsCapability::WireV2Acp
    } else {
        HermesSkillLoadsCapability::ObservedToolsV0
    }
}

const HERMES_EXT_TIMEOUT: Duration = Duration::from_secs(30);

pub(crate) async fn ext_method(
    client: &RpcClient,
    method: &str,
    params: Value,
) -> Result<Value, HarnessError> {
    let wire = format!("_{}", method.trim_start_matches('_'));
    let fut = client.request(&wire, params);
    match tokio::time::timeout(HERMES_EXT_TIMEOUT, fut).await {
        Ok(result) => result,
        Err(_) => Err(HarnessError::Protocol(format!(
            "{wire}: timed out after {}s",
            HERMES_EXT_TIMEOUT.as_secs()
        ))),
    }
}

pub(crate) async fn catalog_page(
    client: &RpcClient,
    session_id: &str,
    cursor: Option<&str>,
) -> Result<Value, HarnessError> {
    ext_method(
        client,
        "hermes/skills/catalog",
        json!({
            "sessionId": session_id,
            "schemaVersion": HERMES_SKILL_LOAD_SCHEMA_VERSION,
            "pagination": { "cursor": cursor, "limit": 200 },
            "includeDisabled": false,
            "includeHiddenTierAliases": false,
        }),
    )
    .await
}

pub(crate) async fn catalog(
    client: &RpcClient,
    session_id: &str,
) -> Result<HermesSkillCatalogResponse, HarnessError> {
    let mut cursor: Option<String> = None;
    let mut merged: Option<HermesSkillCatalogResponse> = None;
    let mut pages = 0usize;
    while pages < 64 {
        pages += 1;
        let value = catalog_page(client, session_id, cursor.as_deref()).await?;
        let page: HermesSkillCatalogResponse = serde_json::from_value(value).map_err(|error| {
            HarnessError::Protocol(format!("invalid Hermes skill catalog page: {error}"))
        })?;
        match merged {
            None => merged = Some(page),
            Some(mut acc) => {
                if acc.resolver_epoch != page.resolver_epoch
                    || acc.profile_key != page.profile_key
                {
                    return Err(HarnessError::Protocol(
                        "Hermes skill catalog resolver epoch changed mid-pagination".into(),
                    ));
                }
                acc.entries.extend(page.entries);
                acc.partial_failures.extend(page.partial_failures);
                acc.next_cursor = page.next_cursor;
                merged = Some(acc);
            }
        }
        let next = merged.as_ref().and_then(|c| c.next_cursor.clone());
        if next.as_deref().is_none_or(|c| c.is_empty()) {
            break;
        }
        cursor = next;
    }
    merged.ok_or_else(|| HarnessError::Protocol("empty Hermes skill catalog".into()))
}

pub(crate) async fn inspect(
    client: &RpcClient,
    session_id: &str,
    load_name: &str,
) -> Result<Value, HarnessError> {
    ext_method(
        client,
        "hermes/skills/inspect",
        json!({
            "sessionId": session_id,
            "schemaVersion": HERMES_SKILL_LOAD_SCHEMA_VERSION,
            "loadName": load_name,
        }),
    )
    .await
}

/// Inspect a Hermes skill on a live ACP session (engine/UI facade — wire stays internal).
pub async fn inspect_skill_for_session(
    acp_session_id: &str,
    load_name: &str,
) -> Result<zeron_proto::skill::HermesSkillInspectResponse, HarnessError> {
    let wire = live_wire_for_session_id(acp_session_id)?;
    let value = inspect(&wire.client, acp_session_id, load_name).await?;
    serde_json::from_value(value).map_err(|error| {
        HarnessError::Protocol(format!("invalid Hermes skill inspect response: {error}"))
    })
}

pub(crate) async fn select_skills(
    client: &RpcClient,
    session_id: &str,
    selections: Vec<(String, Option<String>)>,
    operation_id: &str,
    resolver_epoch: Option<&str>,
) -> Result<Value, HarnessError> {
    let selections_json: Vec<Value> = selections
        .into_iter()
        .map(|(load_name, file_path)| {
            json!({
                "loadName": load_name,
                "filePath": file_path,
            })
        })
        .collect();
    let mut params = json!({
        "sessionId": session_id,
        "schemaVersion": HERMES_SKILL_LOAD_SCHEMA_VERSION,
        "selections": selections_json,
    });
    params["operationId"] = json!(operation_id);
    if let Some(epoch) = resolver_epoch {
        params["resolverEpoch"] = json!(epoch);
    }
    let value = ext_method(client, "hermes/skills/select", params).await?;
    let response: HermesSkillSelectResponse = serde_json::from_value(value.clone()).map_err(|error| {
        HarnessError::Protocol(format!("invalid Hermes skill select response: {error}"))
    })?;
    if !response.accepted || !response.queued_for_prompt {
        return Err(HarnessError::Protocol(
            "Hermes skill select was not accepted for the next prompt boundary".into(),
        ));
    }
    Ok(value)
}

pub fn map_skill_loaded_notification(
    params: &Value,
    session_id: &str,
) -> Option<AgentEvent> {
    if params.get("sessionId").and_then(Value::as_str) != Some(session_id) {
        return None;
    }
    let meta = parse_skill_load_meta(params)?;
    if !validate_skill_load_meta(&meta, Some(session_id), None) {
        return None;
    }
    Some(AgentEvent::HermesSkillLoad { meta })
}

pub(crate) fn parse_skill_load_meta(params: &Value) -> Option<HermesSkillLoadMeta> {
    let skill_load = params
        .pointer("/_meta/hermes/skillLoad")
        .or_else(|| params.pointer("/meta/hermes/skillLoad"))?;
    serde_json::from_value(skill_load.clone()).ok()
}

fn tool_call_id_from_update(update: &Value) -> Option<String> {
    update
        .get("toolCallId")
        .or_else(|| update.get("tool_call_id"))
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
        .map(str::to_owned)
}

pub fn map_skill_load_from_tool_update(
    update: &Value,
    wire_session_id: &str,
) -> Option<AgentEvent> {
    let mut meta = skill_load_meta_from_tool_update(update)?;
    if meta.tool_call_id.is_none() {
        meta.tool_call_id = tool_call_id_from_update(update);
    }
    if !validate_skill_load_meta(&meta, Some(wire_session_id), None) {
        return None;
    }
    Some(AgentEvent::HermesSkillLoad { meta })
}

pub(crate) fn skill_load_meta_from_tool_update(update: &Value) -> Option<HermesSkillLoadMeta> {
    update
        .get("_meta")
        .and_then(|m| m.get("hermes"))
        .and_then(|h| h.get("skillLoad"))
        .and_then(|v| serde_json::from_value(v.clone()).ok())
}

pub(crate) fn map_observed_skill_view(
    update: &Value,
    tool_name: &str,
) -> Option<AgentEvent> {
    if !tool_name.contains("skill_view") {
        return None;
    }
    if skill_load_meta_from_tool_update(update).is_some() {
        return None;
    }
    let status = update
        .get("status")
        .and_then(Value::as_str)
        .unwrap_or("");
    if status != "completed" && status != "failed" {
        return None;
    }
    let raw = update.get("rawInput").or_else(|| update.get("raw_input"));
    let load_name = raw
        .and_then(|r| r.get("name"))
        .and_then(Value::as_str)
        .unwrap_or("skill_view");
    let event_id = format!(
        "observed:{}:{}",
        update
            .get("toolCallId")
            .or_else(|| update.get("tool_call_id"))
            .and_then(Value::as_str)
            .unwrap_or("?"),
        load_name
    );
    Some(AgentEvent::HermesSkillLoad {
        meta: HermesSkillLoadMeta {
            schema_version: HERMES_SKILL_LOAD_SCHEMA_VERSION,
            event_id,
            load_name: load_name.to_string(),
            display_name: None,
            file_path: raw
                .and_then(|r| r.get("file_path"))
                .and_then(Value::as_str)
                .map(str::to_owned),
            terminal_identity: if status == "failed" {
                "failed".into()
            } else {
                "primary".into()
            },
            outcome: if status == "failed" {
                "failed".into()
            } else {
                "success".into()
            },
            setup_needed: false,
            dedup: false,
            content_returned: status == "completed",
            source: "skill_view".into(),
            resolver_epoch: String::new(),
            profile_key: String::new(),
            acp_session_id: String::new(),
            tool_call_id: update
                .get("toolCallId")
                .or_else(|| update.get("tool_call_id"))
                .and_then(Value::as_str)
                .map(str::to_owned),
            turn_epoch: 0,
            process_epoch: String::new(),
            child_session_key: None,
        },
    })
}

pub fn hermes_skill_selections_from_prompt(
    prompt: &str,
) -> Vec<(String, Option<String>)> {
    use zeron_proto::invocation::{invocation_links, Invocation};
    use zeron_proto::skill::{decode_hermes_skill_uri, is_hermes_native_skill_path};
    let mut out = Vec::new();
    for (_, inv) in invocation_links(prompt) {
        if let Invocation::Skill { path, .. } = inv {
            if is_hermes_native_skill_path(&path) {
                let load_name = decode_hermes_skill_uri(&path).unwrap_or_default();
                if !load_name.is_empty() {
                    out.push((load_name, None));
                }
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use zeron_proto::skill::{HermesSkillLoadStatus, ledger_status_from_meta};

    #[test]
    fn decode_uri_never_panics_on_unicode_slice() {
        let uri = "harness-skill:hermes:%E9";
        assert!(zeron_proto::skill::decode_hermes_skill_uri(uri).is_none());
    }

    #[test]
    fn tool_update_skill_load_meta_maps_when_session_matches() {
        let update = json!({
            "sessionUpdate": "tool_call_update",
            "toolCallId": "tc-1",
            "status": "completed",
            "_meta": { "hermes": { "skillLoad": {
                "schemaVersion": HERMES_SKILL_LOAD_SCHEMA_VERSION,
                "eventId": "e-tool",
                "loadName": "demo",
                "terminalIdentity": "primary",
                "outcome": "success",
                "source": "auto_load",
                "resolverEpoch": "r1",
                "profileKey": "p1",
                "acpSessionId": "sess-1",
                "processEpoch": "proc-1"
            }}}
        });
        let event = map_skill_load_from_tool_update(&update, "sess-1").expect("meta");
        assert!(matches!(event, AgentEvent::HermesSkillLoad { .. }));
    }

    #[test]
    fn ledger_status_matches_proto_helper() {
        let meta = HermesSkillLoadMeta {
            schema_version: 1,
            event_id: "e".into(),
            load_name: "x".into(),
            display_name: None,
            file_path: None,
            terminal_identity: "reference".into(),
            outcome: "success".into(),
            setup_needed: false,
            dedup: false,
            content_returned: true,
            source: "skill_view".into(),
            resolver_epoch: String::new(),
            profile_key: String::new(),
            acp_session_id: String::new(),
            tool_call_id: None,
            turn_epoch: 0,
            process_epoch: String::new(),
            child_session_key: None,
        };
        assert_eq!(
            ledger_status_from_meta(&meta),
            HermesSkillLoadStatus::LoadedReference
        );
    }
}
