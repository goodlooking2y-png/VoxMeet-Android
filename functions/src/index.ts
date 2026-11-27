import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { AccessToken, RoomServiceClient } from "livekit-server-sdk";

initializeApp();

const db = getFirestore();
const REGION = "asia-southeast1";
const MAX_ROOM_PARTICIPANTS = 12;

const livekitUrl = defineString("LIVEKIT_URL");
const livekitApiKey = defineSecret("LIVEKIT_API_KEY");
const livekitApiSecret = defineSecret("LIVEKIT_API_SECRET");

function requireUser(uid: string | undefined): string {
  if (!uid) {
    throw new HttpsError("unauthenticated", "VoxMeet account sign-in is required.");
  }
  return uid;
}

function getRoomName(value: unknown): string {
  if (
    typeof value !== "string" ||
    !/^[a-zA-Z0-9_-]{3,64}$/.test(value)
  ) {
    throw new HttpsError("invalid-argument", "A valid room name is required.");
  }
  return value;
}

function getLiveKitHost(): string {
  const configured = livekitUrl.value().trim();
  if (!configured) {
    throw new HttpsError("failed-precondition", "LiveKit URL is not configured.");
  }
  // LiveKit's Android client uses WSS; the server SDK uses HTTPS for its API.
  return configured.replace(/^wss:\/\//i, "https://").replace(/^ws:\/\//i, "http://");
}

function roomService(): RoomServiceClient {
  return new RoomServiceClient(
    getLiveKitHost(),
    livekitApiKey.value(),
    livekitApiSecret.value()
  );
}

async function issueRoomToken(
  roomName: string,
  uid: string,
  role: "host" | "guest",
  displayName: string
): Promise<string> {
  const token = new AccessToken(livekitApiKey.value(), livekitApiSecret.value(), {
    identity: uid,
    name: displayName.slice(0, 64),
    ttl: "10m"
  });

  token.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
    roomAdmin: role === "host"
  });

  return token.toJwt();
}

/**
 * Creates an audio room with the LiveKit server-enforced 12-participant cap.
 * The authenticated Firebase UID is the room host and LiveKit identity.
 */
export const createVoiceRoom = onCall(
  {
    region: REGION,
    secrets: [livekitApiKey, livekitApiSecret],
    timeoutSeconds: 30
  },
  async (request) => {
    const uid = requireUser(request.auth?.uid);
    const roomName = getRoomName(request.data?.roomName);
    const displayName =
      typeof request.data?.displayName === "string"
        ? request.data.displayName.trim().slice(0, 64)
        : "VoxMeet host";

    const roomRef = db.collection("voiceRooms").doc(roomName);
    await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(roomRef);
      if (existing.exists) {
        throw new HttpsError("already-exists", "This room name is already in use.");
      }
      transaction.create(roomRef, {
        roomName,
        hostUid: uid,
        status: "creating",
        maxParticipants: MAX_ROOM_PARTICIPANTS,
        createdAt: FieldValue.serverTimestamp()
      });
    });

    try {
      await roomService().createRoom({
        name: roomName,
        maxParticipants: MAX_ROOM_PARTICIPANTS,
        emptyTimeout: 10 * 60
      });

      await roomRef.update({
        status: "active",
        activatedAt: FieldValue.serverTimestamp()
      });

      const token = await issueRoomToken(roomName, uid, "host", displayName || "VoxMeet host");
      return {
        serverUrl: livekitUrl.value(),
        roomName,
        token,
        identity: uid,
        role: "host",
        maxParticipants: MAX_ROOM_PARTICIPANTS
      };
    } catch (error) {
      await roomRef.update({
        status: "failed",
        failedAt: FieldValue.serverTimestamp()
      }).catch(() => undefined);
      console.error("createVoiceRoom failed", error);
      throw new HttpsError("internal", "The voice room could not be created.");
    }
  }
);

/**
 * Issues a short-lived join token only for an active VoxMeet room.
 * LiveKit itself enforces maxParticipants configured when the room is created.
 */
export const joinVoiceRoom = onCall(
  {
    region: REGION,
    secrets: [livekitApiKey, livekitApiSecret],
    timeoutSeconds: 20
  },
  async (request) => {
    const uid = requireUser(request.auth?.uid);
    const roomName = getRoomName(request.data?.roomName);
    const displayName =
      typeof request.data?.displayName === "string"
        ? request.data.displayName.trim().slice(0, 64)
        : "VoxMeet guest";

    const roomSnapshot = await db.collection("voiceRooms").doc(roomName).get();
    if (!roomSnapshot.exists || roomSnapshot.get("status") !== "active") {
      throw new HttpsError("not-found", "This VoxMeet room is not active.");
    }

    const roomData = roomSnapshot.data();
    if (!roomData) {
      throw new HttpsError("not-found", "This VoxMeet room is not available.");
    }

    const role: "host" | "guest" = roomData.hostUid === uid ? "host" : "guest";

    try {
      // Early feedback only; LiveKit's maxParticipants setting is the final
      // server-side guard against simultaneous joins.
      const participants = await roomService().listParticipants(roomName);
      if (participants.length >= MAX_ROOM_PARTICIPANTS) {
        throw new HttpsError("resource-exhausted", "This room already has 12 members.");
      }
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      console.error("Could not inspect LiveKit participants", error);
      throw new HttpsError("unavailable", "Room capacity could not be checked. Try again.");
    }

    const token = await issueRoomToken(
      roomName,
      uid,
      role,
      displayName || (role === "host" ? "VoxMeet host" : "VoxMeet guest")
    );

    return {
      serverUrl: livekitUrl.value(),
      roomName,
      token,
      identity: uid,
      role,
      maxParticipants: MAX_ROOM_PARTICIPANTS
    };
  }
);

/** Allows only the authenticated room host to close the LiveKit room. */
export const closeVoiceRoom = onCall(
  {
    region: REGION,
    secrets: [livekitApiKey, livekitApiSecret],
    timeoutSeconds: 20
  },
  async (request) => {
    const uid = requireUser(request.auth?.uid);
    const roomName = getRoomName(request.data?.roomName);
    const roomRef = db.collection("voiceRooms").doc(roomName);
    const roomSnapshot = await roomRef.get();

    if (!roomSnapshot.exists || roomSnapshot.get("hostUid") !== uid) {
      throw new HttpsError("permission-denied", "Only the room host can close this room.");
    }

    try {
      await roomService().deleteRoom(roomName);
      await roomRef.update({
        status: "closed",
        closedAt: FieldValue.serverTimestamp()
      });
      return { roomName, closed: true };
    } catch (error) {
      console.error("closeVoiceRoom failed", error);
      throw new HttpsError("internal", "The voice room could not be closed.");
    }
  }
);
