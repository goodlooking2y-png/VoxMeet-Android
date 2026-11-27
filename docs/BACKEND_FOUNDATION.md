# VoxMeet backend foundation

## Scope of this first backend step

This adds the first trusted server-side layer for VoxMeet voice rooms:

- Firebase-authenticated callable functions.
- LiveKit API credentials stay in Firebase Secret Manager, never in the Android APK.
- Server-created LiveKit rooms have `maxParticipants: 12` (host included).
- Short-lived LiveKit access tokens use the Firebase UID as the participant identity.
- Only the host can close a room.
- Firestore client rules deny direct access to room and wallet records.

This is backend source code only. It has **not** been deployed, and the Android app has **not** yet been switched from its development token server to these callable functions. Do not treat room creation or the 12-person limit as active in the app until deployment and Android integration are complete.

## Firebase project setup

Project: `voxmeet-a4332`

Cloud Functions deployment requires the Firebase project to be on the Blaze (pay-as-you-go) plan. This repository change does not enable billing or deploy anything. Review Firebase pricing and set budget alerts before enabling paid services.

After confirming billing and enabling Cloud Functions, configure:

```sh
firebase use voxmeet-a4332
firebase functions:secrets:set LIVEKIT_API_KEY
firebase functions:secrets:set LIVEKIT_API_SECRET
firebase functions:config:get
firebase deploy --only functions,firestore:rules
```

When prompted for `LIVEKIT_URL`, enter the LiveKit Cloud WebSocket URL (the `wss://...` URL). Do not put the API secret in GitHub files, chat messages, Android resources, or the APK.

Before deployment, verify that the LiveKit Cloud project has the correct API key/secret pair and that the Firebase project has Cloud Functions enabled.

## Callable functions

### `createVoiceRoom({ roomName, displayName })`

Requires Firebase Authentication. Creates a LiveKit room with a server-enforced maximum of 12 participants and returns a short-lived host token.

### `joinVoiceRoom({ roomName, displayName })`

Requires Firebase Authentication and an active VoxMeet room record. Returns a short-lived token with host or guest identity. LiveKit enforces the room's participant limit.

### `closeVoiceRoom({ roomName })`

Requires Firebase Authentication. Only the recorded host can close the room.

## Next integration work

1. Add the Firebase Functions Android SDK.
2. Replace `TokenSource.fromDevelopmentTokenServer(...)` with callable function requests.
3. Replace the local room-creation action with `createVoiceRoom`.
4. Use `joinVoiceRoom` for every room entry and `closeVoiceRoom` when the host ends the room.
5. Test simultaneous joins from multiple devices and verify the 12th/13th participant behavior.
6. Add App Check, rate limits, room discovery rules and moderation before public release.
7. Build the wallet ledger and gift transaction functions only after authenticated user and room identity are wired through the app.

## Important security notes

- Firebase Admin SDK functions bypass Firestore Security Rules; callable handlers must validate every input and authorization condition.
- The Android client must never be allowed to write wallet balances or gift settlement records directly.
- The current Android UI uses a locally derived room name. It must be migrated to a server-created room identifier before production.
- This foundation does not implement wallet, gifts, PK scoring, payments, or production moderation.
