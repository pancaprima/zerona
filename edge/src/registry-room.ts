/**
 * RegistryRoom — one Durable Object per per-user workspace registry
 * (`reg1/{orgId}/{userId}`), the wedge-proof replacement for the Loro
 * workspace doc (docs/registry-sync.md).
 *
 * The DO is the authority: it stores CURRENT row state in its SQLite (no
 * update log, no replay, no wasm), applies pushed ops with per-field LWW
 * (registry-core.ts), bumps one monotonic `seq` per accepted batch, and
 * broadcasts merged rows to every connected socket. Cold start is a table
 * read — the 2026-07/08 wedge class (CPU-limited history replay) cannot
 * exist here by construction.
 *
 * Persistence model:
 * - `rows` — the state. Written synchronously inside the message event (write
 *   rate is index-scale: renames, status flips — never transcript traffic).
 * - `meta` — seq counter, gcFloor, per-device push attribution, backup marks.
 * - presence — memory-only by construction (15s client beats, rebroadcast).
 *
 * Cloud runners (the Worker stamps RUNNER_DEVICE_HEADER for runner bearers)
 * act as their device, not the user: every row delivery to them is filtered
 * and every op they push must be on a row they host (registry-runner.ts).
 *
 * Hibernation discipline: ZERO wall-clock timers; ping/pong rides the
 * auto-response pair; the daily alarm does tombstone GC + the R2 backup.
 */
import { applyOp, validateOp, type Op, type Row } from "./registry-core";
import { chatHost, parentOf, runnerCanSee, runnerOpRefusal, type RunnerScope } from "./registry-runner";
import {
  AUTH_USER_HEADER,
  RUNNER_ACCOUNT_HEADER,
  RUNNER_DEVICE_HEADER,
  apnsConfig,
  fcmConfig,
  type Env
} from "./env";
import { isDeadToken as isDeadApnsToken, sendApns, type ApnsEnvironment } from "./apns";
import { isDeadToken as isDeadFcmToken, sendFcm } from "./fcm";
import {
  apnsPayload,
  chatForNotification,
  fcmPayload,
  fcmPayloadSummary,
  notificationFor,
  parsePrefs,
  type Category,
  type PushPrefs
} from "./push-notify";

const DAY_MS = 24 * 60 * 60 * 1000;
/** Android deferred push: one summary per chat after this delay unless read first. */
export const UNREAD_PUSH_DELAY_MS = 5 * 60 * 1000;
/** After enqueue, bump trigger msg_at when chat lastMessageAt rises within this window. */
export const TRIGGER_MARKER_WINDOW_MS = 30_000;
/** Tombstones older than this are purged; cursors from before the purge
 * horizon get a full resync (`gcFloor`). */
const TOMBSTONE_RETAIN_MS = 30 * DAY_MS;
/** Ops per push batch — a cascade delete of a huge space stays comfortably
 * under this; anything larger is split by the client. */
const MAX_BATCH_OPS = 500;
/** Serialized inbound frame budget. */
const MAX_FRAME_BYTES = 1_000_000;

interface SocketState {
  userId: string;
  device: string;
  /** Set once a valid hello established the session. */
  ready?: boolean;
  /** Cloud runner socket: its verified device id. Rows are filtered and
   * pushes ownership-checked for it. */
  runner?: string;
  /** The runner's account (logical Cloud device; JWT `cld`). */
  runnerAccount?: string;
}

/** An op a runner pushed that it does not own — skipped, not fatal. */
interface RejectedOp {
  kind: string;
  id: string;
  error: string;
}

interface WireOp extends Op {}

interface PushOutcome {
  ok: number;
  rejected: number;
  lastOkAt: number;
}

/** A phone that asked for notifications (push token + its choices). */
interface PushTarget {
  device: string;
  token: string;
  kind: "apns" | "fcm";
  environment: ApnsEnvironment;
  prefs: PushPrefs;
}

/** One notification decided by a push batch. */
interface Notification {
  chatId: string;
  title: string;
  category: Category;
}

/** Recent deliveries on /stats (the only place to see them). */
interface PushLogEntry {
  at: number;
  chatId: string;
  category: Category;
  device: string;
  result: string;
}

const TOKEN_RE = /^[0-9a-fA-F]{32,512}$/;
const FCM_TOKEN_RE = /^[^\s]{20,4096}$/;
const PUSH_LOG_MAX = 30;

export class RegistryRoom implements DurableObject {
  private readonly ctx: DurableObjectState;
  private readonly env: Env;
  /** device → last presence beat (epoch ms). Memory-only. */
  private readonly presence = new Map<string, number>();

