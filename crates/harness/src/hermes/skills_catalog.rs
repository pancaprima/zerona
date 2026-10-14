//! Map Hermes ACP catalog payloads to Zerona [`Skill`] rows.

use zeron_proto::invocation::Skill;
use zeron_proto::skill::{
    encode_hermes_skill_uri, HermesSkillCatalogEntry, HermesSkillCatalogMode,
    HermesSkillCatalogResponse, HermesSkillLoadsCapability,
};

pub fn skills_from_catalog(
    response: &HermesSkillCatalogResponse,
    harness: zeron_proto::HarnessId,
) -> Vec<Skill> {
    response
        .entries
        .iter()
        .map(|entry| skill_from_entry(entry, harness))
        .collect()
}

pub fn skill_from_entry(entry: &HermesSkillCatalogEntry, _harness: zeron_proto::HarnessId) -> Skill {
    Skill {
        name: entry.display_name.clone(),
        path: encode_hermes_skill_uri(&entry.load_name),
        description: entry.description.clone().unwrap_or_default(),
        enabled: entry.enabled,
        command: None,
        catalog_source: Some("authoritative".into()),
    }
}

pub fn degraded_filesystem_skills(
    skills: Vec<Skill>,
) -> Vec<Skill> {
    skills
        .into_iter()
        .map(|s| Skill {
            catalog_source: Some("filesystem_degraded".into()),
            ..s
        })
        .collect()
}

pub fn catalog_mode_from_capability(
    cap: HermesSkillLoadsCapability,
    authoritative: bool,
) -> HermesSkillCatalogMode {
    if authoritative && cap == HermesSkillLoadsCapability::WireV2Acp {
        HermesSkillCatalogMode::Authoritative
    } else {
        HermesSkillCatalogMode::FilesystemDegraded
    }
}
