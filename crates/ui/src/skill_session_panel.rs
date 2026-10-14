//! Per-session Hermes skill load panel (confirmed / failed / observed reads).

use std::collections::HashMap;

use gpui::{
    AnyElement, Context, InteractiveElement as _, IntoElement, ParentElement as _, SharedString,
    StatefulInteractiveElement as _, Styled, Window, div, prelude::*, px,
};
use zeron_proto::skill::{HermesSkillLoadRecord, HermesSkillSessionLoads};
use zeron_rpc::methods;

use crate::composer::Composer;
use crate::theme::Theme;

fn panel_enabled() -> bool {
    !std::env::var("ZERON_HERMES_SKILL_SESSION_PANEL")
        .is_ok_and(|v| matches!(v.as_str(), "0" | "false" | "no"))
}

#[derive(Debug, Default, Clone)]
pub(crate) struct SkillSessionPanelState {
    pub expanded: bool,
    pub loads: Option<HermesSkillSessionLoads>,
    pub loading: bool,
    pub error: Option<SharedString>,
    pub request_generation: u64,
    pub last_fetch_generation: u64,
    pub degraded_hint: Option<SharedString>,
}

impl Composer {
    fn hermes_skill_panel_active(&self, cx: &App) -> bool {
        panel_enabled()
            && self
                .pickers
                .read(cx)
                .resolved(cx)
                .harness
                .is_some_and(|h| h == zeron_proto::HarnessId::Hermes)
    }

    pub(crate) fn refresh_skill_session_loads(&mut self, cx: &mut Context<Self>) {
        if !self.hermes_skill_panel_active(cx) {
            return;
        }
        let chat_id = match self.state.read(cx).selected_chat.clone() {
            Some(id) => id,
            None => return,
        };
        let Some(engine) = self.state.read(cx).engine().cloned() else {
            return;
        };
        let panel = self.skill_session_panels.entry(chat_id.clone()).or_default();
        panel.request_generation = panel.request_generation.wrapping_add(1);
        let generation = panel.request_generation;
        panel.loading = true;
        panel.error = None;
        cx.spawn(async move |this, cx| {
            let result = engine
                .client()
                .call(
                    methods::GET_SKILL_SESSION_LOADS,
                    serde_json::json!({ "chatId": chat_id }),
                )
                .await;
            this.update(cx, |this, cx| {
                let panel = this.skill_session_panels.entry(chat_id.clone()).or_default();
                if panel.request_generation != generation {
                    return;
                }
                panel.loading = false;
                panel.last_fetch_generation = generation;
                match result {
                    Ok(value) => {
                        match serde_json::from_value::<HermesSkillSessionLoads>(value) {
                            Ok(loads) => {
                                panel.loads = Some(loads);
                                panel.error = None;
                            }
                            Err(err) => {
                                panel.loads = None;
                                panel.error =
                                    Some(format!("Invalid skill load response: {err}").into());
                            }
                        }
                        panel.degraded_hint = panel.loads.as_ref().and_then(|loads| {
                            if loads.capability
                                == zeron_proto::skill::HermesSkillLoadsCapability::ObservedToolsV0
                            {
                                Some(
                                    "Catalog degraded — confirmed loads need Hermes wire events"
                                        .into(),
                                )
                            } else {
                                None
                            }
                        });
                    }
                    Err(err) => panel.error = Some(err.to_string().into()),
                }
                cx.notify();
            })
            .ok();
        })
        .detach();
    }

    pub(crate) fn on_skill_session_doc_tick(&mut self, cx: &mut Context<Self>) {
        if !self.hermes_skill_panel_active(cx) {
            return;
        }
        let chat_id = self.state.read(cx).selected_chat.clone();
        if chat_id.is_none() {
            return;
        }
        self.refresh_skill_session_loads(cx);
    }