  constructor(ctx: DurableObjectState, env: Env) {
    this.ctx = ctx;
    this.env = env;
    ctx.storage.sql.exec(
      "CREATE TABLE IF NOT EXISTS rows (kind TEXT NOT NULL, id TEXT NOT NULL, seq INTEGER NOT NULL, deleted INTEGER NOT NULL, del_hlc TEXT, fields TEXT NOT NULL, clocks TEXT NOT NULL, PRIMARY KEY (kind, id))"
    );
    ctx.storage.sql.exec("CREATE INDEX IF NOT EXISTS rows_seq ON rows (seq)");
    ctx.storage.sql.exec(
      "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
    );
    // Push targets are private to this room: never rows (those broadcast to
    // every socket, sync to every device and back up to R2).
    ctx.storage.sql.exec(
      "CREATE TABLE IF NOT EXISTS push_targets (device TEXT PRIMARY KEY, token TEXT NOT NULL, environment TEXT NOT NULL, prefs TEXT NOT NULL, updated_at INTEGER NOT NULL)"
    );
    const pushColumns = [...ctx.storage.sql.exec("PRAGMA table_info(push_targets)")];
    if (!pushColumns.some((c) => c.name === "kind")) {
      ctx.storage.sql.exec("ALTER TABLE push_targets ADD COLUMN kind TEXT NOT NULL DEFAULT 'apns'");
    }
    ctx.storage.sql.exec(
      "CREATE TABLE IF NOT EXISTS unread_triggers (chat_id TEXT NOT NULL, trigger_key TEXT NOT NULL, category TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (chat_id, trigger_key))"
    );
    const triggerColumns = [...ctx.storage.sql.exec("PRAGMA table_info(unread_triggers)")];
    if (!triggerColumns.some((c) => c.name === "msg_at")) {
      ctx.storage.sql.exec("ALTER TABLE unread_triggers ADD COLUMN msg_at INTEGER NOT NULL DEFAULT 0");
    }
    if (!triggerColumns.some((c) => c.name === "message_id")) {
      ctx.storage.sql.exec("ALTER TABLE unread_triggers ADD COLUMN message_id TEXT NOT NULL DEFAULT ''");
    }
    if (!triggerColumns.some((c) => c.name === "message_created_at")) {
      ctx.storage.sql.exec(
        "ALTER TABLE unread_triggers ADD COLUMN message_created_at INTEGER NOT NULL DEFAULT 0"
      );
    }
    ctx.storage.sql.exec(
      "CREATE TABLE IF NOT EXISTS read_receipts (chat_id TEXT NOT NULL, device TEXT NOT NULL, message_id TEXT NOT NULL, message_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (chat_id, device))"
    );
    // Same protocol-level keepalive as SessionRoom — and the same caveat: a
    // pong is runtime-answered and proves nothing about this DO's health.
    // Clients judge liveness by probe frames (crates/sync/src/registry.rs).
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  // ── meta helpers ──────────────────────────────────────────────────────────

  private getMeta(key: string): string | undefined {
    const rows = [...this.ctx.storage.sql.exec("SELECT value FROM meta WHERE key = ?", key)];
    return rows[0]?.value as string | undefined;
  }

  private setMeta(key: string, value: string): void {
    this.ctx.storage.sql.exec(
      "INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
      key,
      value
    );
  }

  private deleteMeta(key: string): void {
    this.ctx.storage.sql.exec("DELETE FROM meta WHERE key = ?", key);
  }

  /** Parsed backupDue ms, or null when unset / empty / invalid (never treat "" as 0). */
  private backupDueAt(): number | null {
    const raw = this.getMeta("backupDue");
    if (raw === undefined || raw === "") return null;
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? n : null;
  }

  private seq(): number {
    return Number(this.getMeta("seq") ?? "0");
  }

  private gcFloor(): number {
    return Number(this.getMeta("gcFloor") ?? "0");
  }

  // ── row storage ───────────────────────────────────────────────────────────

  private loadRow(kind: string, id: string): Row | undefined {
    const rows = [
      ...this.ctx.storage.sql.exec(
        "SELECT seq, deleted, del_hlc, fields, clocks FROM rows WHERE kind = ? AND id = ?",
        kind,
        id
      )
    ];
    const raw = rows[0];
    if (!raw) return undefined;
    return {
      kind,
      id,
      seq: raw.seq as number,
      deleted: (raw.deleted as number) === 1,
      ...(raw.del_hlc ? { delHlc: raw.del_hlc as string } : {}),
      fields: JSON.parse(raw.fields as string) as Row["fields"],
      clocks: JSON.parse(raw.clocks as string) as Row["clocks"]
    };
  }

  private saveRow(row: Row): void {
    this.ctx.storage.sql.exec(
      "INSERT INTO rows (kind, id, seq, deleted, del_hlc, fields, clocks) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(kind, id) DO UPDATE SET seq = excluded.seq, deleted = excluded.deleted, del_hlc = excluded.del_hlc, fields = excluded.fields, clocks = excluded.clocks",
      row.kind,
      row.id,
      row.seq,
      row.deleted ? 1 : 0,
      row.delHlc ?? null,
      JSON.stringify(row.fields),
      JSON.stringify(row.clocks)
    );
  }

  /** What one reader may receive: everything for user callers, owned rows
   * (plus all devices, and the projects of the chats it hosts) for a runner. */
  private visible(rows: Row[], runner: string | undefined, _account: string | undefined): Row[] {
    if (runner === undefined) return rows;
    const scope = this.runnerScope(runner);
    return rows.filter((row) => runnerCanSee(runner, row, scope));
  }

  /** What `device` reaches beyond its own rows: the projects of the live
   * chats it hosts, and the live chats whose parent it hosts (delegates it
   * started on other machines). */
  private runnerScope(device: string): RunnerScope {
    const spaces = new Set<string>();
    const hosted = new Set<string>();
    const parents: [string, string][] = [];
    for (const raw of this.ctx.storage.sql.exec<{ id: string; fields: string }>(
      "SELECT id, fields FROM rows WHERE kind = 'chats' AND deleted = 0"
    )) {
      try {
        const fields = JSON.parse(raw.fields) as Record<string, unknown>;
        if (fields.deviceId === device) {
          hosted.add(raw.id);
          if (typeof fields.spaceId === "string") spaces.add(fields.spaceId);
        }
        if (typeof fields.parentChatId === "string") parents.push([raw.id, fields.parentChatId]);
      } catch {
        /* a malformed row names no project and no parent */
      }
    }
    const children = new Set(parents.filter(([, parent]) => hosted.has(parent)).map(([id]) => id));
    return { spaces, children };
  }

  /** The hosts of the parents of the live chats `device` hosts: the runners
   * that delegated work to it (the Worker's device-room gate). */
  private parentHosts(device: string): string[] {
    const hosts = new Set<string>();
    for (const raw of this.ctx.storage.sql.exec<{ fields: string }>(
      "SELECT fields FROM rows WHERE kind = 'chats' AND deleted = 0"
    )) {
      try {
        const fields = JSON.parse(raw.fields) as Record<string, unknown>;
        if (fields.deviceId !== device || typeof fields.parentChatId !== "string") continue;
        const host = chatHost(this.loadRow("chats", fields.parentChatId));
        if (host) hosts.add(host);
      } catch {
        /* a malformed row delegates nothing */
      }
    }
    return [...hosts];
  }

  private rowsSince(cursor: number): Row[] {
    const out: Row[] = [];
    for (const raw of this.ctx.storage.sql.exec(
      "SELECT kind, id, seq, deleted, del_hlc, fields, clocks FROM rows WHERE seq > ? ORDER BY seq",
      cursor
    )) {
      out.push({
        kind: raw.kind as string,
        id: raw.id as string,
        seq: raw.seq as number,
        deleted: (raw.deleted as number) === 1,
        ...(raw.del_hlc ? { delHlc: raw.del_hlc as string } : {}),
        fields: JSON.parse(raw.fields as string) as Row["fields"],
        clocks: JSON.parse(raw.clocks as string) as Row["clocks"]
      });
    }
    return out;
  }

  // ── HTTP surface (only reachable through the authed Worker; org membership
  //    and the per-user room name were enforced there) ──────────────────────

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const userId = request.headers.get(AUTH_USER_HEADER);
    if (!userId) return json({ error: "unauthenticated" }, 401);
    const runner = request.headers.get(RUNNER_DEVICE_HEADER) || undefined;
    const runnerAccount = runner === undefined ? undefined : request.headers.get(RUNNER_ACCOUNT_HEADER) || undefined;
    // Defense in depth (the Worker refuses these too): a runner syncs rows
    // and nothing else — no stats (push targets, push log), no APNs target
    // registration (notification titles of every chat), no operator wipe.
    if (runner !== undefined && !["/ws", "/rows", "/push"].includes(url.pathname)) {
      return json({ error: "forbidden" }, 403);
    }

    if (url.pathname === "/ws") {
      // A runner's device identity is the verified one, never self-declared.
      const device = runner ?? url.searchParams.get("device") ?? "";
      const pair = new WebSocketPair();
      this.ctx.acceptWebSocket(pair[1]);
      const state: SocketState = {
        userId,
        device,
        ...(runner ? { runner } : {}),
        ...(runnerAccount ? { runnerAccount } : {})
      };
      pair[1].serializeAttachment(state);
      return new Response(null, { status: 101, webSocket: pair[0] });
    }

    if (url.pathname === "/stats" && request.method === "GET") {
      const rowCount = [...this.ctx.storage.sql.exec("SELECT COUNT(*) AS n FROM rows")][0]
        ?.n as number;
      const tombstones = [
        ...this.ctx.storage.sql.exec("SELECT COUNT(*) AS n FROM rows WHERE deleted = 1")
      ][0]?.n as number;
      return json({
        seq: this.seq(),
        gcFloor: this.gcFloor(),
        rowCount,
        tombstones,
        connectedSockets: this.ctx.getWebSockets().length,
        // The ONLY per-device attribution surface — kept from the 2026-08-05
        // incident tooling (SessionRoom's /stats pushOutcomes).
        pushOutcomes: JSON.parse(this.getMeta("pushOutcomes") ?? "{}") as Record<string, PushOutcome>,
        lastBackupSeq: Number(this.getMeta("backupSeq") ?? "0"),
        lastGcAt: Number(this.getMeta("lastGcAt") ?? "0"),
        pushTargets: this.pushTargets().map((t) => ({ device: t.device, environment: t.environment, prefs: t.prefs })),
        pushConfigured: apnsConfig(this.env) !== undefined || fcmConfig(this.env) !== undefined,
        pushLog: JSON.parse(this.getMeta("pushLog") ?? "[]") as PushLogEntry[]
      });
    }

    // A phone registering (or updating) its APNs token and choices; DELETE on
    // sign-out or when the user turns notifications off.
    if (url.pathname === "/push-target") {
      const device = url.searchParams.get("device") ?? "";
      if (device === "") return json({ error: "bad_request", message: "device required" }, 400);
      if (request.method === "DELETE") {
        this.ctx.storage.sql.exec("DELETE FROM push_targets WHERE device = ?", device);
        return json({ ok: true });
      }
      if (request.method === "POST") {
        let body: Record<string, unknown>;
        try {
          body = (await request.json()) as Record<string, unknown>;
        } catch {
          return json({ error: "bad_request", message: "malformed body" }, 400);
        }
        const kind = body.kind === "fcm" ? "fcm" : "apns";
        const token = typeof body.token === "string" ? body.token : "";
        let environment: ApnsEnvironment | "";
        if (kind === "fcm") {
          if (!FCM_TOKEN_RE.test(token)) {
            return json({ error: "bad_request", message: "token required" }, 400);
          }
          environment = "";
        } else {
          const env =
            body.environment === "sandbox" ? "sandbox" : body.environment === "production" ? "production" : undefined;
          if (!TOKEN_RE.test(token) || env === undefined) {
            return json({ error: "bad_request", message: "token and environment required" }, 400);
          }
          environment = env;
        }
        this.ctx.storage.sql.exec(
          "INSERT INTO push_targets (device, token, environment, prefs, updated_at, kind) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(device) DO UPDATE SET token = excluded.token, environment = excluded.environment, prefs = excluded.prefs, updated_at = excluded.updated_at, kind = excluded.kind",
          device,
          token,
          environment,
          JSON.stringify(parsePrefs(body.prefs)),
          Date.now(),
          kind
        );
        return json({ ok: true });
      }
    }

    if (url.pathname === "/seen" && request.method === "POST") {
      const device = url.searchParams.get("device") ?? "";
      if (device === "") return json({ error: "bad_request", message: "device required" }, 400);
      let body: Record<string, unknown>;
      try {
        body = (await request.json()) as Record<string, unknown>;
      } catch {
        return json({ error: "bad_request", message: "malformed body" }, 400);
      }
      const chatId = typeof body.chatId === "string" ? body.chatId : "";
      const messageId = typeof body.messageId === "string" ? body.messageId : "";
      const messageAt = body.messageAt;
      if (chatId === "" || messageId === "") {
        return json({ error: "bad_request", message: "chatId and messageId required" }, 400);
      }
      if (typeof messageAt !== "number" || !Number.isFinite(messageAt) || messageAt < 0) {
        return json({ error: "bad_request", message: "messageAt must be a finite number" }, 400);
      }
      const now = Date.now();
      if (messageAt > now + 60 * 60 * 1000) {
        return json({ error: "bad_request", message: "messageAt too far in the future" }, 400);
      }
      const rows = [
        ...this.ctx.storage.sql.exec(
          "SELECT message_at FROM read_receipts WHERE chat_id = ? AND device = ?",
          chatId,
          device
        )
      ];
      const prevAt = rows[0]?.message_at as number | undefined;
      if (prevAt !== undefined && messageAt < prevAt) {
        return json({ ok: true });
      }
      const updatedAt = now;
      this.ctx.storage.sql.exec(
        "INSERT INTO read_receipts (chat_id, device, message_id, message_at, updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(chat_id, device) DO UPDATE SET message_id = excluded.message_id, message_at = excluded.message_at, updated_at = excluded.updated_at",
        chatId,
        device,
        messageId,
        messageAt,
        updatedAt
      );
      this.ctx.storage.sql.exec(
        `DELETE FROM unread_triggers WHERE chat_id = ? AND (
          (message_id != '' AND (message_id = ? OR (message_created_at > 0 AND message_created_at < ?)))
          OR (message_id = '' AND msg_at <= ?)
        )`,
        chatId,
        messageId,
        messageAt,
        messageAt
      );
      this.scheduleAlarmSafe();
      return json({ ok: true });
    }

    if (url.pathname === "/rows" && request.method === "GET") {
      // Pull over plain HTTPS: with `?since=` this is the WS hello's exact
      // delta answer (same full/gcFloor rules); without it, the original
      // full-table repair read. `?device=&beat=1` doubles as a presence
      // beat so an HTTP-only client stays visible to socket peers.
      const sinceRaw = url.searchParams.get("since");
      const sinceNum = sinceRaw === null ? NaN : Number(sinceRaw);
      const cursor = Number.isInteger(sinceNum) && sinceNum >= 0 ? sinceNum : null;
      const device = runner ?? url.searchParams.get("device") ?? "";
      if (device !== "" && url.searchParams.get("beat") === "1") {
        const at = Date.now();
        this.presence.set(device, at);
        for (const socket of this.ctx.getWebSockets()) {
          const socketState = socket.deserializeAttachment() as SocketState | null;
          if (!socketState?.ready) continue;
          send(socket, { t: "presence", device, at });
        }
      }
      const seq = this.seq();
      const gcFloor = this.gcFloor();
      const full = cursor === null || cursor < gcFloor || cursor > seq;
      return json({
        seq,
        full,
        gcFloor,
        rows: this.visible(full ? this.rowsSince(0) : this.rowsSince(cursor), runner, runnerAccount),
        presence: Object.fromEntries(this.presence)
      });
    }

    // The Worker's chat2/blob gate for runners: who hosts this chat? Only
    // reachable through the Worker, which asks the caller's own room.
    if (url.pathname === "/chat-host" && request.method === "GET") {
      const chat = url.searchParams.get("chat") ?? "";
      const row = this.loadRow("chats", chat);
      const parent = parentOf(row);
      return json({
        deviceId: chatHost(row) ?? null,
        // A delegate's parent host may reach it too (its runner started it).
        parentHost: (parent && chatHost(this.loadRow("chats", parent))) ?? null,
        // Whether the id was ever used, tombstones included: a new delegate
        // must take a fresh id, never an existing (or deleted) chat's.
        known: row !== undefined
      });
    }

    // The Worker's device-room gate for runners: which runners delegated
    // work to this device (host the parents of the chats it hosts)?
    if (url.pathname === "/device-parents" && request.method === "GET") {
      const device = url.searchParams.get("device") ?? "";
      return json({ hosts: this.parentHosts(device) });
    }

    if (url.pathname === "/push" && request.method === "POST") {
      // Push over plain HTTPS — the WS push's fallback twin for networks
      // where the upgrade never completes. Same validation, same atomic
      // apply, same rows broadcast to live sockets; the ack is the response
      // body. LWW clocks make replayed batches apply zero ops, so
      // at-least-once delivery (including 0-RTT/early-data replays) is safe.
      const device = runner ?? url.searchParams.get("device") ?? "";
      // Pre-read cap (the WS path gets this for free from MAX_FRAME_BYTES):
      // a legitimate batch is bounded well under this by MAX_BATCH_OPS ×
      // MAX_OP_BYTES; anything larger only burns this room's own CPU.
      const declared = Number(request.headers.get("content-length") ?? "0");
      if (declared > 2 * 1024 * 1024) return json({ error: "too_large" }, 413);
      let frame: Record<string, unknown>;
      try {
        frame = (await request.json()) as Record<string, unknown>;
      } catch {
        return json({ error: "bad_push", message: "malformed body" }, 400);
      }
      const outcome = this.applyPushBatch(device, frame, runner);
      if (!outcome.ok) return json({ error: outcome.code, message: outcome.message }, 400);
      return json({
        batch: outcome.batch,
        seq: outcome.seq,
        applied: outcome.applied,
        ...(outcome.rejected.length > 0 ? { rejected: outcome.rejected } : {})
      });
    }

    if (url.pathname === "/reset" && request.method === "POST") {
      // Operator wipe. Recovery is automatic and lossless: every client
      // detects `state.seq < cursor` on its next hello and re-seeds the table
      // from its local rows with their ORIGINAL clocks (registry-core
      // rowToSeedOp) — the ws4 repair recipe, built in.
      this.ctx.storage.sql.exec("DELETE FROM rows");
      this.ctx.storage.sql.exec("DELETE FROM meta");
      for (const ws of this.ctx.getWebSockets()) {
        try {
          ws.close(4410, "registry reset");
        } catch {
          /* already gone */
        }
      }
      return json({ ok: true });
    }

    return json({ error: "not found" }, 404);
  }

