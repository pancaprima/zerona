# Plan: sync fork main with upstream zeronsh/zeron (v0.2.112)

## Strategy
- Merge (not rebase): `git merge upstream/main` on branch `sync/upstream-0.2.112`, then PR to `pancaprima/zerona` main. No force-push, open PRs (#6, #11) stay valid.

## Content search decision
- Take upstream #856 (fff-based `WorkspaceSearch`, tabbed cmd+K palette, `SearchWorkspaceContent`) as the base.
- Keep fork fuzzy content search: add optional `mode` (literal | fuzzy, default literal) to `SearchWorkspaceContentRequest`; fuzzy path reuses `FuzzyLineMatcher` from `workspace_content_search.rs`.
- Fork content index (PR #8/#9, `ZERONA_CONTENT_INDEX`) must not be silently dropped; if it cannot coexist with fff, keep it only behind the fuzzy mode or document the removal in the PR.

## Rules
- Product name stays Zerona (display only); crate/binary/bundle id stay `zeron*` / `sh.zeron.app`.
- Version files: take upstream version (0.2.112) unless fork-only change is required.
- Cargo.lock: regenerate by resolving Cargo.toml first, do not hand-merge hunks (no cargo build on VPS: use `cargo metadata --offline` only if it works, else take upstream lock and re-add fork-only deps).

## Verification
- No local build on VPS. Evidence = GitHub Actions on the PR.
