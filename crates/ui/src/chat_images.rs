//! Markdown inline images in chat transcripts.

use std::collections::HashMap;
use std::sync::Arc;

use gpui::{AnyElement, SharedString};

use crate::image_media::MediaImage;
use crate::theme::Theme;

const MAX_CACHE_BYTES: usize = 32 * 1024 * 1024;
const MAX_CACHE_ENTRIES: usize = 48;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum ImageSourceKind {
    Url,
    Path,
}

pub(crate) fn classify_markdown_image_source(source: &str) -> ImageSourceKind {
    let trimmed = source.trim();
    if trimmed.starts_with("https://")
        || trimmed.starts_with("http://")
        || trimmed.starts_with("//")
    {
        ImageSourceKind::Url
    } else {
        ImageSourceKind::Path
    }
}

#[derive(Clone, Hash, PartialEq, Eq)]
pub(crate) struct MarkdownImageKey {
    pub chat_id: String,
    pub source: String,
}

#[derive(Clone)]
pub(crate) enum MarkdownImageState {
    Loading,
    Ready(MediaImage),
    Failed,
}

#[derive(Default)]
pub(crate) struct MarkdownImageCache {
    entries: HashMap<MarkdownImageKey, MarkdownImageState>,
    bytes: usize,
}

impl MarkdownImageCache {
    pub(crate) fn get(&self, key: &MarkdownImageKey) -> Option<&MarkdownImageState> {
        self.entries.get(key)
    }

    pub(crate) fn mark_loading(&mut self, key: MarkdownImageKey) -> bool {
        if self.entries.contains_key(&key) {
            return false;
        }
        self.entries.insert(key, MarkdownImageState::Loading);
        true
    }

    pub(crate) fn store_ready(&mut self, key: MarkdownImageKey, media: MediaImage) {
        if let Some(previous) = self.entries.get(&key) {
            self.bytes -= state_bytes(previous);
        }
        self.bytes += media.bytes;
        self.entries.insert(key, MarkdownImageState::Ready(media));
        self.enforce_budget();
    }

    pub(crate) fn store_failed(&mut self, key: MarkdownImageKey) {
        if let Some(previous) = self.entries.remove(&key) {
            self.bytes -= state_bytes(&previous);
        }
        self.entries.insert(key, MarkdownImageState::Failed);
    }

    fn enforce_budget(&mut self) {
        while self.bytes > MAX_CACHE_BYTES || self.entries.len() > MAX_CACHE_ENTRIES {
            let oldest = self.entries.keys().next().cloned();
            let Some(key) = oldest else { break };
            if let Some(entry) = self.entries.remove(&key) {
                self.bytes -= state_bytes(&entry);
            }
        }
    }
}

fn state_bytes(state: &MarkdownImageState) -> usize {
    match state {
        MarkdownImageState::Ready(media) => media.bytes,
        _ => 0,
    }
}

pub(crate) fn render_markdown_image(
    state: Option<&MarkdownImageState>,
    source: &str,
    alt: &str,
    id: SharedString,
    theme: &Theme,
    _image: Option<Arc<gpui::Image>>,
    on_open: impl Fn(&mut gpui::Window, &mut gpui::App) + 'static,
) -> AnyElement {
    let label = if alt.is_empty() { source } else { alt };
    match state {
        Some(MarkdownImageState::Ready(media)) => crate::image_media::preview_element(
            media,
            format!("{id}-image").into(),
            move |window, cx| on_open(window, cx),
        )
        .into_any_element(),
        Some(MarkdownImageState::Failed) | None => {
            let external = classify_markdown_image_source(source) == ImageSourceKind::Url;
            gpui::div()
                .id(id)
                .text_color(theme.text_muted)
                .child(format!("{label} — {source}"))
                .when(external, |el| el.cursor_pointer())
                .into_any_element()
        }
        Some(MarkdownImageState::Loading) => gpui::div()
            .id(id)
            .text_color(theme.text_muted)
            .child(format!("{label} — loading…"))
            .into_any_element(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classifies_http_and_path_sources() {
        assert_eq!(
            classify_markdown_image_source("https://cdn.example/a.png"),
            ImageSourceKind::Url
        );
        assert_eq!(
            classify_markdown_image_source("//cdn.example/a.png"),
            ImageSourceKind::Url
        );
        assert_eq!(
            classify_markdown_image_source("/root/foo/shot.png"),
            ImageSourceKind::Path
        );
        assert_eq!(
            classify_markdown_image_source("shots/a.png"),
            ImageSourceKind::Path
        );
    }
}