  // ── WebSocket protocol ────────────────────────────────────────────────────

  async webSocketMessage(ws: WebSocket, message: ArrayBuffer | string): Promise<void> {
    if (typeof message !== "string") {
      ws.close(1003, "text frames only");
      return;
    }
    if (message.length > MAX_FRAME_BYTES) {
      ws.close(1009, "frame too large");
      return;
    }
    let frame: Record<string, unknown>;
    try {
      frame = JSON.parse(message) as Record<string, unknown>;
    } catch {
      ws.close(1002, "bad json");
      return;
    }
    const state = ws.deserializeAttachment() as SocketState;
    switch (frame.t) {
      case "hello":
        this.handleHello(ws, state, frame);
        return;
      case "push":
        this.handlePush(ws, state, frame);
        return;
      case "presence":
        this.handlePresence(ws, state, frame);
        return;
      case "probe":
        send(ws, { t: "probe-ok", seq: this.seq() });
        return;
      default:
        send(ws, { t: "error", code: "bad_frame", message: `unknown frame: ${String(frame.t)}` });
    }
  }

  async webSocketClose(): Promise<void> {
    /* nothing buffered; rows are written synchronously on push */
  }

  async webSocketError(): Promise<void> {
    /* ditto */
  }

  private handleHello(ws: WebSocket, state: SocketState, frame: Record<string, unknown>): void {
    const cursor = typeof frame.cursor === "number" && frame.cursor >= 0 ? frame.cursor : null;
    if (state.runner === undefined && typeof frame.device === "string" && frame.device.length > 0) {
      state.device = frame.device;
    }
    state.ready = true;
    ws.serializeAttachment(state);
    const seq = this.seq();
    const gcFloor = this.gcFloor();
    // A cursor from before the tombstone-GC horizon may have missed purged
    // deletes; a cursor AHEAD of the server means the server lost state (a
    // reset/wipe) — both force `full`, and the client reacts by replacing
    // (former) or re-seeding the server (latter; see registry.rs).
    const full = cursor === null || cursor < gcFloor || cursor > seq;
    const rows = this.visible(full ? this.rowsSince(0) : this.rowsSince(cursor), state.runner, state.runnerAccount);
    send(ws, {
      t: "state",
      seq,
      full,
      gcFloor,
      rows,
      presence: Object.fromEntries(this.presence)
    });
  }

