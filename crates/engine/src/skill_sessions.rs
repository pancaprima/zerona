//! Hermes per-chat skill load ledger (confirmed / failed / observed).

use std::collections::HashMap;

use zeron_proto::skill::{
    HermesSkillLoadConfidence, HermesSkillLoadMeta, HermesSkillLoadRecord,
    HermesSkillLoadStatus, HermesSkillLoadsCapability, HermesSkillSessionLoads,
    SessionDocSkillLoads, HERMES_SKILL_LOAD_SCHEMA_VERSION, ledger_status_from_meta,
    skill_load_meta_is_authoritative, skill_load_record_scope_for_meta,
    skill_load_record_scope_for_record, validate_skill_load_meta,
};
use zeron_proto::AgentEvent;

#[derive(Debug, Default, Clone)]
pub struct SkillLoadLedger {
    pub capability: HermesSkillLoadsCapability,
    pub profile_key: Option<String>,
    pub generation: u64,
    pub(crate) by_event: HashMap<String, HermesSkillLoadRecord>,
    pub(crate) hydrated_from_doc: bool,
}

impl SkillLoadLedger {
    pub fn new(capability: HermesSkillLoadsCapability) -> Self {
        Self {
            capability,
            profile_key: None,
            generation: 0,
            by_event: HashMap::new(),
            hydrated_from_doc: false,
        }
    }

    pub fn hydrate_from_doc(&mut self, doc: &SessionDocSkillLoads) {
        if self.hydrated_from_doc {
            return;
        }
        self.hydrated_from_doc = true;
        self.capability = doc.capability;
        self.profile_key = doc.profile_key.clone();
        self.generation = doc.generation;
        self.by_event.clear();
        for rec in &doc.records {
            let scope = skill_load_record_scope_for_record(rec);
            self.by_event.insert(scope, rec.clone());
        }
    }

    pub fn to_doc(&self) -> SessionDocSkillLoads {
        SessionDocSkillLoads {
            schema_version: HERMES_SKILL_LOAD_SCHEMA_VERSION,
            capability: self.capability,
            profile_key: self.profile_key.clone(),
            records: self.all_records(),
            generation: self.generation,
            catalog_mode: None,
            resolver_epoch: None,
        }
    }

    fn all_records(&self) -> Vec<HermesSkillLoadRecord> {
        let mut rows: Vec<_> = self.by_event.values().cloned().collect();
        rows.sort_by(|a, b| {
            skill_load_record_scope_for_record(a)
                .cmp(&skill_load_record_scope_for_record(b))
                .then_with(|| a.load_name.cmp(&b.load_name))
                .then_with(|| a.event_id.cmp(&b.event_id))
        });
        rows
    }

    fn snapshot_records_for_active_profile(&self) -> Vec<HermesSkillLoadRecord> {
        let active = self.profile_key.as_deref();
        self.by_event
            .values()
            .filter(|rec| {
                active.is_none_or(|key| rec.profile_key.as_deref() == Some(key))
            })
            .cloned()
            .collect()
    }

