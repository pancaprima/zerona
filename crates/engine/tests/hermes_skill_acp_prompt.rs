//! Hermes ACP prompt staging contract (synthetic — no live child process).

use zeron_harness::acp::hermes_skill_wire;
use zeron_proto::invocation::{Invocation, harness_prompt};
use zeron_proto::skill::encode_hermes_skill_uri;
use zeron_proto::HarnessId;

#[test]
fn canonical_prompt_yields_selections_before_harness_delivery_text() {
    let uri = encode_hermes_skill_uri("review-skill");
    let canonical = format!(
        "Please {}",
        Invocation::Skill {
            name: "review-skill".into(),
            path: uri,
            command: None,
        }
        .link()
    );
    let selections = hermes_skill_wire::hermes_skill_selections_from_prompt(&canonical);
    assert_eq!(selections.len(), 1);
    assert_eq!(selections[0].0, "review-skill");
    let delivered = harness_prompt(&canonical, HarnessId::Hermes);
    assert!(
        !delivered.contains("harness-skill:hermes:"),
        "delivered prompt must not retain URI chips"
    );
    assert!(delivered.contains("review-skill"));
}
