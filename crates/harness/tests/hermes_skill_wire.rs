//! Hermes skill wire helpers (synthetic — no live Hermes process).

use serde_json::json;
use zeron_harness::acp::hermes_skill_wire;
use zeron_proto::skill::HERMES_SKILL_LOAD_SCHEMA_VERSION;

#[test]
fn loaded_notification_requires_matching_session() {
    let params = json!({
        "sessionId": "other",
        "_meta": { "hermes": { "skillLoad": sample_load() } }
    });
    assert!(hermes_skill_wire::map_skill_loaded_notification(&params, "sess-1").is_none());
}

#[test]
fn loaded_notification_maps_for_session() {
    let params = json!({
        "sessionId": "sess-1",
        "_meta": { "hermes": { "skillLoad": sample_load() } }
    });
    let event = hermes_skill_wire::map_skill_loaded_notification(&params, "sess-1");
    assert!(event.is_some());
}

#[test]
fn selections_preserve_multiselect_order() {
    let first = zeron_proto::invocation::Invocation::Skill {
        name: "first".into(),
        path: zeron_proto::skill::encode_hermes_skill_uri("first"),
        command: None,
    };
    let second = zeron_proto::invocation::Invocation::Skill {
        name: "second".into(),
        path: zeron_proto::skill::encode_hermes_skill_uri("second"),
        command: None,
    };
    let prompt = format!("Run {} and {}", first.link(), second.link());
    let picks = hermes_skill_wire::hermes_skill_selections_from_prompt(&prompt);
    assert_eq!(picks.len(), 2);
    assert_eq!(picks[0].0, "first");
    assert_eq!(picks[1].0, "second");
}

fn sample_load() -> serde_json::Value {
    json!({
        "schemaVersion": HERMES_SKILL_LOAD_SCHEMA_VERSION,
        "eventId": "e1",
        "loadName": "demo",
        "terminalIdentity": "primary",
        "outcome": "success",
        "source": "wire_loaded",
        "resolverEpoch": "r1",
        "profileKey": "p1",
        "acpSessionId": "sess-1",
        "processEpoch": "proc-1"
    })
}

#[test]
fn bare_uri_without_invocation_link_is_not_a_selection() {
    let prompt = format!(
        "Run {} and {}",
        zeron_proto::skill::encode_hermes_skill_uri("first"),
        zeron_proto::invocation::Invocation::Skill {
            name: "second".into(),
            path: zeron_proto::skill::encode_hermes_skill_uri("second"),
            command: None,
        }
        .link()
    );
    let picks = hermes_skill_wire::hermes_skill_selections_from_prompt(&prompt);
    assert_eq!(picks.len(), 1);
    assert_eq!(picks[0].0, "second");
}

#[test]
fn bare_skill_name_does_not_infer_selections() {
    assert!(hermes_skill_wire::hermes_skill_selections_from_prompt("review-skill").is_empty());
    let uri = zeron_proto::skill::encode_hermes_skill_uri("review-skill");
    let prompt = format!(
        "Run {}",
        zeron_proto::invocation::Invocation::Skill {
            name: "review-skill".into(),
            path: uri,
            command: None,
        }
        .link()
    );
    let picks = hermes_skill_wire::hermes_skill_selections_from_prompt(&prompt);
    assert_eq!(picks.len(), 1);
    assert_eq!(picks[0].0, "review-skill");
}

