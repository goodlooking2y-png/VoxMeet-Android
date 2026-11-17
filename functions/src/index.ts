import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { defineSecret } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";
import { AccessToken, RoomServiceClient } from "livekit-server-sdk";

initializeApp();

const livekitUrl = defineSecret("LIVEKIT_URL");
const livekitApiKey = defineSecret("LIVEKIT_API_KEY");
const livekitApiSecret = defineSecret("LIVEKIT_API_SECRET");
const MAX_ROOM_PARTICIPANTS = 12;

type TokenRequestBody = {
  room_name?: unknown;
  participant_name?: unknown;
};

export const getLiveKitToken = onRequest(
  {
    region: "us-central1",
    cors: false,
    invoker: "public",
    timeoutSeconds: 15,
    memory: "256MiB",
    maxInstances: 10,
    secrets: [livekitUrl, livekitApiKey, livekitApiSecret],
  },
  async (req, res) => {
    res.set("Cache-Control", "no-store");

    if (req.method !== "POST") {
      res.set("Allow", "POST").status(405).json({ error: "Method not allowed" });
      return;
    }

    const authorization = req.get("authorization") ?? "";
    const bearerMatch = authorization.match(/^Bearer\s+(.+)$/i);
    if (!bearerMatch) {
      res.status(401).json({ error: "Authentication required" });
      return;
    }

    let user;
    try {
      // Check revocation as well as signature, issuer, audience and expiry.
      user = await getAuth().verifyIdToken(bearerMatch[1], true);
    } catch {
      res.status(401).json({ error: "Invalid or expired authentication token" });
      return;
    }

    const body = req.body as TokenRequestBody | undefined;
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      res.status(400).json({ error: "A JSON request body is required" });
      return;
    }

    const roomName =
      typeof body.room_name === "string" ? body.room_name.trim() : "";
    if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(roomName)) {
      res.status(400).json({ error: "Invalid room name" });
      return;
    }

    const requestedName =
      typeof body.participant_name === "string"
        ? body.participant_name.trim()
        : "";
    if (requestedName.length > 40) {
      res.status(400).json({ error: "Participant name is too long" });
      return;
    }

    const serverUrl = livekitUrl.value().trim();
    const apiKey = livekitApiKey.value().trim();
    const apiSecret = livekitApiSecret.value();

    if (!serverUrl.startsWith("wss://") || !apiKey || !apiSecret) {
      console.error("LiveKit token endpoint is missing valid server configuration");
      res.status(500).json({ error: "Voice service is not configured" });
      return;
    }

    try {
      // Create every production room with a server-enforced 12-participant cap.
      // New production room names use the voxmeet-prod-room- prefix so an older
      // uncapped development room cannot silently bypass this setting.
      const roomService = new RoomServiceClient(
        serverUrl.replace(/^wss:/, "https:"),
        apiKey,
        apiSecret,
      );
      let room = (await roomService.listRooms([roomName]))[0];
      if (!room) {
        try {
          room = await roomService.createRoom({
            name: roomName,
            maxParticipants: MAX_ROOM_PARTICIPANTS,
            emptyTimeout: 300,
          });
        } catch (createError) {
          // Another token request may have created this room at the same time.
          const racedRoom = (await roomService.listRooms([roomName]))[0];
          if (!racedRoom) throw createError;
          room = racedRoom;
        }
      }
      if (room.maxParticipants !== MAX_ROOM_PARTICIPANTS) {
        res.status(409).json({ error: "This room is not configured for production capacity" });
        return;
      }
      if (room.numParticipants >= MAX_ROOM_PARTICIPANTS) {
        res.status(409).json({ error: "This voice room is full" });
        return;
      }

      // Identity always comes from the verified Firebase user. A client cannot
      // mint a token for another identity by changing participant_identity.
      const participantName =
        requestedName || user.name?.trim().slice(0, 40) || "VoxMeet User";

      const accessToken = new AccessToken(apiKey, apiSecret, {
        identity: user.uid,
        name: participantName,
        ttl: "10m",
      });

      accessToken.addGrant({
        roomJoin: true,
        room: roomName,
        canPublish: true,
        canSubscribe: true,
        canPublishData: true,
      });

      const participantToken = await accessToken.toJwt();
      res.status(201).json({
        server_url: serverUrl,
        participant_token: participantToken,
      });
    } catch (error) {
      console.error("LiveKit token generation failed", error);
      res.status(500).json({ error: "Could not issue a voice room token" });
    }
  },
);