    fn bound_wire_session_id<'a>(
        wire_session_id: Option<&'a str>,
        meta: &'a HermesSkillLoadMeta,
    ) -> Option<&'a str> {
        wire_session_id
            .filter(|session| !session.is_empty())
            .or((!meta.acp_session_id.is_empty()).then_some(meta.acp_session_id.as_str()))
    }

    pub fn apply_meta(
        &mut self,
        meta: &HermesSkillLoadMeta,
        authoritative: bool,
        wire_session_id: Option<&str>,
    ) {
        let bound_wire = Self::bound_wire_session_id(wire_session_id, meta);
        if !validate_skill_load_meta(meta, bound_wire, self.profile_key.as_deref()) {
            return;
        }
        if meta.child_session_key.is_some() {
            return;
        }
        let status = if authoritative {
            ledger_status_from_meta(meta)
        } else if meta.source == "skill_view" {
            HermesSkillLoadStatus::ToolObservedRead
        } else {
            return;
        };
        let confidence = if authoritative {
            HermesSkillLoadConfidence::WireAuthoritative
        } else {
            HermesSkillLoadConfidence::ToolObserved
        };
        if !meta.profile_key.is_empty() {
            if self.profile_key.is_none() {
                self.profile_key = Some(meta.profile_key.clone());
            } else if self.profile_key.as_deref() != Some(meta.profile_key.as_str()) {
                self.profile_key = Some(meta.profile_key.clone());
            }
        }
        let scope = skill_load_record_scope_for_meta(meta);
        let record = HermesSkillLoadRecord {
            event_id: meta.event_id.clone(),
            load_name: meta.load_name.clone(),
            display_name: meta.display_name.clone(),
            file_path: meta.file_path.clone(),
            status,
            confidence,
            setup_needed: meta.setup_needed,
            historical: true,
            tool_call_id: meta.tool_call_id.clone(),
            profile_key: Some(meta.profile_key.clone()).filter(|key| !key.is_empty()),
            acp_session_id: Some(meta.acp_session_id.clone()).filter(|session| !session.is_empty()),
            record_scope: Some(scope.clone()),
        };
        if let Some(existing) = self.by_event.get(&scope) {
            if !status_monotonic(existing.status, status) {
                return;
            }
            if record_equivalent(existing, &record) {
                return;
            }
        }
        self.by_event.insert(scope, record);
        self.generation = self.generation.wrapping_add(1);
    }

    pub fn apply_event(
        &mut self,
        event: &AgentEvent,
        wire_session_id: Option<&str>,
    ) {
        match event {
            AgentEvent::HermesSkillLoad { meta } => {
                let bound_wire = Self::bound_wire_session_id(wire_session_id, meta);
                let authoritative = skill_load_meta_is_authoritative(meta, bound_wire);
                if authoritative {
                    self.capability = HermesSkillLoadsCapability::WireV2Acp;
                }
                self.apply_meta(meta, authoritative, wire_session_id);
            }
            _ => {}
        }
    }

    pub fn snapshot(&self) -> HermesSkillSessionLoads {
        let mut confirmed = Vec::new();
        let mut failed = Vec::new();
        let mut observed_reads = Vec::new();
        for rec in self.snapshot_records_for_active_profile() {
            match rec.status {
                HermesSkillLoadStatus::Failed | HermesSkillLoadStatus::Abandoned => {
                    failed.push(rec);
                }
                HermesSkillLoadStatus::ToolObservedRead => observed_reads.push(rec),
                HermesSkillLoadStatus::LoadedPrimary
                | HermesSkillLoadStatus::LoadedReference
                | HermesSkillLoadStatus::LoadedUnchanged
                | HermesSkillLoadStatus::LoadedSetupNeeded => {
                    if rec.confidence == HermesSkillLoadConfidence::WireAuthoritative {
                        confirmed.push(rec);
                    }
                }
                _ => {}
            }
        }
        sort_stable_unique(&mut confirmed);
        sort_stable_unique(&mut failed);
        sort_stable_unique(&mut observed_reads);
        HermesSkillSessionLoads {
            capability: self.capability,
            schema_version: HERMES_SKILL_LOAD_SCHEMA_VERSION,
            generation: self.generation,
            confirmed,
            failed,
            observed_reads,
            catalog_mode: None,
            resolver_epoch: None,
        }
    }
}

fn record_equivalent(prev: &HermesSkillLoadRecord, next: &HermesSkillLoadRecord) -> bool {
    prev.event_id == next.event_id
        && prev.load_name == next.load_name
        && prev.display_name == next.display_name
        && prev.file_path == next.file_path
        && prev.status == next.status
        && prev.confidence == next.confidence
        && prev.setup_needed == next.setup_needed
        && prev.historical == next.historical
        && prev.tool_call_id == next.tool_call_id
        && prev.profile_key == next.profile_key
        && prev.acp_session_id == next.acp_session_id
        && prev.record_scope == next.record_scope
}

fn status_monotonic(prev: HermesSkillLoadStatus, next: HermesSkillLoadStatus) -> bool {
    use HermesSkillLoadStatus::*;
    let success = |s: HermesSkillLoadStatus| {
        matches!(
            s,
            LoadedPrimary | LoadedReference | LoadedUnchanged | LoadedSetupNeeded
        )
    };
    if success(prev) && matches!(next, Failed | Abandoned) {
        return false;
    }
    if matches!(prev, Failed | Abandoned) && success(next) {
        return true;
    }
    true
}

fn sort_stable_unique(rows: &mut Vec<HermesSkillLoadRecord>) {
    rows.sort_by(|a, b| {
        a.load_name
            .cmp(&b.load_name)
            .then_with(|| a.event_id.cmp(&b.event_id))
    });
    rows.dedup_by(|a, b| {
        skill_load_record_scope_for_record(a) == skill_load_record_scope_for_record(b)
    });
}

pub fn replay_events(
    events: &[AgentEvent],
    capability: HermesSkillLoadsCapability,
) -> SkillLoadLedger {
    let mut ledger = SkillLoadLedger::new(capability);
    for event in events {
        let wire = match event {
            AgentEvent::HermesSkillLoad { meta } => {
                (!meta.acp_session_id.is_empty()).then_some(meta.acp_session_id.as_str())
            }
            _ => None,
        };
        ledger.apply_event(event, wire);
    }
    ledger
}