  private handlePush(ws: WebSocket, state: SocketState, frame: Record<string, unknown>): void {
    if (!state.ready) {
      this.recordPush(state.device, false);
      send(ws, { t: "error", code: "bad_push", message: "hello first / malformed push" });
      return;
    }
    const outcome = this.applyPushBatch(state.device, frame, state.runner);
    if (!outcome.ok) {
      send(ws, { t: "error", code: outcome.code, message: outcome.message });
      return;
    }
    // Skipped runner ops are reported like invalid ones (error frames the
    // client logs and counts); the ack still retires the batch.
    for (const rejected of outcome.rejected.slice(0, 20)) {
      send(ws, { t: "error", code: "not_owner", message: `${rejected.kind}/${rejected.id}: ${rejected.error}` });
    }
    send(ws, {
      t: "ack",
      batch: outcome.batch,
      seq: outcome.seq,
      applied: outcome.applied,
      ...(outcome.rejected.length > 0 ? { rejected: outcome.rejected } : {})
    });
  }

  /** Validate + atomically apply one op batch and broadcast merged rows to
   * every ready socket -- shared by the WS push and the HTTP `POST /push`
   * fallback. The caller delivers the ack/error on its own transport.
   * `runner` (a Cloud device) may only touch rows it hosts: its other ops
   * are skipped and reported in `rejected`, the rest of the batch applies. */
  private applyPushBatch(
    device: string,
    frame: Record<string, unknown>,
    runner?: string
  ):
    | { ok: false; code: string; message: string }
    | { ok: true; batch: string; seq: number; applied: number; rejected: RejectedOp[] } {
    const batch = typeof frame.batch === "string" ? frame.batch : "";
    if (batch === "" || !Array.isArray(frame.ops)) {
      this.recordPush(device, false);
      return { ok: false, code: "bad_push", message: "malformed push" };
    }
    const ops = frame.ops as WireOp[];
    if (ops.length === 0 || ops.length > MAX_BATCH_OPS) {
      this.recordPush(device, false);
      return { ok: false, code: "bad_push", message: `batch of ${ops.length} ops` };
    }
    for (const op of ops) {
      const invalid = validateOp(op);
      if (invalid) {
        // Reject the WHOLE batch: batches are transactional (cascade deletes)
        // and a client that builds one bad op is a client bug to surface, not
        // to partially apply. Rejections are attributed per device on /stats.
        this.recordPush(device, false);
        return { ok: false, code: "invalid_op", message: `${op.kind}/${op.id}: ${invalid}` };
      }
    }

    // Apply atomically: DO events are single-threaded and SQLite writes in an
    // event commit together, so a mid-batch crash never persists half a batch.
    const nextSeq = this.seq() + 1;
    const touched = new Map<string, Row>();
    /** Each touched session row as it was before this batch. */
    const sessionsBefore = new Map<string, Row | undefined>();
    let applied = 0;
    const rejected: RejectedOp[] = [];
    // Children as the batch starts: a delegate the runner may manage.
    const children = runner === undefined ? undefined : this.runnerScope(runner).children;
    for (const op of ops) {
      const key = `${op.kind} ${op.id}`;
      const before = touched.get(key) ?? this.loadRow(op.kind, op.id);
      if (runner !== undefined) {
        const refusal = runnerOpRefusal(runner, before, op, children);
        if (refusal !== null) {
          rejected.push({ kind: op.kind, id: op.id, error: refusal });
          continue;
        }
      }
      if (op.kind === "sessions" && !sessionsBefore.has(op.id)) sessionsBefore.set(op.id, before);
      const { row, changed } = applyOp(before, op);
      if (!changed || row === undefined) continue;
      applied += 1;
      row.seq = nextSeq;
      touched.set(key, row);
    }
    if (applied > 0) {
      for (const row of touched.values()) {
        this.saveRow(row);
        if (row.kind === "chats") {
          if (row.deleted || row.fields.archived === true) {
            this.clearUnreadQueue(row.id);
          } else {
            this.bumpTriggerMarkersFromChat(row);
          }
        }
      }
      this.setMeta("seq", String(nextSeq));
      this.markBackupDirty();
    }
    this.recordPush(device, true);
    if (rejected.length > 0) this.recordPush(device, false);
    const seq = applied > 0 ? nextSeq : this.seq();
    if (applied > 0) {
      // Merged full rows to EVERY ready socket (sender included -- its op may
      // have lost LWW, and the merged row is the truth it must display).
      // Rows go out BEFORE the ack so the sender's authoritative state is
      // current by the time the ack retires its optimistic pending batch --
      // no between-frames flicker window.
      const rows = [...touched.values()];
      for (const socket of this.ctx.getWebSockets()) {
        const socketState = socket.deserializeAttachment() as SocketState | null;
        if (!socketState?.ready) continue;
        // A runner still gets the frame (possibly empty) so its cursor
        // advances with the room's seq.
        send(socket, { t: "rows", seq, rows: this.visible(rows, socketState.runner, socketState.runnerAccount) });
      }
      this.notifySessions(sessionsBefore, touched);
    }
    return { ok: true, batch, seq, applied, rejected };
  }