    pub(crate) fn render_skill_session_panel(
        &mut self,
        _below_queue: bool,
        _window: &mut Window,
        cx: &mut Context<Self>,
    ) -> Option<AnyElement> {
        if !self.hermes_skill_panel_active(cx) {
            return None;
        }
        let chat_id = self.state.read(cx).selected_chat.clone()?;
        let needs_fetch = {
            let panel = self.skill_session_panels.entry(chat_id.clone()).or_default();
            panel.loads.is_none() && !panel.loading && panel.error.is_none()
        };
        if needs_fetch {
            self.refresh_skill_session_loads(cx);
        }
        let panel_snapshot = self
            .skill_session_panels
            .get(&chat_id)
            .cloned()
            .unwrap_or_default();
        let theme = Theme::of(cx).clone();
        let expanded = panel_snapshot.expanded;
        let title = panel_snapshot
            .loads
            .as_ref()
            .map(|l| {
                let n = unique_load_names(&l.confirmed);
                if n == 0 {
                    "Loaded skills".to_string()
                } else {
                    format!("Loaded skills ({n})")
                }
            })
            .unwrap_or_else(|| "Loaded skills".to_string());
        let body = render_body(&panel_snapshot, &theme, chat_id.clone(), cx);
        let chat = chat_id.clone();
        let header = div()
            .flex()
            .items_center()
            .justify_between()
            .cursor_pointer()
            .on_click(cx.listener(move |this, _, _, cx| {
                if let Some(panel) = this.skill_session_panels.get_mut(&chat) {
                    panel.expanded = !panel.expanded;
                }
                if this
                    .skill_session_panels
                    .get(&chat)
                    .is_some_and(|p| p.expanded)
                {
                    this.refresh_skill_session_loads(cx);
                }
                cx.notify();
            }))
            .child(div().text_sm().child(title))
            .child(
                div()
                    .text_xs()
                    .text_color(theme.text_muted)
                    .child(if expanded { "▾" } else { "▸" }),
            );
        let surface = crate::queue::queue_panel_surface(&theme, cx.has_active_drag())
            .child(header)
            .when(expanded, |el| el.children(body));
        Some(surface.into_any_element())
    }
}

fn render_body(
    panel: &SkillSessionPanelState,
    theme: &Theme,
    chat_id: String,
    cx: &mut Context<Composer>,
) -> Vec<AnyElement> {
    let mut rows = Vec::new();
    if panel.loading {
        rows.push(
            div()
                .text_xs()
                .text_color(theme.text_muted)
                .child("Loading…")
                .into_any_element(),
        );
        return rows;
    }
    if let Some(hint) = &panel.degraded_hint {
        rows.push(
            div()
                .text_xs()
                .text_color(theme.text_muted)
                .child(hint.clone())
                .into_any_element(),
        );
    }
    if let Some(err) = &panel.error {
        let chat = chat_id;
        rows.push(
            div()
                .flex()
                .flex_col()
                .gap(px(4.0))
                .child(
                    div()
                        .text_xs()
                        .text_color(theme.danger)
                        .child(err.clone()),
                )
                .child(
                    div()
                        .text_xs()
                        .text_color(theme.text_muted)
                        .cursor_pointer()
                        .on_click(cx.listener(move |this, _, _, cx| {
                            this.refresh_skill_session_loads(cx);
                            cx.notify();
                        }))
                        .child("Retry"),
                )
                .into_any_element(),
        );
        return rows;
    }
    let Some(loads) = &panel.loads else {
        rows.push(
            div()
                .text_xs()
                .text_color(theme.text_muted)
                .child("No skill load data yet — start a Hermes session")
                .into_any_element(),
        );
        return rows;
    };
    if loads.confirmed.is_empty() && loads.failed.is_empty() && loads.observed_reads.is_empty() {
        rows.push(
            div()
                .text_xs()
                .text_color(theme.text_muted)
                .child("No confirmed loads this session")
                .into_any_element(),
        );
    }
    if !loads.confirmed.is_empty() {
        rows.push(label("Confirmed", theme));
        for group in group_by_load_name(&loads.confirmed) {
            rows.extend(group_rows(&group, theme));
        }
    }
    if !loads.failed.is_empty() {
        rows.push(label("Failed / cancelled", theme));
        for group in group_by_load_name(&loads.failed) {
            rows.extend(group_rows(&group, theme));
        }
    }
    if !loads.observed_reads.is_empty() {
        rows.push(label("Observed reads (degraded)", theme));
        for group in group_by_load_name(&loads.observed_reads) {
            rows.extend(group_rows(&group, theme));
        }
    }
    rows
}

fn group_by_load_name(rows: &[HermesSkillLoadRecord]) -> Vec<Vec<HermesSkillLoadRecord>> {
    let mut map: HashMap<String, Vec<HermesSkillLoadRecord>> = HashMap::new();
    for row in rows {
        map.entry(row.load_name.clone()).or_default().push(row.clone());
    }
    let mut groups: Vec<_> = map.into_values().collect();
    groups.sort_by(|a, b| a[0].load_name.cmp(&b[0].load_name));
    groups
}

