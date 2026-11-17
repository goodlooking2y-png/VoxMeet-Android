# VoxMeet LiveKit token backend (Cloudflare Workers)

This replaces the Firebase Cloud Functions token service. The Android app keeps Firebase Authentication for sign-in, but the production LiveKit token endpoint runs on Cloudflare Workers.

## Free plan

Cloudflare Workers Free currently includes up to 100,000 requests per day and a 10 ms CPU-time limit per request. Requests beyond the free quota can fail until the daily reset. Check the current limits before public launch:
https://developers.cloudflare.com/workers/platform/limits/

This service makes a small number of HTTPS calls to Firebase's public signing-key endpoint and the LiveKit server API. Voice/audio traffic itself continues to flow between the VoxMeet app and LiveKit, not through this Worker.

## Deploy from GitHub

1. Create/sign in to a Cloudflare account: https://dash.cloudflare.com/
2. Open **Workers & Pages** and choose **Create application**.
3. Choose **GitHub** and authorize Cloudflare to access the VoxMeet repository.
4. Select `goodlooking2y-png/VoxMeet-Android`.
5. Configure the Worker:
   - Worker name: `voxmeet-token` (must match `wrangler.jsonc`)
   - Production branch: `production/livekit-token-backend`
   - Root directory: `cloudflare/worker`
   - Build command: `npm install --no-audit --no-fund`
   - Deploy command: `npx wrangler deploy`
6. Save and deploy. Cloudflare will assign a URL similar to:
   `https://voxmeet-token.<your-workers-subdomain>.workers.dev`

Cloudflare Workers Builds can deploy directly from GitHub. See:
https://developers.cloudflare.com/workers/ci-cd/builds/

## Add runtime variables and secrets

In Cloudflare Dashboard, open **Workers & Pages → voxmeet-token → Settings → Variables and Secrets**.

The `FIREBASE_PROJECT_ID` text variable is already set to `voxmeet-a4332` in `wrangler.jsonc`. Add only these LiveKit values:

| Name | Type | Value |
|---|---|---|
| `LIVEKIT_URL` | Secret or Text | Your LiveKit Cloud URL beginning with `wss://` |
| `LIVEKIT_API_KEY` | Secret | LiveKit API key |
| `LIVEKIT_API_SECRET` | Secret | LiveKit API secret |

Use the LiveKit Cloud server URL and API credentials from the LiveKit project. Do not use the Development Token Server ID. Keep API credentials as Cloudflare Secrets; never put them in this repository, Android source code, or a chat message. Save and deploy after adding them.

Cloudflare's secret settings encrypt values and hide them after creation:
https://developers.cloudflare.com/workers/configuration/secrets/

## Android endpoint

After the Worker is deployed, copy its exact `workers.dev` URL. The Android source constant `TOKEN_ENDPOINT_URL` must be set to:

`https://voxmeet-token.<your-workers-subdomain>.workers.dev`

Then rebuild the Android APK. Do not install or test a production APK with a placeholder or the old Firebase Functions URL.

## Quick deployment check

After deployment, open `https://voxmeet-token.<your-workers-subdomain>.workers.dev/health`. A successful deployment returns `{"service":"voxmeet-token","status":"ok"}`. This health check does not verify LiveKit secrets; complete the authenticated two-device test as well.

## Security behavior

- Verifies Firebase Authentication ID-token signature using Google's published secure-token JWKs, and checks issuer, audience, expiry, and subject.
- Uses the verified Firebase UID as the LiveKit participant identity; the client cannot select another identity.
- Creates rooms with a server-enforced maximum of 12 participants and refuses existing rooms with a different capacity.
- Issues room-scoped participant JWTs with a 10-minute lifetime and only join/publish/subscribe/data permissions.
- LiveKit API credentials remain on the Worker and are never sent to the Android app.
- Responses are marked no-store.

## Important limitation and launch checks

This lightweight Worker verifies Firebase ID-token signatures and standard claims, but it does not query Firebase Admin to detect revoked ID tokens or disabled accounts. Firebase ID tokens are short-lived (normally one hour); add an authoritative revocation/account-status check before a broad public launch if immediate account disablement is required.

The 12-person limit is enforced by LiveKit room configuration, not only by the app UI. Do a two-device production test after the endpoint URL and secrets are configured. Also test an unauthenticated request (401), an invalid room name (400), and a full room (409).

The Worker is only the token backend. It does not make demo inbox messages, profile edits, wallet coins, gifts, PK, notifications, or privacy toggles into production features. Those require separate persistent backend work.