  // ── push notifications ───────────────────────────────────────────────────

  private pushTargets(): PushTarget[] {
    return [...this.ctx.storage.sql.exec("SELECT device, token, environment, prefs, kind FROM push_targets")].map(
      (raw) => ({
        device: raw.device as string,
        token: raw.token as string,
        kind: (raw.kind as string) === "fcm" ? "fcm" : "apns",
        environment: (raw.environment as string) === "sandbox" ? "sandbox" : "production",
        prefs: parsePrefs(JSON.parse(raw.prefs as string))
      })
    );
  }

  private unreadPushDelayMs(): number {
    const override = this.getMeta("unreadPushDelayMs");
    if (override !== undefined && override !== "") {
      const n = Number(override);
      if (Number.isFinite(n) && n >= 0) return n;
    }
    return UNREAD_PUSH_DELAY_MS;
  }

  private clearUnreadQueue(chatId: string): void {
    this.ctx.storage.sql.exec("DELETE FROM unread_triggers WHERE chat_id = ?", chatId);
  }

  private triggerKey(category: Category, session: Row): string {
    const fields = session.fields;
    if (category === "done") {
      const turn = typeof fields.lastCompletedTurn === "string" ? fields.lastCompletedTurn : "";
      return `done:${turn}`;
    }
    const updatedAt = typeof fields.updatedAt === "number" ? fields.updatedAt : 0;
    return `${category}:${updatedAt}`;
  }

