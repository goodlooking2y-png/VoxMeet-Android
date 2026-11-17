# VoxMeet production LiveKit token service

This service replaces the LiveKit Development Token Server. The Android app sends a Firebase Authentication ID token to the HTTPS function. The function verifies that token with Firebase Admin, uses the verified Firebase UID as the LiveKit participant identity, and returns a short-lived (10 minute) LiveKit token.

## Security model

- The Android app never contains the LiveKit API key or API secret.
- Only a signed-in Firebase user can request a room token.
- Firebase ID tokens are verified server-side, including revocation checks.
- The participant identity is always taken from the verified Firebase UID; the client cannot choose another identity.
- Tokens are limited to one requested room and have a 10-minute lifetime.
- The token grants room join, audio/data publish and subscribe. It does not grant room administration.
- Responses are marked `Cache-Control: no-store`.

## Required Firebase configuration

The project is `voxmeet-a4332`. Enable Firebase Authentication Email/Password and Phone providers, and confirm both login flows work in the installed app.

Deploying Cloud Functions requires the Firebase project to be on the Blaze (pay-as-you-go) plan with billing enabled. Usage may remain within no-cost quotas, but billing must be enabled and Google Cloud usage should be monitored.

Install Firebase CLI if needed, then from the repository root:

```bash
npm --prefix functions install
npm --prefix functions run build
firebase login
firebase use voxmeet-a4332
```

Create the three Firebase-managed secrets. Run each command and enter the value in the secure prompt; do not put secrets in source files or GitHub commits:

```bash
firebase functions:secrets:set LIVEKIT_URL
firebase functions:secrets:set LIVEKIT_API_KEY
firebase functions:secrets:set LIVEKIT_API_SECRET
```

Use the LiveKit Cloud project's server URL (it must begin with `wss://`), API key and API secret. These are server-side credentials from LiveKit Cloud, not the Development Token Server ID.

Deploy only this function:

```bash
firebase deploy --only functions:getLiveKitToken
```

The configured Android endpoint is:

`https://us-central1-voxmeet-a4332.cloudfunctions.net/getLiveKitToken`

## Validation before enabling the production build

1. Confirm the function deploys successfully and its logs contain no configuration errors.
2. Sign in on two separate test accounts and join the same room from both devices.
3. Confirm an unauthenticated request receives HTTP 401.
4. Confirm invalid room names receive HTTP 400.
5. Confirm the issued token uses the authenticated Firebase UID and expires after 10 minutes.
6. Verify microphone mute/unmute, participant disconnect/reconnect and room exit.
7. Keep the existing tested build/branch available until this endpoint is deployed and the two-device production test passes.

## Follow-up hardening still required

Before public launch, add Firebase App Check enforcement, server-side room membership/visibility checks, server-enforced room capacity and host/moderator permissions, and abuse/rate limiting. This first production-token milestone secures token issuance; it does not by itself complete the entire voice-room moderation or account backend.
