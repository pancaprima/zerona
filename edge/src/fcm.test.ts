import { describe, expect, it, vi } from "vitest";
import { exportPKCS8, generateKeyPair, jwtVerify } from "jose";
import { isDeadToken, providerToken, sendFcm } from "./fcm";

const serviceAccountCfg = async (projectId: string) => {
  const { privateKey, publicKey } = await generateKeyPair("RS256", { extractable: true });
  const sa = {
    client_email: "fcm@test-project.iam.gserviceaccount.com",
    private_key: await exportPKCS8(privateKey),
    private_key_id: "abc123key",
    project_id: projectId,
    token_uri: "https://oauth2.googleapis.com/token"
  };
  return {
    cfg: { serviceAccountJson: JSON.stringify(sa), projectId },
    publicKey
  };
};

describe("FCM provider token", () => {
  it("is an RS256 JWT with the key id and service account, reused within its window", async () => {
    const { cfg, publicKey } = await serviceAccountCfg("proj-a");
    const now = Date.now();
    let assertion = "";
    const fetchMock = vi.fn(async (url: string | URL, init?: RequestInit) => {
      expect(String(url)).toBe("https://oauth2.googleapis.com/token");
      const body = String(init?.body);
      expect(body).toContain("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer");
      assertion = decodeURIComponent(body.split("assertion=")[1] ?? "");
      return new Response(JSON.stringify({ access_token: "ya29.cached" }), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const token = await providerToken(cfg, now);
    expect(token).toBe("ya29.cached");
    const { payload, protectedHeader } = await jwtVerify(assertion, publicKey);
    expect(protectedHeader).toMatchObject({ alg: "RS256", kid: "abc123key" });
    expect(payload.iss).toBe("fcm@test-project.iam.gserviceaccount.com");
    expect(payload.sub).toBe("fcm@test-project.iam.gserviceaccount.com");
    expect(payload.aud).toBe("https://oauth2.googleapis.com/token");
    expect(payload.scope).toBe("https://www.googleapis.com/auth/firebase.messaging");

    expect(await providerToken(cfg, now + 10 * 60 * 1000)).toBe("ya29.cached");
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const { cfg: cfgB } = await serviceAccountCfg("proj-b");
    vi.mocked(fetchMock).mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "ya29.other" }), { status: 200 }));
    await providerToken(cfgB, now + 55 * 60 * 1000);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    vi.unstubAllGlobals();
  });

  it("drops tokens FCM has retired", () => {
    expect(isDeadToken({ status: 404, reason: "UNREGISTERED" })).toBe(true);
    expect(isDeadToken({ status: 404, reason: "NOT_FOUND" })).toBe(true);
    expect(isDeadToken({ status: 400, reason: "INVALID_ARGUMENT" })).toBe(true);
    expect(isDeadToken({ status: 429, reason: "RESOURCE_EXHAUSTED" })).toBe(false);
  });
});

describe("sendFcm", () => {
  it("POSTs to the project send URL with bearer auth and collapse_key", async () => {
    const { cfg } = await serviceAccountCfg("my-firebase-project");
    const fetchMock = vi.fn(async (url: string | URL, init?: RequestInit) => {
      if (String(url).includes("oauth2.googleapis.com")) {
        return new Response(JSON.stringify({ access_token: "oauth-token" }), { status: 200 });
      }
      expect(String(url)).toBe("https://fcm.googleapis.com/v1/projects/my-firebase-project/messages:send");
      expect(init?.headers).toMatchObject({
        authorization: "Bearer oauth-token",
        "content-type": "application/json"
      });
      const body = JSON.parse(String(init?.body)) as {
        message: { token: string; notification: unknown; android: { collapse_key: string; notification: unknown } };
      };
      expect(body.message.token).toBe("device-tok-12345678901234567890");
      expect(body.message.android.collapse_key).toBe("chat-1");
      expect(body.message.android.notification).toEqual({ channel_id: "sessions" });
      return new Response(JSON.stringify({ name: "projects/x/messages/1" }), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const r = await sendFcm(
      cfg,
      "device-tok-12345678901234567890",
      {
        notification: { title: "Hi", body: "There" },
        data: { chatId: "chat-1", category: "done" },
        android: { notification: { channel_id: "sessions" } }
      },
      "chat-1"
    );
    expect(r).toEqual({ status: 200 });
    vi.unstubAllGlobals();
  });
});