  /** Registry chat rows store lastMessageAt as epoch ms (see crates/doc registry opt_ms). */
  private parseLastMessageAtMs(fields: Record<string, unknown>): number | null {
    const v = fields.lastMessageAt;
    if (typeof v === "number" && Number.isFinite(v)) return v;
    if (typeof v === "string" && v !== "") {
      const n = Number(v);
      if (Number.isFinite(n)) return n;
      const parsed = Date.parse(v);
      if (Number.isFinite(parsed)) return parsed;
    }
    return null;
  }

  private parseLastMessageId(fields: Record<string, unknown>): string {
    const v = fields.lastMessageId;
    return typeof v === "string" ? v : "";
  }

  /** Message id for read matching: done → completed transcript entry id (`lastCompletedTurn`, same as clients' seen reports); other categories → same-batch chat `lastMessageId`. */
  private resolveTriggerMessageId(
    category: Category,
    session: Row,
    chat: Row | undefined,
    chatUpdatedInBatch: boolean
  ): string {
    if (category === "done") {
      const completed =
        typeof session.fields.lastCompletedTurn === "string" ? session.fields.lastCompletedTurn : "";
      if (completed !== "") return completed;
    }
    if (chatUpdatedInBatch && chat !== undefined) {
      const fromChat = this.parseLastMessageId(chat.fields);
      if (fromChat !== "") return fromChat;
    }
    return "";
  }

  private resolveTriggerMsgAt(chat: Row | undefined, session: Row, createdAt: number): number {
    const fromChat = chat ? this.parseLastMessageAtMs(chat.fields) : null;
    if (fromChat !== null) return fromChat;
    const updatedAt = typeof session.fields.updatedAt === "number" ? session.fields.updatedAt : null;
    if (updatedAt !== null) return updatedAt;
    return createdAt;
  }

  /** Transcript message createdAt for read matching; not bumped from chat `lastMessageAt` on `done`. */
  private resolveTriggerMessageCreatedAt(
    category: Category,
    session: Row,
    chat: Row | undefined,
    createdAt: number,
    chatUpdatedInBatch: boolean
  ): number {
    const updatedAt = typeof session.fields.updatedAt === "number" ? session.fields.updatedAt : null;
    if (category === "done") {
      if (updatedAt !== null) return updatedAt;
      return createdAt;
    }
    if (chatUpdatedInBatch && chat !== undefined) {
      const fromChat = this.parseLastMessageAtMs(chat.fields);
      if (fromChat !== null) return fromChat;
    }
    if (updatedAt !== null) return updatedAt;
    return createdAt;
  }

  private bumpTriggerMarkersFromChat(chat: Row): void {
    const lastMsg = this.parseLastMessageAtMs(chat.fields);
    if (lastMsg === null) return;
    const lastId = this.parseLastMessageId(chat.fields);
    const windowStart = Date.now() - TRIGGER_MARKER_WINDOW_MS;
    if (lastId !== "") {
      this.ctx.storage.sql.exec(
        `UPDATE unread_triggers SET msg_at = MAX(msg_at, ?),
          message_id = CASE WHEN category = 'done' THEN message_id ELSE ? END
         WHERE chat_id = ? AND created_at >= ?`,
        lastMsg,
        lastId,
        chat.id,
        windowStart
      );
    } else {
      this.ctx.storage.sql.exec(
        "UPDATE unread_triggers SET msg_at = MAX(msg_at, ?) WHERE chat_id = ? AND created_at >= ?",
        lastMsg,
        chat.id,
        windowStart
      );
    }
  }

  private enqueueUnreadTrigger(
    chatId: string,
    category: Category,
    session: Row,
    createdAt: number,
    chat: Row | undefined,
    chatUpdatedInBatch: boolean
  ): void {
    const key = this.triggerKey(category, session);
    const msgAt = this.resolveTriggerMsgAt(chat, session, createdAt);
    const messageCreatedAt = this.resolveTriggerMessageCreatedAt(
      category,
      session,
      chat,
      createdAt,
      chatUpdatedInBatch
    );
    const messageId = this.resolveTriggerMessageId(category, session, chat, chatUpdatedInBatch);
    this.ctx.storage.sql.exec(
      "INSERT OR IGNORE INTO unread_triggers (chat_id, trigger_key, category, created_at, msg_at, message_id, message_created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      chatId,
      key,
      category,
      createdAt,
      msgAt,
      messageId,
      messageCreatedAt
    );
  }

