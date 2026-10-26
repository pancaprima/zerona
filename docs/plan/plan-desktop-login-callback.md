# Plan — desktop sign-in loopback callback

Status: **engine bind fallback implemented**; **headed desktop login not acceptance-verified** (WorkOS redirect registration is a live blocker).

## Symptom

Mac desktop toast: `Sign in failed: sign-in callback bind failed: Address already in use (os error 48)`.

## Root cause A — local loopback bind (`EADDRINUSE`)

| Observation | Location |
|---|---|
| Production engine sets default callback port **27641** (overridable via `ZERON_CALLBACK_PORT`) | `crates/engine/src/lib.rs:747-751` |
| Pre-fix headed sign-in bound **only** the configured port; bind errors surfaced before WorkOS or edge I/O | `crates/engine/src/auth.rs` (`ensure_loopback`, pre-patch) |
| Error string matches UI wrapping of `EngineError::Other` from RPC sign-in | `crates/ui/src/shell.rs:7015` |
| Staging documents intentional port split (27642 vs installed 27641) to avoid contention | `scripts/staging-app.sh:21-26` |

**Classification:** local loopback **bind** failure, not self-hosted edge routing. Sign-in never reaches `/auth/exchange` when bind fails.

**Fix (in tree):** `bind_sign_in_loopback` — on `AddrInUse` for a non-zero requested port, retry `127.0.0.1:0`. `ensure_loopback` stays idempotent (reuse stored port + listener task). Log when the bound port differs from the configured preference.

Self-host edge move is **correlated in time** but not causal for this error string: exchange runs only after a successful loopback callback.

## Root cause B — WorkOS redirect URI (live blocker after bind fix)

WorkOS validates `redirect_uri` at **authorize** time. The engine embeds the **actual bound loopback port** in each headed sign-in URL (`http://127.0.0.1:{port}/callback`).

[WorkOS AuthKit — Get authorization URL](https://workos.com/docs/reference/authkit/authentication/get-authorization-url) documents **wildcard ports** for `http://127.0.0.1` on native / loopback clients.

**Parent verification (live, self-host WorkOS client id — public identifier):**

| Probe `redirect_uri` | Result |
|---|---|
| `http://127.0.0.1:27641/callback` | `302` → `error.workos.com/redirect-uri-invalid` |
| `http://127.0.0.1:49183/callback` (arbitrary ephemeral) | same `redirect-uri-invalid` |
| `zeron://callback` | accepted (authorize proceeds) |
| hosted `{edge}/auth/cli/callback` | accepted (headless path) |

**Dashboard action (user-owned, not agent):** register `http://127.0.0.1:*/callback` (or per-port entries) for the self-host client so headed loopback sign-in can complete after bind succeeds.

Until that registration exists, **do not claim headed desktop login is verified end-to-end** — only bind fallback + stub-edge integration behavior.

### Reproducible authorize probes (no browser, no follow)

Substitute `CLIENT_ID` and a fresh `state` UUID. Expect `redirect-uri-invalid` until wildcard localhost is registered; expect `200` HTML or a WorkOS login redirect when registered.

```bash
# Should fail today on self-host client without localhost wildcard:
curl -sS --max-redirs 0 -o /dev/null -w '%{http_code} %{redirect_url}\n' \
  --get 'https://api.workos.com/user_management/authorize' \
  --data-urlencode 'response_type=code' \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode 'redirect_uri=http://127.0.0.1:27641/callback' \
  --data-urlencode 'provider=authkit' \
  --data-urlencode "state=$(uuidgen)"

# Control — known-good redirect for same client:
curl -sS --max-redirs 0 -o /dev/null -w '%{http_code} %{redirect_url}\n' \
  --get 'https://api.workos.com/user_management/authorize' \
  --data-urlencode 'response_type=code' \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode 'redirect_uri=zeron://callback' \
  --data-urlencode 'provider=authkit' \
  --data-urlencode "state=$(uuidgen)"
```

**Acceptance gate (headed desktop, production-like):**

1. WorkOS dashboard: `http://127.0.0.1:*/callback` registered for the desktop client id.
2. Authorize probe above for `http://127.0.0.1:27641/callback` returns WorkOS login (not `redirect-uri-invalid`).
3. With preferred port occupied, engine logs ephemeral fallback and authorize probe for the **chosen** port succeeds (same wildcard rule).
4. Full headed flow: browser completes AuthKit → loopback `/callback` → edge `/auth/exchange` → `SignedIn` in app.

## Auth / redirect flow (headed desktop)

1. `build_auth` → `Auth::start_sign_in` → `ensure_loopback` → authorize URL with `redirect_uri=http://127.0.0.1:{port}/callback`.
2. Browser → WorkOS AuthKit → redirect to loopback `/callback?code&state`.
3. Engine loopback handler validates **pending state** (CSRF), POSTs `{ code }` to `{edge_url}/auth/exchange` (no `redirect_uri` on wire).
4. Edge calls WorkOS `authenticateWithCode`; JWT/session commit in engine.

Headless paste-code path uses `{edge}/auth/cli/callback` and does not bind locally.

## Stale embedded edge URL (separate issue)

Wrong edge fails **later** (exchange unreachable / 401), not with `sign-in callback bind failed`.

## Verification strategy (CI — not run on this VPS)

`scripts/ci/test-core.sh` includes the `auth` integration binary. **Tests were not executed in the agent environment** (no `cargo`/`rustc`); CI runs the real suite.

| Test name | What it covers |
|---|---|
| `loopback_callback_ephemeral_bind_is_idempotent` | `callback_port = 0`, nonzero chosen port, idempotent `ensure_loopback`, independent OAuth state, concurrent `start_sign_in` shares listener |
| `loopback_callback_falls_back_when_preferred_port_in_use` | Squatter on preferred port → ephemeral bind, full callback + exchange, post-sign-in port stability |
| `loopback_callback_completes_headed_sign_in` | Ephemeral loopback path end-to-end against stub edge |
| `sign_out_invalidates_pending_and_in_flight_oauth_callbacks` | OAuth state / generation fencing |

**`auth` integration suitability for CI:** tests use a local `StubEdge` (`127.0.0.1:0`) and loopback `reqwest` only — no live WorkOS or public network dependency. Timing-sensitive tests use bounded `tokio::time::timeout` (≤5s). Default register in `test-core.sh`.

**Out of scope:** clearing loopback listener on `sign_out`, IPv6 dual-stack, WorkOS dashboard edits by agent.

## Static notes (`bind_sign_in_loopback`)

- `port == 0`: single bind attempt; any error propagates (no fallback).
- `port != 0` + `AddrInUse`: one retry on `127.0.0.1:0`; failure on retry propagates.
- Other `ErrorKind` values on the preferred bind propagate unchanged (permission denied, address not available, etc.).
