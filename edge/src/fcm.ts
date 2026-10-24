/** Firebase Cloud Messaging HTTP v1: OAuth2 service-account JWT (RS256) and messages:send. */
import { SignJWT, importPKCS8 } from "jose";

export interface FcmConfig {
  serviceAccountJson: string;
  projectId: string;
}

export interface FcmResult {
  status: number;
  /** FCM error.status on failure (NOT_FOUND, INVALID_ARGUMENT, …). */
  reason?: string;
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  private_key_id: string;
  token_uri?: string;
}

const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

/** Access tokens are valid for an hour; reuse one for 50 minutes. Per isolate. */
let cached: { projectId: string; token: string; at: number } | undefined;
const TOKEN_TTL_MS = 50 * 60 * 1000;

const parseServiceAccount = (json: string): ServiceAccount => JSON.parse(json) as ServiceAccount;

export const providerToken = async (cfg: FcmConfig, now = Date.now()): Promise<string> => {
  if (cached && cached.projectId === cfg.projectId && now - cached.at < TOKEN_TTL_MS) return cached.token;
  const sa = parseServiceAccount(cfg.serviceAccountJson);
  const tokenUri = sa.token_uri ?? DEFAULT_TOKEN_URI;
  const iat = Math.floor(now / 1000);
  const key = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({ scope: FCM_SCOPE })
    .setProtectedHeader({ alg: "RS256", kid: sa.private_key_id })
    .setIssuer(sa.client_email)
    .setSubject(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(iat)
    .setExpirationTime(iat + 3600)
    .sign(key);
  const response = await fetch(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${assertion}`
  });
  if (!response.ok) {
    throw new Error(`FCM OAuth failed: ${response.status}`);
  }
  const { access_token } = (await response.json()) as { access_token?: string };
  if (typeof access_token !== "string" || access_token === "") {
    throw new Error("FCM OAuth: missing access_token");
  }
  cached = { projectId: cfg.projectId, token: access_token, at: now };
  return access_token;
};

/** Tokens FCM says are gone for good — drop them. */
export const isDeadToken = (r: FcmResult): boolean =>
  (r.status === 404 && (r.reason === "UNREGISTERED" || r.reason === "NOT_FOUND")) ||
  (r.status === 400 && r.reason === "INVALID_ARGUMENT");

export const sendFcm = async (
  cfg: FcmConfig,
  deviceToken: string,
  payload: Record<string, unknown>,
  collapseId: string
): Promise<FcmResult> => {
  const accessToken = await providerToken(cfg);
  const android = {
    ...((payload.android as Record<string, unknown> | undefined) ?? {}),
    collapse_key: collapseId
  };
  const { android: _drop, ...rest } = payload;
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${cfg.projectId}/messages:send`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      message: {
        token: deviceToken,
        ...rest,
        android
      }
    })
  });
  if (response.ok) return { status: response.status };
  let reason: string | undefined;
  try {
    reason = ((await response.json()) as { error?: { status?: string } }).error?.status;
  } catch {
    reason = undefined;
  }
  return { status: response.status, reason };
};
