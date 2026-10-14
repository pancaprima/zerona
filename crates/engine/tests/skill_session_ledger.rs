//! Hermes skill load ledger fold tests (synthetic fixtures — not live ACP traffic).

use zeron_proto::skill::{
    HermesSkillLoadConfidence, HermesSkillLoadMeta, HermesSkillLoadStatus,
    HermesSkillLoadsCapability, HERMES_SKILL_LOAD_SCHEMA_VERSION,
};
use zeron_proto::AgentEvent;
use zeron_engine::skill_sessions::{replay_events, SkillLoadLedger};

fn sample_meta(event_id: &str, terminal: &str) -> HermesSkillLoadMeta {
    HermesSkillLoadMeta {
        schema_version: HERMES_SKILL_LOAD_SCHEMA_VERSION,
        event_id: event_id.to_string(),
        load_name: "demo-skill".into(),
        display_name: Some("Demo".into()),
        file_path: None,
        terminal_identity: terminal.into(),
        outcome: "success".into(),
        setup_needed: false,
        dedup: false,
        content_returned: true,
        source: "prompt_select".into(),
        resolver_epoch: "epoch1".into(),
        profile_key: "profile1".into(),
        acp_session_id: "acp-1".into(),
        tool_call_id: Some("tc-1".into()),
        turn_epoch: 0,
        process_epoch: "proc-1".into(),
        child_session_key: None,
    }
}

#[test]
fn authoritative_primary_load_goes_to_confirmed() {
    let events = vec![AgentEvent::HermesSkillLoad {
        meta: sample_meta("e1", "primary"),
    }];
    let ledger = replay_events(&events, HermesSkillLoadsCapability::WireV2Acp);
    let snap = ledger.snapshot();
    assert_eq!(snap.confirmed.len(), 1);
    assert_eq!(snap.confirmed[0].status, HermesSkillLoadStatus::LoadedPrimary);
    assert_eq!(
        snap.confirmed[0].confidence,
        HermesSkillLoadConfidence::WireAuthoritative
    );
    assert!(snap.failed.is_empty());
}

#[test]
fn failed_load_never_enters_confirmed() {
    let mut meta = sample_meta("e2", "failed");
    meta.outcome = "failed".into();
    let events = vec![AgentEvent::HermesSkillLoad { meta }];
    let ledger = replay_events(&events, HermesSkillLoadsCapability::WireV2Acp);
    let snap = ledger.snapshot();
    assert!(snap.confirmed.is_empty());
    assert_eq!(snap.failed.len(), 1);
}

#[test]
fn replay_is_idempotent_on_event_id() {
    let events = vec![
        AgentEvent::HermesSkillLoad {
            meta: sample_meta("e3", "primary"),
        },
        AgentEvent::HermesSkillLoad {
            meta: sample_meta("e3", "reference"),
        },
    ];
    let ledger = replay_events(&events, HermesSkillLoadsCapability::WireV2Acp);
    let snap = ledger.snapshot();
    assert_eq!(snap.confirmed.len(), 1);
}

#[test]
fn late_success_upgrades_provisional_failed() {
    let mut failed = sample_meta("e4", "failed");
    failed.outcome = "failed".into();
    let events = vec![
        AgentEvent::HermesSkillLoad { meta: failed },
        AgentEvent::HermesSkillLoad {
            meta: sample_meta("e4", "primary"),
        },
    ];
    let ledger = replay_events(&events, HermesSkillLoadsCapability::WireV2Acp);
    let snap = ledger.snapshot();
    assert_eq!(snap.confirmed.len(), 1);
    assert!(snap.failed.is_empty());
}

#[test]
fn confirmed_load_resists_late_abandonment() {
    let mut abandoned = sample_meta("e5", "failed");
    abandoned.outcome = "failed".into();
    let events = vec![
        AgentEvent::HermesSkillLoad {
            meta: sample_meta("e5", "primary"),
        },
        AgentEvent::HermesSkillLoad { meta: abandoned },
    ];
    let ledger = replay_events(&events, HermesSkillLoadsCapability::WireV2Acp);
    let snap = ledger.snapshot();
    assert_eq!(snap.confirmed.len(), 1);
    assert!(snap.failed.is_empty());
}

