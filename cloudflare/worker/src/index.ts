import { createRemoteJWKSet, jwtVerify, SignJWT } from "jose";

interface Env {
  FIREBASE_PROJECT_ID: string;
  LIVEKIT_URL: string;
  LIVEKIT_API_KEY: string;
  LIVEKIT_API_SECRET: string;
}

interface TokenRequestBody {
  room_name?: unknown;
  participant_name?: unknown;
}

interface LiveKitRoom {
  name?: string;
  maxParticipants?: number;
  numParticipants?: number;
}

interface ListRoomsResponse {
  rooms?: LiveKitRoom[];
}

const MAX_ROOM_PARTICIPANTS = 12;
const TOKEN_TTL_SECONDS = 10 * 60;
const FIREBASE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);
const encoder = new TextEncoder();

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
      "Pragma": "no-cache",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function getBearerToken(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = header.match(/^Bearer\s+([^\s]+)$/i);
  return match?.[1] ?? null;
}

async function verifyFirebaseUser(
  idToken: string,
  projectId: string,
): Promise<{ uid: string; name: string }> {
  const { payload } = await jwtVerify(idToken, FIREBASE_JWKS, {
    issuer: `https://securetoken.google.com/${projectId}`,
    audience: projectId,
    algorithms: ["RS256"],
    clockTolerance: 5,
  });

  if (typeof payload.sub !== "string" || payload.sub.length === 0 || payload.sub.length > 128) {
    throw new Error("Firebase token has no valid user identity");
  }

  const name = typeof payload.name === "string" ? payload.name.trim().slice(0, 40) : "";
  return { uid: payload.sub, name };
}

async function signLiveKitJwt(
  apiKey: string,
  apiSecret: string,
  subject: string,
  videoGrant: Record<string, unknown>,
  ttlSeconds: number,
  name?: string,
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  let token = new SignJWT({ video: videoGrant, ...(name ? { name } : {}) })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setIssuer(apiKey)
    .setSubject(subject)
    .setIssuedAt(now)
    .setNotBefore(now - 5)
    .setExpirationTime(now + ttlSeconds);

  return token.sign(encoder.encode(apiSecret));
}

async function liveKitRequest<T>(
  baseUrl: string,
  serviceToken: string,
  method: "ListRooms" | "CreateRoom",
  body: Record<string, unknown>,
): Promise<T> {
  const response = await fetch(
    new URL(`/twirp/livekit.RoomService/${method}`, baseUrl),
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${serviceToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    },
  );

  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(`LiveKit ${method} failed with HTTP ${response.status}`);
  }

  try {
    return JSON.parse(responseText) as T;
  } catch {
    throw new Error(`LiveKit ${method} returned invalid JSON`);
  }
}

