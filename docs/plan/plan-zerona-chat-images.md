# Plan: images in chat (remote paths + URLs)

## Goal
In the chat transcript, show images from:
1. File paths that live on the chat's owner device (e.g. a Linux VPS) while the user views the chat in the Mac app.
2. http(s) URLs.
Sources: assistant markdown `![alt](path-or-url)`, tool chips, user attachment trailers (fallback only).

## Decisions (user, 2026-10-20)
- Allowlist is STRICT (not whole-host).
- URL fetch happens on the chat host engine (not on the Mac).
- Paths and URLs ship in ONE PR.
- Mobile/iOS is out of scope (deferred).

## Findings
- Mac UI -> local engine -> peer relay -> owner engine via `targetDeviceId`; `forwardable()` in `crates/engine/src/rpc.rs`.
- `ReadAttachmentChunk` is jailed to uploads + chat cwds (no chat id) so `/root/foo/shot.png` is rejected.
- `ReadWorkspaceImage` can read any image on host for a valid chat id; too broad for chat use; keep for files panel only.
- Transcript sets `MediaUi.image: None` (`crates/ui/src/transcript.rs` `diagram_media`), so markdown images render as alt text only.

## Design
### Engine (owner device only)
New module `crates/engine/src/chat_media.rs`.

`ReadChatImage { chatId, path, offset? }` (forwardable):
- chat must exist and `chat.device_id == this engine` (same check as workspace_files).
- reject UNC/device/drive-relative/relative-with-`..`; relative paths resolve against chat cwd.
- canonicalize; reject symlink final component escape (canonical path must satisfy allowlist).
- allowlist (any of): uploads dir; chat cwd; `/tmp` (temp_dir); generated_images import root; EXACT paths mentioned in this chat's text (bounded scan: last N parts, path tokens and markdown image destinations; exact canonical match only, not prefix).
- extension allowlist (png jpg jpeg gif webp) + magic-byte sniff (reuse `uploads.rs` `raster_signature`).
- size cap 16 MiB; chunked base64 like attachments.

`FetchChatUrlImage { chatId, url, offset? }` (forwardable):
- same chat ownership check.
- https only (http only if explicitly env-enabled `ZERONA_CHAT_IMAGE_ALLOW_HTTP=1`).
- resolve DNS, block loopback/private/link-local/CGNAT/metadata/unique-local IPs; connect to the validated IP; re-validate each redirect (max 3); timeout 10s; size cap 16 MiB; content-type image/* AND magic sniff.
- cache on disk keyed by (chat id, sha256(url)) with TTL 1h under profile temp; serve chunks from cache.

Register both in `crates/rpc/src/lib.rs` `methods`, `forwardable`, dispatch in `rpc.rs`; types in `crates/proto/src/entities.rs`.

### UI
- `crates/ui/src/transcript.rs`: set `MediaUi.image` for Markdown and SETTLED LiveMarkdown rows; classify source: `http(s)://` -> FetchChatUrlImage, else path -> ReadChatImage (target = chat.device_id when different from local device). In-memory cache with count/size caps; show alt text + link while loading/failed.
- `crates/ui/src/tool_images.rs` + `attachments.rs`: if ReadAttachmentChunk fails with jail error, fall back to ReadChatImage.
- Reuse lightbox (`image_viewer`) on click.

## Tests (no cargo on VPS; run in CI)
- engine unit tests for `chat_media`: allowlist hit/miss, `..`, symlink escape, mentioned-path exact match, non-image bytes with .png extension, oversize, UNC.
- SSRF tests: IP classification table (v4/v6/mapped), redirect-to-private rejected.
- `crates/engine/tests/device_routing.rs`: ReadChatImage forwarded to device-b succeeds for allowlisted path, fails otherwise.
- Register any new integration binary in `scripts/ci/test-core.sh`; ensure macOS workflow nextest filters select new UI tests.
- UI: pure-function test for source classification and markdown image hook.

## Verification
- CI: `ui-tests.yml` (Linux) and `macos.yml` on the pushed branch; confirm headSha.
- Manual: Mac app connected to VPS chat, assistant writes `![x](/root/foo/shot.png)` and an https image URL.

## Out of scope
Mobile/iOS parity; non-image files; whole-host reads.

## Risks
- SSRF/DNS rebinding (mitigate by connecting to validated IP).
- Memory growth in UI cache (caps).
- macOS-only compile errors only visible in CI.