  private isTriggerRead(
    chatId: string,
    msgAt: number,
    messageCreatedAt: number,
    messageId: string
  ): boolean {
    if (messageId !== "") {
      const readBaseline = messageCreatedAt > 0 ? messageCreatedAt : msgAt;
      const rows = [
        ...this.ctx.storage.sql.exec(
          "SELECT 1 FROM read_receipts WHERE chat_id = ? AND (message_id = ? OR message_at > ?) LIMIT 1",
          chatId,
          messageId,
          readBaseline
        )
      ];
      return rows.length > 0;
    }
    const rows = [
      ...this.ctx.storage.sql.exec(
        "SELECT 1 FROM read_receipts WHERE chat_id = ? AND message_at > ? LIMIT 1",
        chatId,
        msgAt
      )
    ];
    return rows.length > 0;
  }

  private earliestUnreadPushDue(): number | null {
    const rows = [...this.ctx.storage.sql.exec("SELECT MIN(created_at) AS m FROM unread_triggers")];
    const m = rows[0]?.m as number | undefined;
    if (m === undefined || m === null) return null;
    return m + this.unreadPushDelayMs();
  }

  private scheduleAlarmSafe(): void {
    void this.scheduleAlarm().catch((err: Error) => {
      console.error("scheduleAlarm failed:", err.message);
    });
  }

  private async scheduleAlarm(): Promise<void> {
    const pushDue = this.earliestUnreadPushDue() ?? Infinity;
    const dueAt = this.backupDueAt();
    const backup = this.getMeta("backupDirty") === "1" && dueAt !== null ? dueAt : Infinity;
    const next = Math.min(pushDue, backup);
    if (!Number.isFinite(next)) return;
    const alarmAt = Math.max(next, Date.now());
    if (!Number.isFinite(alarmAt) || alarmAt <= 0) return;
    const existing = await this.ctx.storage.getAlarm();
    if (existing === null || alarmAt < existing) await this.ctx.storage.setAlarm(alarmAt);
  }

  private deliverFcm(
    fcmCfg: NonNullable<ReturnType<typeof fcmConfig>>,
    target: PushTarget,
    chatId: string,
    title: string,
    categories: Category[],
    entry: PushLogEntry
  ): () => Promise<void> {
    const payload =
      categories.length === 1
        ? fcmPayload(chatId, title, categories[0])
        : fcmPayloadSummary(chatId, title, categories);
    return async () => {
      try {
        const r = await sendFcm(fcmCfg, target.token, payload, chatId);
        entry.result = r.reason ? `${r.status} ${r.reason}` : String(r.status);
        if (isDeadFcmToken(r)) {
          this.ctx.storage.sql.exec("DELETE FROM push_targets WHERE device = ? AND token = ?", target.device, target.token);
        }
      } catch (err) {
        entry.result = `error ${(err as Error).message}`;
      }
    };
  }

  private deliverApns(
    apnsCfg: NonNullable<ReturnType<typeof apnsConfig>>,
    target: PushTarget,
    note: Notification,
    entry: PushLogEntry
  ): () => Promise<void> {
    return async () => {
      try {
        const r = await sendApns(
          apnsCfg,
          target.environment,
          target.token,
          apnsPayload(note.chatId, note.title, note.category),
          note.chatId
        );
        entry.result = r.reason ? `${r.status} ${r.reason}` : String(r.status);
        if (isDeadApnsToken(r)) {
          this.ctx.storage.sql.exec("DELETE FROM push_targets WHERE device = ? AND token = ?", target.device, target.token);
        }
      } catch (err) {
        entry.result = `error ${(err as Error).message}`;
      }
    };
  }

  /** Session rows that changed in a batch → APNs immediately; FCM targets enqueue
   * a deferred summary per chat. Best effort: a failed delivery is logged, never retried. */
  private notifySessions(before: Map<string, Row | undefined>, touched: Map<string, Row>): void {
    if (before.size === 0) return;
    const now = Date.now();
    const notes: Notification[] = [];
    let enqueuedFcm = false;
    for (const [chatId, prev] of before) {
      const next = touched.get(`sessions ${chatId}`);
      if (!next) continue;
      const category = notificationFor(prev, next, now);
      if (category === null) continue;
      const chat = chatForNotification(touched.get(`chats ${chatId}`) ?? this.loadRow("chats", chatId));
      if (chat === null) {
        this.clearUnreadQueue(chatId);
        continue;
      }
      notes.push({ chatId, title: chat.title, category });
      const chatRow = touched.get(`chats ${chatId}`) ?? this.loadRow("chats", chatId);
      this.enqueueUnreadTrigger(chatId, category, next, now, chatRow, touched.has(`chats ${chatId}`));
      enqueuedFcm = true;
    }
    if (notes.length === 0) return;
    if (enqueuedFcm) this.scheduleAlarmSafe();
    const targets = this.pushTargets();
    const apnsCfg = apnsConfig(this.env);
    const apnsTargets = targets.filter((t) => t.kind === "apns");
    const log: PushLogEntry[] = [];
    const sends: Array<() => Promise<void>> = [];
    for (const note of notes) {
      for (const target of apnsTargets) {
        if (!target.prefs[note.category]) continue;
        const entry: PushLogEntry = { at: now, chatId: note.chatId, category: note.category, device: target.device, result: "pending" };
        log.push(entry);
        if (apnsCfg === undefined) {
          entry.result = "not configured";
          continue;
        }
        sends.push(this.deliverApns(apnsCfg, target, note, entry));
      }
    }
    if (targets.length === 0) {
      for (const note of notes) log.push({ at: now, chatId: note.chatId, category: note.category, device: "", result: "no targets" });
    }
    if (log.length > 0) this.appendPushLog(log);
    if (sends.length > 0) {
      this.ctx.waitUntil(
        Promise.all(sends.map((send) => send())).then(() => this.appendPushLog([], log))
      );
    }
  }

