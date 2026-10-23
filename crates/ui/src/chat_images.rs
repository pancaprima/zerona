//! Markdown inline images in chat transcripts.

use std::collections::HashMap;
use std::sync::Arc;

use gpui::{
    AnyElement, InteractiveElement as _, IntoElement as _, ParentElement as _, SharedString,
    Styled, prelude::*,
};

use crate::image_media::MediaImage;
use crate::theme::Theme;

const MAX_CACHE_BYTES: usize = 32 * 1024 * 1024;
const MAX_CACHE_ENTRIES: usize = 48;

pub(crate) const MEDIA_TOKEN_PREFIX: &str = "MEDIA:";

const IMAGE_EXTENSIONS: &[&str] = &["png", "jpg", "jpeg", "gif", "webp"];

fn media_token_boundary(text: &str, start: usize) -> bool {
    start == 0 || text[..start].chars().next_back().is_some_and(|c| c.is_whitespace())
}

fn path_has_allowed_image_extension(path: &str) -> bool {
    let path = path.trim_end_matches(|c: char| matches!(c, '.' | ',' | '!' | '?' | ';' | ':'));
    path.rsplit('/')
        .next()
        .and_then(|name| name.rsplit('.').next())
        .map(|ext| {
            let ext = ext.to_ascii_lowercase();
            IMAGE_EXTENSIONS.iter().any(|allowed| *allowed == ext)
        })
        .unwrap_or(false)
}

fn trim_media_trailing_punctuation(path: &str) -> &str {
    path.trim_end_matches(|c: char| {
        matches!(c, ',' | '!' | '?' | ';' | ')' | ']' | '"' | '\'' | '`')
    })
    .trim_end_matches('.')
}

fn parse_media_source_tail(tail: &str) -> Option<(&str, usize)> {
    let leading = tail.len() - tail.trim_start().len();
    let tail = tail.trim_start();
    if tail.is_empty() {
        return None;
    }
    let end = if tail.starts_with("https://")
        || tail.starts_with("http://")
        || tail.starts_with("//")
        || tail.starts_with('/')
    {
        tail.find(|c: char| c.is_whitespace()).unwrap_or(tail.len())
    } else {
        let bytes = tail.as_bytes();
        if bytes.len() >= 3
            && bytes[0].is_ascii_alphabetic()
            && bytes[1] == b':'
            && matches!(bytes.get(2), Some(b'/' | b'\\'))
        {
            tail.find(|c: char| c.is_whitespace()).unwrap_or(tail.len())
        } else {
            return None;
        }
    };
    let raw = &tail[..end];
    let source = trim_media_trailing_punctuation(raw);
    if source.is_empty() || !path_has_allowed_image_extension(source) {
        return None;
    }
    Some((source, leading + source.len()))
}

/// Conservative scan for Hermes-style `MEDIA:` image tokens in message text.
pub(crate) fn media_token_spans(text: &str) -> Vec<(usize, usize, String)> {
    let mut out = Vec::new();
    let mut search_at = 0;
    while let Some(rel) = text[search_at..].find(MEDIA_TOKEN_PREFIX) {
        let start = search_at + rel;
        if !media_token_boundary(text, start) {
            search_at = start + MEDIA_TOKEN_PREFIX.len();
            continue;
        }
        let tail_start = start + MEDIA_TOKEN_PREFIX.len();
        let tail = &text[tail_start..];
        let Some((source, consumed)) = parse_media_source_tail(tail) else {
            search_at = start + MEDIA_TOKEN_PREFIX.len();
            continue;
        };
        let end = tail_start + consumed;
        out.push((start, end, source.to_string()));
        search_at = end;
    }
    out
}

/// Rewrite recognized `MEDIA:` tokens into inline markdown images for the normal loader.
pub(crate) fn expand_media_tokens_for_markdown(text: &str) -> String {
    let spans = media_token_spans(text);
    if spans.is_empty() {
        return text.to_string();
    }
    let mut out = String::with_capacity(text.len() + spans.len() * 8);
    let mut last = 0;
    for (start, end, source) in spans {
        out.push_str(&text[last..start]);
        let token = &text[start..end];
        out.push_str(&format!("![{token}]({source})"));
        last = end;
    }
    out.push_str(&text[last..]);
    out
}