async function ensureProductionRoom(
  env: Env,
  roomName: string,
  httpUrl: string,
): Promise<{ full: boolean; invalidCapacity: boolean }> {
  const serviceToken = await signLiveKitJwt(
    env.LIVEKIT_API_KEY,
    env.LIVEKIT_API_SECRET,
    "voxmeet-token-service",
    { roomCreate: true, roomList: true, roomAdmin: true },
    5 * 60,
  );

  const listRooms = async () =>
    liveKitRequest<ListRoomsResponse>(httpUrl, serviceToken, "ListRooms", {
      names: [roomName],
    });

  let room = (await listRooms()).rooms?.find((candidate) => candidate.name === roomName);

  if (!room) {
    try {
      room = await liveKitRequest<LiveKitRoom>(httpUrl, serviceToken, "CreateRoom", {
        name: roomName,
        maxParticipants: MAX_ROOM_PARTICIPANTS,
        emptyTimeout: 300,
      });
    } catch (createError) {
      // A concurrent request may have created the same room after ListRooms.
      room = (await listRooms()).rooms?.find((candidate) => candidate.name === roomName);
      if (!room) throw createError;
    }
  }

  if (Number(room.maxParticipants) !== MAX_ROOM_PARTICIPANTS) {
    return { full: false, invalidCapacity: true };
  }

  return {
    full: Number(room.numParticipants ?? 0) >= MAX_ROOM_PARTICIPANTS,
    invalidCapacity: false,
  };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const requestUrl = new URL(request.url);
    if (request.method === "GET" && requestUrl.pathname === "/health") {
      return json({ service: "voxmeet-token", status: "ok" });
    }

    if (request.method !== "POST") {
      return json({ error: "Method not allowed" }, 405);
    }

    if (!request.headers.get("Content-Type")?.toLowerCase().includes("application/json")) {
      return json({ error: "Content-Type must be application/json" }, 415);
    }

    const contentLength = Number(request.headers.get("Content-Length") ?? "0");
    if (contentLength > 4096) return json({ error: "Request body is too large" }, 413);

    const authorizationToken = getBearerToken(request);
    if (!authorizationToken) {
      return json({ error: "Authentication required" }, 401);
    }

    const projectId = env.FIREBASE_PROJECT_ID?.trim();
    if (!projectId) {
      return json({ error: "Authentication service is not configured" }, 500);
    }

    let user: { uid: string; name: string };
    try {
      user = await verifyFirebaseUser(authorizationToken, projectId);
    } catch {
      return json({ error: "Invalid or expired authentication token" }, 401);
    }

    let body: TokenRequestBody;
    try {
      const rawBody = await request.text();
      if (rawBody.length > 4096) return json({ error: "Request body is too large" }, 413);
      body = JSON.parse(rawBody) as TokenRequestBody;
    } catch {
      return json({ error: "A valid JSON request body is required" }, 400);
    }

    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return json({ error: "A JSON object request body is required" }, 400);
    }

    const roomName = typeof body.room_name === "string" ? body.room_name.trim() : "";
    if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(roomName)) {
      return json({ error: "Invalid room name" }, 400);
    }

    const requestedName =
      typeof body.participant_name === "string" ? body.participant_name.trim() : "";
    if (requestedName.length > 40) {
      return json({ error: "Participant name is too long" }, 400);
    }

    const serverUrl = env.LIVEKIT_URL?.trim() ?? "";
    const apiKey = env.LIVEKIT_API_KEY?.trim() ?? "";
    const apiSecret = env.LIVEKIT_API_SECRET ?? "";

    let livekitBaseUrl: string;
    try {
      const parsedUrl = new URL(serverUrl);
      if (
        parsedUrl.protocol !== "wss:" ||
        !parsedUrl.hostname ||
        parsedUrl.username ||
        parsedUrl.password ||
        parsedUrl.search ||
        parsedUrl.hash
      ) {
        throw new Error("Invalid LiveKit URL");
      }
      parsedUrl.protocol = "https:";
      livekitBaseUrl = parsedUrl.origin;
    } catch {
      return json({ error: "Voice service is not configured correctly" }, 500);
    }

    if (!apiKey || !apiSecret) {
      return json({ error: "Voice service is not configured" }, 500);
    }

    try {
      const roomStatus = await ensureProductionRoom(env, roomName, livekitBaseUrl);
      if (roomStatus.invalidCapacity) {
        return json({ error: "This room is not configured for production capacity" }, 409);
      }
      if (roomStatus.full) {
        return json({ error: "This voice room is full" }, 409);
      }

      // The participant identity is always the verified Firebase UID.
      const participantName = requestedName || user.name || "VoxMeet User";
      const participantToken = await signLiveKitJwt(
        apiKey,
        apiSecret,
        user.uid,
        {
          roomJoin: true,
          room: roomName,
          canPublish: true,
          canSubscribe: true,
          canPublishData: true,
        },
        TOKEN_TTL_SECONDS,
        participantName,
      );

      return json(
        {
          server_url: serverUrl,
          participant_token: participantToken,
        },
        201,
      );
    } catch (error) {
      // Never log credentials, tokens, or raw provider responses.
      console.error("VoxMeet LiveKit token issuance failed", error instanceof Error ? error.message : "unknown error");
      return json({ error: "Could not issue a voice room token" }, 500);
    }
  },
};