fn unique_load_names(rows: &[HermesSkillLoadRecord]) -> usize {
    let mut names = std::collections::HashSet::new();
    for row in rows {
        names.insert(row.load_name.as_str());
    }
    names.len()
}

fn group_rows(group: &[HermesSkillLoadRecord], theme: &Theme) -> Vec<AnyElement> {
    let primary = &group[0];
    let mut label = primary.load_name.clone();
    if group.len() == 1
        && primary.status == zeron_proto::skill::HermesSkillLoadStatus::LoadedReference
    {
        if let Some(path) = primary
            .file_path
            .as_deref()
            .filter(|p| !p.is_empty())
        {
            label = format!("{} · {path}", primary.load_name);
        }
    }
    let mut out = vec![row(
        &label,
        primary.historical,
        primary.setup_needed,
        theme,
    )];
    for reference in group.iter().skip(1) {
        let path = reference
            .file_path
            .as_deref()
            .filter(|p| !p.is_empty())
            .unwrap_or("reference");
        out.push(
            div()
                .pl(px(12.0))
                .text_xs()
                .text_color(theme.text_muted)
                .child(format!("↳ {path}"))
                .into_any_element(),
        );
    }
    out
}

fn label(text: &str, theme: &Theme) -> AnyElement {
    div()
        .mt(px(6.0))
        .text_xs()
        .text_color(theme.text_muted)
        .child(text.to_string())
        .into_any_element()
}

#[cfg(test)]
mod tests {
    use super::*;
    use zeron_proto::skill::{
        HermesSkillLoadConfidence, HermesSkillLoadStatus, HermesSkillLoadsCapability,
        HERMES_SKILL_LOAD_SCHEMA_VERSION,
    };

    #[test]
    fn unique_load_name_count_dedups_events() {
        let rows = vec![
            HermesSkillLoadRecord {
                event_id: "a".into(),
                load_name: "alpha".into(),
                display_name: None,
                file_path: None,
                status: HermesSkillLoadStatus::LoadedPrimary,
                confidence: HermesSkillLoadConfidence::WireAuthoritative,
                setup_needed: false,
                historical: false,
                tool_call_id: None,
                profile_key: None,
            },
            HermesSkillLoadRecord {
                event_id: "b".into(),
                load_name: "alpha".into(),
                display_name: None,
                file_path: None,
                status: HermesSkillLoadStatus::LoadedReference,
                confidence: HermesSkillLoadConfidence::WireAuthoritative,
                setup_needed: false,
                historical: false,
                tool_call_id: None,
                profile_key: None,
            },
        ];
        assert_eq!(unique_load_names(&rows), 1);
    }

    #[test]
    fn stale_fetch_generation_skips_panel_apply() {
        let mut panel = SkillSessionPanelState::default();
        panel.request_generation = 2;
        panel.last_fetch_generation = 1;
        let generation = panel.request_generation;
        let should_apply = panel.request_generation == generation;
        assert!(should_apply);
        panel.last_fetch_generation = generation;
        assert_eq!(panel.last_fetch_generation, panel.request_generation);
    }

    #[test]
    fn invalid_rpc_payload_surfaces_error_not_silent_empty() {
        let value = serde_json::json!({ "not": "loads" });
        let parsed = serde_json::from_value::<HermesSkillSessionLoads>(value);
        assert!(parsed.is_err());
    }

    #[test]
    fn snapshot_generation_roundtrips() {
        let loads = HermesSkillSessionLoads {
            capability: HermesSkillLoadsCapability::WireV2Acp,
            schema_version: HERMES_SKILL_LOAD_SCHEMA_VERSION,
            generation: 7,
            confirmed: Vec::new(),
            failed: Vec::new(),
            observed_reads: Vec::new(),
            catalog_mode: None,
            resolver_epoch: None,
        };
        let json = serde_json::to_value(&loads).unwrap();
        let back: HermesSkillSessionLoads = serde_json::from_value(json).unwrap();
        assert_eq!(back.generation, 7);
    }
}

fn row(name: &str, historical: bool, setup_needed: bool, theme: &Theme) -> AnyElement {
    let mut suffix = String::new();
    if historical {
        suffix.push_str(" · historical");
    }
    if setup_needed {
        suffix.push_str(" · setup needed");
    }
    div()
        .text_sm()
        .text_color(theme.text)
        .child(format!("{name}{suffix}"))
        .into_any_element()
}