#[test]
fn structured_skill_view_with_session_profile_is_confirmed() {
    let mut meta = sample_meta("e-structured-sv", "primary");
    meta.source = "skill_view".into();
    let ledger = replay_events(
        &[AgentEvent::HermesSkillLoad { meta }],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    let snap = ledger.snapshot();
    assert_eq!(snap.confirmed.len(), 1);
    assert!(snap.observed_reads.is_empty());
}

#[test]
fn repeated_event_id_in_two_profiles_keeps_both_partitions() {
    let mut profile_a = sample_meta("shared-id", "primary");
    profile_a.profile_key = "profile-a".into();
    let mut profile_b = sample_meta("shared-id", "reference");
    profile_b.profile_key = "profile-b".into();
    profile_b.load_name = "other-skill".into();
    let ledger = replay_events(
        &[
            AgentEvent::HermesSkillLoad { meta: profile_a },
            AgentEvent::HermesSkillLoad { meta: profile_b },
        ],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    assert_eq!(ledger.to_doc().records.len(), 2);
    let doc = ledger.to_doc();
    let mut reopened = SkillLoadLedger::new(HermesSkillLoadsCapability::ObservedToolsV0);
    reopened.hydrate_from_doc(&doc);
    assert_eq!(reopened.to_doc().records.len(), 2);
    assert_eq!(reopened.snapshot().confirmed.len(), 1);
    assert_eq!(
        reopened.snapshot().confirmed[0].status,
        HermesSkillLoadStatus::LoadedReference
    );
}

#[test]
fn profile_a_b_a_switch_isolates_active_snapshot_without_dropping_history() {
    let mut a1 = sample_meta("e1", "primary");
    a1.profile_key = "profile-a".into();
    let mut b1 = sample_meta("e2", "primary");
    b1.profile_key = "profile-b".into();
    b1.load_name = "skill-b".into();
    let mut a2 = sample_meta("e3", "reference");
    a2.profile_key = "profile-a".into();
    a2.load_name = "skill-a2".into();
    let ledger = replay_events(
        &[
            AgentEvent::HermesSkillLoad { meta: a1 },
            AgentEvent::HermesSkillLoad { meta: b1 },
            AgentEvent::HermesSkillLoad { meta: a2 },
        ],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    assert_eq!(ledger.to_doc().records.len(), 3);
    assert_eq!(ledger.snapshot().confirmed.len(), 2);
    assert!(
        ledger
            .snapshot()
            .confirmed
            .iter()
            .all(|row| row.profile_key.as_deref() == Some("profile-a"))
    );
}

#[test]
fn identical_metadata_replay_does_not_bump_generation() {
    let events = vec![AgentEvent::HermesSkillLoad {
        meta: sample_meta("gen-stable", "primary"),
    }];
    let mut ledger = replay_events(&events, HermesSkillLoadsCapability::WireV2Acp);
    assert_eq!(ledger.snapshot().generation, 1);
    ledger.apply_event(&events[0], Some("acp-1"));
    assert_eq!(ledger.snapshot().generation, 1);
}

#[test]
fn foreign_profile_on_wrong_wire_session_is_rejected() {
    let ledger = replay_events(
        &[AgentEvent::HermesSkillLoad {
            meta: sample_meta("keep", "primary"),
        }],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    let mut foreign = sample_meta("leak", "primary");
    foreign.profile_key = "profile-other".into();
    foreign.acp_session_id = "wrong-session".into();
    let mut ledger = ledger;
    ledger.apply_event(&AgentEvent::HermesSkillLoad { meta: foreign }, Some("acp-1"));
    assert_eq!(ledger.to_doc().records.len(), 1);
    assert_eq!(ledger.snapshot().confirmed.len(), 1);
    assert_eq!(
        ledger.snapshot().confirmed[0].profile_key.as_deref(),
        Some("profile1")
    );
}

#[test]
fn cross_profile_switch_keeps_all_partition_records_in_doc() {
    let mut profile_a = sample_meta("e-a", "primary");
    profile_a.profile_key = "profile-a".into();
    let mut profile_b = sample_meta("e-b", "primary");
    profile_b.profile_key = "profile-b".into();
    profile_b.load_name = "other-skill".into();
    let ledger = replay_events(
        &[
            AgentEvent::HermesSkillLoad { meta: profile_a },
            AgentEvent::HermesSkillLoad { meta: profile_b },
        ],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    assert_eq!(ledger.to_doc().records.len(), 2);
    assert_eq!(ledger.snapshot().confirmed.len(), 1);
    assert_eq!(ledger.snapshot().confirmed[0].profile_key.as_deref(), Some("profile-b"));
}

#[test]
fn invalid_meta_does_not_erase_prior_history() {
    let ledger = replay_events(
        &[AgentEvent::HermesSkillLoad {
            meta: sample_meta("keep", "primary"),
        }],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    let mut bad = sample_meta("bad", "primary");
    bad.source = "wire_loaded".into();
    bad.outcome = "mystery".into();
    let mut ledger = ledger;
    ledger.apply_event(
        &AgentEvent::HermesSkillLoad { meta: bad },
        Some("acp-1"),
    );
    assert_eq!(ledger.snapshot().confirmed.len(), 1);
}

#[test]
fn skill_loads_meta_persists_through_doc_host_store_reopen() {
    use std::sync::Arc;
    use zeron_engine::{DocHost, DocHostConfig};
    use zeron_proto::HarnessId;
    use zeron_sync::DocsStore;

    const CHAT: &str = "skill-loads-disk";
    let dir = tempfile::tempdir().unwrap();
    let store = Arc::new(DocsStore::open(dir.path()).unwrap());
    let config = DocHostConfig {
        device_id: "test-device".into(),
        default_harness: HarnessId::Hermes,
        edge: None,
    };
    let host = DocHost::new(store.clone(), config.clone());
    let handle = host.open(CHAT).unwrap();
    assert!(handle.doc().read_entries().unwrap().is_empty());

    let mut profile_a = sample_meta("disk-a", "primary");
    profile_a.profile_key = "profile-a".into();
    let mut profile_b = sample_meta("disk-b", "primary");
    profile_b.profile_key = "profile-b".into();
    profile_b.load_name = "other-skill".into();
    let ledger = replay_events(
        &[
            AgentEvent::HermesSkillLoad { meta: profile_a },
            AgentEvent::HermesSkillLoad { meta: profile_b },
        ],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    assert_eq!(ledger.snapshot().generation, 2);
    handle.doc().set_skill_loads(&ledger.to_doc()).unwrap();
    let bytes = handle.doc().export_snapshot().unwrap();
    store.save_snapshot(CHAT, &bytes).unwrap();
    drop(handle);
    drop(host);

    let reopened = DocHost::new(store.clone(), config);
    let handle = reopened.open(CHAT).unwrap();
    assert!(handle.doc().read_entries().unwrap().is_empty());
    let persisted = handle
        .doc()
        .skill_loads()
        .expect("skillLoads meta must survive store reopen");
    assert_eq!(persisted.records.len(), 2);
    let mut rehydrated = SkillLoadLedger::new(HermesSkillLoadsCapability::ObservedToolsV0);
    rehydrated.hydrate_from_doc(&persisted);
    assert_eq!(rehydrated.to_doc().records.len(), 2);
    assert_eq!(rehydrated.snapshot().generation, 2);
    assert_eq!(rehydrated.snapshot().confirmed.len(), 1);
    assert_eq!(
        rehydrated.snapshot().confirmed[0].profile_key.as_deref(),
        Some("profile-b")
    );
}

#[test]
fn doc_roundtrip_rehydrates_full_partition_history() {
    let ledger = replay_events(
        &[AgentEvent::HermesSkillLoad {
            meta: sample_meta("persist", "reference"),
        }],
        HermesSkillLoadsCapability::WireV2Acp,
    );
    let doc = ledger.to_doc();
    let mut reopened = SkillLoadLedger::new(HermesSkillLoadsCapability::ObservedToolsV0);
    reopened.hydrate_from_doc(&doc);
    assert_eq!(reopened.to_doc().records.len(), 1);
    assert_eq!(
        reopened.snapshot().confirmed[0].status,
        HermesSkillLoadStatus::LoadedReference
    );
}

#[test]
fn companion_notification_and_tool_mapper_share_ledger_contract() {
    use serde_json::json;
    use zeron_harness::acp::hermes_skill_wire;

    let skill_load = json!({
        "schemaVersion": HERMES_SKILL_LOAD_SCHEMA_VERSION,
        "eventId": "companion-e1",
        "loadName": "review-skill",
        "terminalIdentity": "reference",
        "outcome": "success",
        "source": "skill_view",
        "resolverEpoch": "epoch1",
        "profileKey": "profile1",
        "acpSessionId": "acp-1",
        "processEpoch": "proc-1",
        "filePath": "skills/review/SKILL.md"
    });
    let notification = json!({
        "sessionId": "acp-1",
        "_meta": { "hermes": { "skillLoad": skill_load.clone() } }
    });
    let tool_update = json!({
        "sessionUpdate": "tool_call_update",
        "toolCallId": "tc-1",
        "status": "completed",
        "_meta": { "hermes": { "skillLoad": skill_load } }
    });
    let from_notification =
        hermes_skill_wire::map_skill_loaded_notification(&notification, "acp-1")
            .expect("notification");
    let from_tool = hermes_skill_wire::map_skill_load_from_tool_update(&tool_update, "acp-1")
        .expect("tool meta");
    for event in [from_notification, from_tool] {
        let ledger = replay_events(&[event], HermesSkillLoadsCapability::WireV2Acp);
        let snap = ledger.snapshot();
        assert_eq!(snap.confirmed.len(), 1);
        assert_eq!(snap.confirmed[0].status, HermesSkillLoadStatus::LoadedReference);
    }

    use zeron_harness::acp::normalize::map_update;
    let mapped = map_update(&tool_update, "acp-1");
    assert_eq!(mapped.len(), 1);
    let ledger = replay_events(&mapped, HermesSkillLoadsCapability::WireV2Acp);
    assert_eq!(ledger.snapshot().confirmed.len(), 1);
    assert_eq!(
        ledger.snapshot().confirmed[0].profile_key.as_deref(),
        Some("profile1")
    );
}

#[test]
fn tool_observed_skill_view_stays_degraded() {
    let mut meta = sample_meta("e6", "primary");
    meta.source = "skill_view".into();
    meta.process_epoch = String::new();
    meta.profile_key = String::new();
    meta.acp_session_id = String::new();
    let ledger = replay_events(
        &[AgentEvent::HermesSkillLoad { meta }],
        HermesSkillLoadsCapability::ObservedToolsV0,
    );
    let snap = ledger.snapshot();
    assert!(snap.confirmed.is_empty());
    assert_eq!(snap.observed_reads.len(), 1);
}