fn media_token_alt_matches_source(alt: &str, source: &str) -> bool {
    alt.strip_prefix(MEDIA_TOKEN_PREFIX) == Some(source)
}

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

pub(crate) fn markdown_image_failure_caption(alt: &str, source: &str) -> String {
    if media_token_alt_matches_source(alt, source) {
        alt.to_string()
    } else {
        let label = if alt.is_empty() { source } else { alt };
        format!("{label} — {source}")
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
    match state {
        Some(MarkdownImageState::Ready(media)) => crate::image_media::preview_element(
            media,
            format!("{id}-image").into(),
            move |window, cx| on_open(window, cx),
        ),
        Some(MarkdownImageState::Failed) | None => {
            let external = classify_markdown_image_source(source) == ImageSourceKind::Url;
            gpui::div()
                .id(id)
                .text_color(theme.text_muted)
                .child(markdown_image_failure_caption(alt, source))
                .when(external, |el| el.cursor_pointer())
                .into_any_element()
        }
        Some(MarkdownImageState::Loading) => {
            let label = if alt.is_empty() { source } else { alt };
            gpui::div()
                .id(id)
                .text_color(theme.text_muted)
                .child(format!("{label} — loading…"))
                .into_any_element()
        }
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

    #[test]
    fn media_token_spans_prose_and_multiple() {
        let path = "/tmp/phone_shot_20261021_042820.jpg";
        let text = format!("A07 screenshot captured. MEDIA:{path} Done.");
        let spans = media_token_spans(&text);
        assert_eq!(spans.len(), 1);
        assert_eq!(&text[spans[0].0..spans[0].1], format!("MEDIA:{path}"));
        assert_eq!(spans[0].2, path);

        let two = format!("MEDIA:{path} and MEDIA:{path}");
        assert_eq!(media_token_spans(&two).len(), 2);
    }

    #[test]
    fn media_token_rejects_malformed_and_non_images() {
        assert!(media_token_spans("MEDIA:").is_empty());
        assert!(media_token_spans("MEDIA:not-a-path").is_empty());
        assert!(media_token_spans("MEDIA:/tmp/readme.txt").is_empty());
        assert!(media_token_spans("prefixMEDIA:/tmp/x.png").is_empty());
        let text = "see /tmp/unmentioned.png";
        assert!(media_token_spans(text).is_empty());
    }

    #[test]
    fn media_token_trims_trailing_sentence_punctuation() {
        let text = "Saved MEDIA:/tmp/shot.png.";
        let spans = media_token_spans(text);
        assert_eq!(spans.len(), 1);
        assert_eq!(spans[0].2, "/tmp/shot.png");
        assert_eq!(&text[spans[0].0..spans[0].1], "MEDIA:/tmp/shot.png");
    }

    #[test]
    fn expand_media_tokens_to_markdown_images() {
        let path = "/tmp/phone_shot.jpg";
        let text = format!("note MEDIA:{path}");
        let expanded = expand_media_tokens_for_markdown(&text);
        assert_eq!(expanded, format!("note ![MEDIA:{path}]({path})"));
    }

    #[test]
    fn expand_media_tokens_preserves_trailing_sentence_punctuation() {
        let text = "Saved MEDIA:/tmp/shot.png.";
        let spans = media_token_spans(text);
        assert_eq!(&text[spans[0].0..spans[0].1], "MEDIA:/tmp/shot.png");
        assert_eq!(spans[0].2, "/tmp/shot.png");
        let expanded = expand_media_tokens_for_markdown(text);
        assert_eq!(
            expanded,
            "Saved ![MEDIA:/tmp/shot.png](/tmp/shot.png)."
        );
    }

    #[test]
    fn media_token_failure_caption_keeps_literal_token() {
        let token = "MEDIA:/tmp/a.png";
        assert_eq!(
            markdown_image_failure_caption(token, "/tmp/a.png"),
            token
        );
        assert_eq!(
            markdown_image_failure_caption("alt", "/tmp/a.png"),
            "alt — /tmp/a.png"
        );
    }
}
