# VoxMeet production LiveKit token service

The production token backend is now implemented as a Cloudflare Worker. It replaces the Firebase Cloud Functions approach so VoxMeet can use Cloudflare Workers Free instead of enabling Firebase Blaze billing.

See the complete deployment guide:

- [Cloudflare Worker README](../cloudflare/worker/README.md)

## Current status

- Worker source: `cloudflare/worker/src/index.ts`
- Worker name: `voxmeet-token`
- Firebase project used for authentication: `voxmeet-a4332`
- Production room capacity: 12 participants
- Participant token lifetime: 10 minutes
- Android endpoint: intentionally set to a placeholder until the actual Cloudflare workers.dev URL is known.

## Before production testing

1. Connect the GitHub repository to Cloudflare Workers Builds with root directory `cloudflare/worker` and production branch `production/livekit-token-backend`.
2. Add `LIVEKIT_URL`, `LIVEKIT_API_KEY`, and `LIVEKIT_API_SECRET` in Cloudflare Worker Settings → Variables and Secrets. Keep API credentials as Secrets.
3. Deploy the Worker and copy its exact `workers.dev` URL.
4. Replace the placeholder `TOKEN_ENDPOINT_URL` in `overrides/LiveKitAudioManager.kt` with that URL.
5. Build a new APK and test two different Firebase accounts on two phones.

Do not merge this pull request or distribute its APK as a production build until the endpoint URL is configured, secrets are present, and the two-device test succeeds.

## Security note

The Worker verifies Firebase ID-token signatures and standard claims using Google's published JWKs. Unlike Firebase Admin SDK, this lightweight implementation does not currently perform immediate revoked-token or disabled-account checks. See the Worker README for details and remaining launch hardening.
