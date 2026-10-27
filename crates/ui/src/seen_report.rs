//! Visible-transcript read receipts for deferred unread push (`POST /seen`).

use crate::transcript::{Row, RowKind};

/// Row band in list coordinates (same idea as Android `PlacementBounds`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct RowBounds {
    pub index: usize,
    pub top: f32,
    pub height: f32,
}

/// Newest rendered message eligible for a read receipt.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SeenMessage {
    pub message_id: String,
    pub message_at_ms: i64,
}

/// Indices of rows that intersect the viewport by at least `min_visible_px` (or
/// the full row height when the row is shorter).
pub fn filter_visible_in_viewport(
    placements: &[RowBounds],
    viewport_top: f32,
    viewport_bottom: f32,
    min_visible_px: f32,
) -> Vec<usize> {
    placements
        .iter()
        .filter_map(|p| {
            if p.height <= 0.0 {
                return None;
            }
            let row_bottom = p.top + p.height;
            let visible_height =
                (row_bottom.min(viewport_bottom) - p.top.max(viewport_top)).max(0.0);
            let threshold = min_visible_px.min(p.height);
            if visible_height >= threshold {
                Some(p.index)
            } else {
                None
            }
        })
        .collect()
}

/// Pick the visible row with the greatest `messageAt` (monotonic read cursor).
pub fn newest_visible_message(
    placement_indices: &[usize],
    entry_id_for_index: impl Fn(usize) -> Option<&str>,
    created_at_ms: impl Fn(&str) -> Option<i64>,
) -> Option<SeenMessage> {
    let mut best: Option<SeenMessage> = None;
    for index in placement_indices {
        let entry_id = entry_id_for_index(index)?;
        if entry_id.is_empty() {
            continue;
        }
        let at = created_at_ms(entry_id)?;
        let candidate = SeenMessage {
            message_id: entry_id.to_string(),
            message_at_ms: at,
        };
        if best.as_ref().is_none_or(|b| candidate.message_at_ms > b.message_at_ms) {
            best = Some(candidate);
        }
    }
    best
}

/// Whether this transcript row can carry a read receipt (mirrors Android: no
/// ephemeral working strip — it is not a list row on desktop).
pub fn row_eligible_for_seen(row: &Row) -> bool {
    if row.entry_id.is_empty() {
        return false;
    }
    !matches!(
        row.kind,
        RowKind::ForkMarker { .. } | RowKind::SwitchMarker { .. }
    )
}

/// Dedup: only report when strictly newer than the last successful report.
pub fn candidate_to_report(
    visible: Option<&SeenMessage>,
    last_reported: Option<&SeenMessage>,
) -> Option<SeenMessage> {
    let visible = visible?;
    if last_reported.is_some_and(|prev| visible.message_at_ms <= prev.message_at_ms) {
        return None;
    }
    Some(visible.clone())
}

/// At most one network call per `min_interval_ms` unless the candidate advanced
/// past `last_reported`.
pub fn may_emit_now(
    now_ms: i64,
    candidate: &SeenMessage,
    last_reported: Option<&SeenMessage>,
    last_emit_at_ms: Option<i64>,
    min_interval_ms: i64,
) -> bool {
    if last_reported.is_some_and(|prev| candidate.message_at_ms <= prev.message_at_ms) {
        return false;
    }
    match last_emit_at_ms {
        None => true,
        Some(last_emit) => {
            if now_ms - last_emit >= min_interval_ms {
                true
            } else {
                last_reported.is_some_and(|prev| candidate.message_at_ms > prev.message_at_ms)
            }
        }
    }
}

pub const SEEN_DEBOUNCE_MS: u64 = 1_000;
pub const SEEN_MIN_INTERVAL_MS: i64 = 5_000;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn filter_visible_respects_viewport_and_min_slice() {
        let placements = [
            RowBounds {
                index: 0,
                top: 100.0,
                height: 40.0,
            },
            RowBounds {
                index: 1,
                top: 900.0,
                height: 40.0,
            },
            RowBounds {
                index: 2,
                top: 495.0,
                height: 80.0,
            },
            RowBounds {
                index: 3,
                top: 120.0,
                height: 12.0,
            },
            RowBounds {
                index: 4,
                top: 50.0,
                height: 0.0,
            },
        ];
        let visible = filter_visible_in_viewport(&placements, 100.0, 500.0, 24.0);
        assert_eq!(visible, vec![0, 3]);
    }

    #[test]
    fn newest_picks_max_message_at() {
        let ids = [("a", 100_i64), ("b", 300), ("c", 200)];
        let msg = newest_visible_message(
            &[0, 1, 2],
            |ix| Some(ids[ix].0),
            |id| ids.iter().find(|(e, _)| *e == id).map(|(_, t)| *t),
        );
        assert_eq!(
            msg,
            Some(SeenMessage {
                message_id: "b".into(),
                message_at_ms: 300,
            })
        );
    }

    #[test]
    fn policy_dedup_and_throttle() {
        let first = SeenMessage {
            message_id: "m1".into(),
            message_at_ms: 1_000,
        };
        let second = SeenMessage {
            message_id: "m2".into(),
            message_at_ms: 2_000,
        };
        assert_eq!(candidate_to_report(Some(&first), None), Some(first.clone()));
        assert_eq!(candidate_to_report(Some(&first), Some(&first)), None);
        assert_eq!(
            candidate_to_report(Some(&second), Some(&first)),
            Some(second.clone())
        );
        assert!(may_emit_now(0, &first, None, None, 5_000));
        assert!(!may_emit_now(1_000, &first, None, Some(0), 5_000));
        assert!(may_emit_now(5_000, &first, None, Some(0), 5_000));
        assert!(may_emit_now(1_000, &second, Some(&first), Some(0), 5_000));
    }
}
