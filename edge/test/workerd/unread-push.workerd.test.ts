import { env, runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { AUTH_USER_HEADER } from "../../src/env";
import type { Op } from "../../src/registry-core";
import { TRIGGER_MARKER_WINDOW_MS } from "../../src/registry-room";

const H = { [AUTH_USER_HEADER]: "user" };
let tick = 1;
const hlc = () => `${String(tick++).padStart(13, "0")}-000000-mac`;
const session = (status: string, turn: string | null, updatedAt = Date.now()): Op => ({
  kind: "sessions",
  id: "c1",
  op: "upsert",
  hlc: hlc(),
  set: { chatId: "c1", deviceId: "mac", status, lastCompletedTurn: turn, updatedAt }
});

function room() {
  return env.REGISTRY_ROOMS.get(env.REGISTRY_ROOMS.idFromName(crypto.randomUUID()));
}
async function push(stub: DurableObjectStub, ops: Op[]) {
  const r = await stub.fetch("https://registry/push?device=mac", { method: "POST", headers: H, body: JSON.stringify({ batch: crypto.randomUUID(), ops }) });
  expect(r.status).toBe(200);
}
async function stats(stub: DurableObjectStub) {
  const r = await stub.fetch("https://registry/stats", { headers: H });
  return r.json<{
    pushLog: Array<{ chatId: string; category: string; device: string; result: string }>;
    lastBackupSeq: number;
    lastGcAt: number;
  }>();
}
async function registerApns(stub: DurableObjectStub, device: string) {
  return stub.fetch(`https://registry/push-target?device=${device}`, {
    method: "POST",
    headers: H,
    body: JSON.stringify({ token: "ab".repeat(32), environment: "production" })
  });
}
async function registerFcm(stub: DurableObjectStub, device: string, prefs?: Record<string, boolean>) {
  return stub.fetch(`https://registry/push-target?device=${device}`, {
    method: "POST",
    headers: H,
    body: JSON.stringify({ kind: "fcm", token: "x".repeat(40), prefs })
  });
}
async function seen(stub: DurableObjectStub, device: string, chatId: string, messageId: string, messageAt: number) {
  return stub.fetch(`https://registry/seen?device=${device}`, {
    method: "POST",
    headers: H,
    body: JSON.stringify({ chatId, messageId, messageAt })
  });
}
async function seedChat(stub: DurableObjectStub, lastMessageAt?: number, lastMessageId?: string) {
  await push(stub, [
    {
      kind: "chats",
      id: "c1",
      op: "upsert",
      hlc: hlc(),
      set: {
        title: "Fix login",
        ...(lastMessageAt !== undefined ? { lastMessageAt } : {}),
        ...(lastMessageId !== undefined ? { lastMessageId } : {})
      }
    },
    session("working", null)
  ]);
}
async function markChatMessage(stub: DurableObjectStub, lastMessageAt: number, lastMessageId: string) {
  await push(stub, [
    {
      kind: "chats",
      id: "c1",
      op: "upsert",
      hlc: hlc(),
      set: { title: "Fix login", lastMessageAt, lastMessageId }
    }
  ]);
}
async function fastForwardUnread(stub: DurableObjectStub) {
  await runInDurableObject(stub, async (_i, state) => {
    state.storage.sql.exec("UPDATE unread_triggers SET created_at = 0");
    await state.storage.setAlarm(Date.now());
  });
}
async function triggerDone(
  stub: DurableObjectStub,
  updatedAt = Date.now(),
  messageMarker?: { at: number; id: string }
) {
  const ops: Op[] = [session("idle", "turn-1", updatedAt)];
  if (messageMarker !== undefined) {
    ops.unshift({
      kind: "chats",
      id: "c1",
      op: "upsert",
      hlc: hlc(),
      set: { title: "Fix login", lastMessageAt: messageMarker.at, lastMessageId: messageMarker.id }
    });
  }
  await push(stub, ops);
}
async function triggerMsgAt(stub: DurableObjectStub) {
  return runInDurableObject(stub, (_i, state) => {
    return [...state.storage.sql.exec("SELECT msg_at FROM unread_triggers WHERE chat_id = 'c1'")][0]?.msg_at as number;
  });
}

describe("deferred FCM unread push", () => {
  it("does not push when desktop read receipt arrives before the delay", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const msgAt = Date.now() - 500;
    await seedChat(stub, msgAt, "m1");
    await triggerDone(stub, Date.now(), { at: msgAt, id: "m1" });
    await seen(stub, "desktop-a", "c1", "m1", msgAt);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    const s = await stats(stub);
    expect(s.pushLog).toEqual([]);
  });

  it("does not push when phone read receipt arrives before the delay", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const msgAt = Date.now() - 500;
    await seedChat(stub, msgAt, "m1");
    await triggerDone(stub, Date.now(), { at: msgAt, id: "m1" });
    await seen(stub, "phone-b", "c1", "m1", msgAt);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toEqual([]);
  });

  it("treats receipt at msg_at as read (triggering message on screen)", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const msgAt = Date.now() - 5_000;
    await seedChat(stub, msgAt, "closing");
    await triggerDone(stub, Date.now(), { at: msgAt, id: "closing" });
    expect(await triggerMsgAt(stub)).toBe(msgAt);
    await seen(stub, "desktop-a", "c1", "closing", msgAt);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toEqual([]);
  });

  it("keeps trigger unread when receipt is older than msg_at", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const msgAt = 1_700_000_100_000;
    await seedChat(stub, msgAt, "new-msg");
    await triggerDone(stub, Date.now(), { at: msgAt, id: "new-msg" });
    await seen(stub, "desktop-a", "c1", "old", msgAt - 1);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toHaveLength(1);
  });

  it("uses session updatedAt as msg_at when chat has no lastMessageAt", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const updatedAt = Date.now() - 2_000;
    await push(stub, [
      {
        kind: "chats",
        id: "c1",
        op: "upsert",
        hlc: hlc(),
        set: { title: "Fix login", lastMessageAt: updatedAt, lastMessageId: "m" }
      },
      session("working", null, updatedAt)
    ]);
    await triggerDone(stub, updatedAt, { at: updatedAt, id: "m" });
    expect(await triggerMsgAt(stub)).toBe(updatedAt);
    await seen(stub, "desktop-a", "c1", "m", updatedAt);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toEqual([]);
  });

  it("raises msg_at when lastMessageAt advances within the marker window", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const first = 1_700_000_300_000;
    await seedChat(stub, first, "partial");
    await triggerDone(stub);
    const later = first + 5_000;
    await markChatMessage(stub, later, "completion-msg");
    expect(await triggerMsgAt(stub)).toBe(later);
    await seen(stub, "desktop-a", "c1", "partial", first);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toHaveLength(1);
  });

  it("still sends push when an older message was already seen at the prior timestamp", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    const first = 1_700_000_500_000;
    await seedChat(stub, first, "prev-msg");
    await seen(stub, "desktop-a", "c1", "prev-msg", first);
    await triggerDone(stub);
    await markChatMessage(stub, first + 5_000, "completion-msg");
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toHaveLength(1);
  });

  it("does not raise msg_at when lastMessageAt advances after the marker window", async () => {
    const stub = room();
    const first = 1_700_000_400_000;
    await seedChat(stub, first);
    await triggerDone(stub);
    await runInDurableObject(stub, (_i, state) => {
      state.storage.sql.exec("UPDATE unread_triggers SET created_at = ?", Date.now() - TRIGGER_MARKER_WINDOW_MS - 1);
    });
    const later = first + 50_000;
    await push(stub, [
      { kind: "chats", id: "c1", op: "upsert", hlc: hlc(), set: { title: "Fix login", lastMessageAt: later } }
    ]);
    expect(await triggerMsgAt(stub)).toBe(first);
  });

  it("fires alarm twice in a row for one pending push and sends exactly one push", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await triggerDone(stub);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    await runDurableObjectAlarm(stub);
    const s = await stats(stub);
    expect(s.pushLog.filter((e) => e.chatId === "c1" && e.device === "android-1")).toHaveLength(1);
    expect(s.pushLog).toHaveLength(1);
  });

  it("updates the pending push when a newer trigger arrives before due and still sends one push", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await triggerDone(stub);
    await push(stub, [session("awaitingInput", "turn-1")]);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    const s = await stats(stub);
    expect(s.pushLog).toHaveLength(1);
    expect(s.pushLog[0]).toMatchObject({ chatId: "c1", category: "input", device: "android-1" });
  });

  it("sends exactly one deferred push per chat when still unread on both devices", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await triggerDone(stub);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    const s = await stats(stub);
    expect(s.pushLog).toHaveLength(1);
    expect(s.pushLog[0]).toMatchObject({ chatId: "c1", category: "done", device: "android-1" });
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toHaveLength(1);
  });

  it("coalesces several triggers into one summary push", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await push(stub, [session("awaitingInput", "turn-1")]);
    await push(stub, [session("idle", "turn-2")]);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    const s = await stats(stub);
    expect(s.pushLog).toHaveLength(1);
    expect(s.pushLog[0].category).toBe("input");
    expect(s.pushLog[0].result).toContain("not configured");
  });

  it("runs the daily backup job when due without dropping deferred triggers", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await triggerDone(stub);
    await runInDurableObject(stub, async (_i, state) => {
      state.storage.sql.exec(
        "INSERT INTO meta (key, value) VALUES ('backupDue', ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        String(Date.now() - 1000)
      );
      await state.storage.setAlarm(Date.now());
    });
    await runDurableObjectAlarm(stub);
    const afterBackup = await runInDurableObject(stub, async (_i, state) => {
      const triggers = [...state.storage.sql.exec("SELECT COUNT(*) AS n FROM unread_triggers")][0]?.n;
      const backupSeq = [...state.storage.sql.exec("SELECT value FROM meta WHERE key = 'backupSeq'")][0]?.value;
      const backupDue = [...state.storage.sql.exec("SELECT value FROM meta WHERE key = 'backupDue'")];
      const alarm = await state.storage.getAlarm();
      return { triggers, backupSeq, backupDue, alarm };
    });
    expect(afterBackup.triggers).toBe(1);
    expect(afterBackup.backupSeq).toBeTruthy();
    expect(afterBackup.backupDue).toHaveLength(0);
    expect(afterBackup.alarm).not.toBeNull();
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toHaveLength(1);
  });

  it("does not re-fire the daily backup alarm on every registry write after backup completes", async () => {
    const stub = room();
    await seedChat(stub);
    await runInDurableObject(stub, async (_i, state) => {
      state.storage.sql.exec(
        "INSERT INTO meta (key, value) VALUES ('backupDue', ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        String(Date.now() - 1000)
      );
      state.storage.sql.exec(
        "INSERT INTO meta (key, value) VALUES ('backupDirty', '1') ON CONFLICT(key) DO UPDATE SET value = '1'"
      );
      await state.storage.setAlarm(Date.now());
    });
    await runDurableObjectAlarm(stub);
    const now = Date.now();
    for (let i = 0; i < 20; i++) {
      await push(stub, [session("working", null, now + i)]);
    }
    const end = await runInDurableObject(stub, async (_i, state) => {
      const alarm = await state.storage.getAlarm();
      const backupDue = [...state.storage.sql.exec("SELECT value FROM meta WHERE key = 'backupDue'")][0]?.value as string | undefined;
      return { alarm, backupDue };
    });
    expect(end.alarm).not.toBeNull();
    expect(end.alarm!).toBeGreaterThan(now);
    expect(end.alarm!).toBeGreaterThan(now + 23 * 60 * 60 * 1000);
    expect(Number(end.backupDue)).toBeGreaterThan(now + 23 * 60 * 60 * 1000);
  });

  it("fires deferred push when due without running a not-yet-due daily backup", async () => {
    const stub = room();
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await triggerDone(stub);
    const backupSeqBefore = (await stats(stub)).lastBackupSeq;
    await fastForwardUnread(stub);
    const futureBackup = Date.now() + DAY_MS;
    await runInDurableObject(stub, async (_i, state) => {
      state.storage.sql.exec(
        "INSERT INTO meta (key, value) VALUES ('backupDue', ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        String(futureBackup)
      );
      state.storage.sql.exec(
        "INSERT INTO meta (key, value) VALUES ('backupDirty', '1') ON CONFLICT(key) DO UPDATE SET value = '1'"
      );
      await state.storage.setAlarm(Date.now());
    });
    await runDurableObjectAlarm(stub);
    expect((await stats(stub)).pushLog).toHaveLength(1);
    const after = await runInDurableObject(stub, async (_i, state) => {
      const backupSeq = [...state.storage.sql.exec("SELECT value FROM meta WHERE key = 'backupSeq'")][0]?.value;
      const backupDirty = [...state.storage.sql.exec("SELECT value FROM meta WHERE key = 'backupDirty'")][0]?.value;
      return { backupSeq, backupDirty };
    });
    expect(Number(after.backupSeq ?? 0)).toBe(backupSeqBefore);
    expect(after.backupDirty).toBe("1");
  });

  it("keeps APNs instant while FCM waits for the alarm", async () => {
    const stub = room();
    await registerApns(stub, "ios-1");
    await registerFcm(stub, "android-1");
    await seedChat(stub);
    await triggerDone(stub);
    const immediate = await stats(stub);
    expect(immediate.pushLog.map((e) => [e.device, e.category])).toEqual([["ios-1", "done"]]);
    await fastForwardUnread(stub);
    await runDurableObjectAlarm(stub);
    const after = await stats(stub);
    expect(after.pushLog).toHaveLength(2);
    expect(after.pushLog.some((e) => e.device === "android-1")).toBe(true);
  });

  it("rejects bad seen payloads and keeps read receipts monotonic", async () => {
    const stub = room();
    expect((await seen(stub, "", "c1", "m", 1)).status).toBe(400);
    expect((await seen(stub, "d1", "c1", "", 1)).status).toBe(400);
    expect((await seen(stub, "d1", "c1", "m", Number.NaN)).status).toBe(400);
    expect((await seen(stub, "d1", "c1", "m", Date.now() + 2 * 60 * 60 * 1000)).status).toBe(400);
    const t0 = Date.now();
    expect((await seen(stub, "d1", "c1", "new", t0)).status).toBe(200);
    expect((await seen(stub, "d1", "c1", "old", t0 - 1000)).status).toBe(200);
    const stored = await runInDurableObject(stub, (_i, state) => {
      return [...state.storage.sql.exec("SELECT message_id, message_at FROM read_receipts WHERE chat_id = 'c1'")][0];
    });
    expect(stored?.message_id).toBe("new");
    expect(stored?.message_at).toBe(t0);
  });
});

const DAY_MS = 24 * 60 * 60 * 1000;