  private async processDueUnreadPushes(): Promise<void> {
    const delay = this.unreadPushDelayMs();
    const cutoff = Date.now() - delay;
    const dueChats = [
      ...this.ctx.storage.sql.exec<{ chat_id: string }>(
        "SELECT chat_id FROM unread_triggers GROUP BY chat_id HAVING MIN(created_at) <= ?",
        cutoff
      )
    ].map((r) => r.chat_id);
    if (dueChats.length === 0) return;
    const fcmCfg = fcmConfig(this.env);
    const fcmTargets = this.pushTargets().filter((t) => t.kind === "fcm");
    const now = Date.now();
    const log: PushLogEntry[] = [];
    const sends: Array<() => Promise<void>> = [];
    for (const chatId of dueChats) {
      const chat = chatForNotification(this.loadRow("chats", chatId));
      if (chat === null) {
        this.clearUnreadQueue(chatId);
        continue;
      }
      const triggers = [
        ...this.ctx.storage.sql.exec<{
          category: string;
          msg_at: number;
          message_id: string;
          message_created_at: number;
        }>(
          "SELECT category, msg_at, message_id, message_created_at FROM unread_triggers WHERE chat_id = ? ORDER BY created_at",
          chatId
        )
      ];
      const unread: Category[] = [];
      for (const t of triggers) {
        const mid = typeof t.message_id === "string" ? t.message_id : "";
        const messageCreatedAt =
          typeof t.message_created_at === "number" ? t.message_created_at : 0;
        if (!this.isTriggerRead(chatId, t.msg_at as number, messageCreatedAt, mid)) {
          unread.push(t.category as Category);
        }
      }
      this.clearUnreadQueue(chatId);
      if (unread.length === 0) continue;
      for (const target of fcmTargets) {
        const enabled = unread.filter((c) => target.prefs[c]);
        if (enabled.length === 0) continue;
        const entry: PushLogEntry = {
          at: now,
          chatId,
          category: enabled.includes("input") ? "input" : enabled.includes("failed") ? "failed" : enabled[0],
          device: target.device,
          result: "pending"
        };
        log.push(entry);
        if (fcmCfg === undefined) {
          entry.result = "not configured";
          continue;
        }
        sends.push(this.deliverFcm(fcmCfg, target, chatId, chat.title, enabled, entry));
      }
    }
    if (log.length === 0) return;
    this.appendPushLog(log);
    if (sends.length > 0) {
      await Promise.all(sends.map((send) => send()));
      this.appendPushLog([], log);
    }
  }

  /** Keep the last few deliveries; `updated` entries (same objects, results
   * filled in after sending) replace their pending copies. */
  private appendPushLog(entries: PushLogEntry[], updated: PushLogEntry[] = []): void {
    let log = JSON.parse(this.getMeta("pushLog") ?? "[]") as PushLogEntry[];
    for (const u of updated) {
      const i = log.findIndex((e) => e.at === u.at && e.chatId === u.chatId && e.device === u.device && e.category === u.category);
      if (i >= 0) log[i] = u;
    }
    log = [...log, ...entries].slice(-PUSH_LOG_MAX);
    this.setMeta("pushLog", JSON.stringify(log));
  }

  private handlePresence(ws: WebSocket, state: SocketState, frame: Record<string, unknown>): void {
    if (!state.ready || state.device === "") return;
    const at = typeof frame.at === "number" ? frame.at : Date.now();
    this.presence.set(state.device, at);
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === ws) continue;
      const socketState = socket.deserializeAttachment() as SocketState | null;
      if (!socketState?.ready) continue;
      send(socket, { t: "presence", device: state.device, at });
    }
  }

  private recordPush(device: string, ok: boolean): void {
    const key = device === "" ? "(unknown)" : device;
    const outcomes = JSON.parse(this.getMeta("pushOutcomes") ?? "{}") as Record<string, PushOutcome>;
    const entry = outcomes[key] ?? { ok: 0, rejected: 0, lastOkAt: 0 };
    if (ok) {
      entry.ok += 1;
      entry.lastOkAt = Date.now();
    } else {
      entry.rejected += 1;
    }
    outcomes[key] = entry;
    this.setMeta("pushOutcomes", JSON.stringify(outcomes));
  }

  private markBackupDirty(): void {
    this.setMeta("backupDirty", "1");
    if (this.backupDueAt() === null) {
      this.setMeta("backupDue", String(Date.now() + DAY_MS));
    }
    this.scheduleAlarmSafe();
  }

  private gcReadReceipts(horizon: number): void {
    this.ctx.storage.sql.exec("DELETE FROM read_receipts WHERE message_at < ?", horizon);
  }

  /** Deferred FCM pushes + daily tombstone GC / R2 backup share one durable alarm. */
  async alarm(): Promise<void> {
    try {
      await this.processDueUnreadPushes();
      const backupDue = this.backupDueAt();
      if (this.getMeta("backupDirty") === "1" && backupDue !== null && backupDue <= Date.now()) {
        const horizon = Date.now() - TOMBSTONE_RETAIN_MS;
        const horizonHlc = `${String(horizon).padStart(13, "0")}-`;
        let purgedMaxSeq = 0;
        for (const raw of this.ctx.storage.sql.exec(
          "SELECT seq FROM rows WHERE deleted = 1 AND del_hlc < ?",
          horizonHlc
        )) {
          purgedMaxSeq = Math.max(purgedMaxSeq, raw.seq as number);
        }
        if (purgedMaxSeq > 0) {
          this.ctx.storage.sql.exec("DELETE FROM rows WHERE deleted = 1 AND del_hlc < ?", horizonHlc);
          this.setMeta("gcFloor", String(Math.max(this.gcFloor(), purgedMaxSeq)));
          this.setMeta("lastGcAt", String(Date.now()));
        }
        this.gcReadReceipts(horizon);

        const seq = this.seq();
        if (seq > Number(this.getMeta("backupSeq") ?? "0")) {
          const rows = this.rowsSince(0);
          await this.env.BLOBS.put(
            `backup/registry/${this.ctx.id.toString()}/latest.json`,
            JSON.stringify({ seq, at: Date.now(), rows })
          );
          this.setMeta("backupSeq", String(seq));
        }
        this.setMeta("backupDirty", "0");
        this.deleteMeta("backupDue");
      }
    } finally {
      await this.scheduleAlarm();
    }
  }
}

const send = (ws: WebSocket, frame: Record<string, unknown>): void => {
  try {
    ws.send(JSON.stringify(frame));
  } catch {
    /* socket already gone; hibernation API cleans it up */
  }
};

const json = (value: unknown, status = 200): Response =>
  new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json" }
  });
