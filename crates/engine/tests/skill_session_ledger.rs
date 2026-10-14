//! Hermes skill load ledger fold tests (synthetic fixtures — not live ACP traffic).

use zeron_proto::skill::{
    HermesSkillLoadConfidence, HermesSkillLoadMeta, HermesSkillLoadStatus,
    HermesSkillLoadsCapability, HERMES_SKILL_LOAD_SCHEMA_VERSION,
};
use zeron_proto::AgentEvent;
use zeron_engine::skill_sessions::replay_events;

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
