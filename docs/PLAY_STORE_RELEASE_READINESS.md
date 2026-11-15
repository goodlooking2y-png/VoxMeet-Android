# VoxMeet Google Play release readiness

This document tracks the difference between the current working prototype and a production release. Do not describe demo actions as completed production features.

## Verified in the current Android build

- Android application ID: `com.rakib.voxmeet`
- Kotlin + Jetpack Compose UI
- Home, Discover, Messages, Profile and voice-room screens
- LiveKit Android SDK dependency and room connection flow
- Room participant list and room chat data events
- Wallet, gift and PK screens are explicitly demo interactions
- GitHub Actions debug APK build passed for commit `5f3da2540e28ec706776e154d91333f6068344b4`

## Production blockers

### 1. Accounts and verification

- Add Firebase Authentication (or an equivalent trusted identity provider).
- Implement email registration/sign-in and email verification.
- Implement phone number OTP sign-in with resend limits and abuse protection.
- Persist user profiles in a trusted backend/database.
- Add account deletion and recovery flows.
- Do not mark an account verified based only on client-side state.

Required configuration before enabling this work:
- Firebase Android app registration for `com.rakib.voxmeet`.
- A valid `google-services.json` added through a secure repository workflow (never commit service-account private keys).
- Firebase Email/Password and Phone providers enabled.
- SHA-256/SHA-1 fingerprints registered where required.

### 2. Live voice security

- Replace `TokenSource.fromDevelopmentTokenServer(...)` with an authenticated VoxMeet backend token endpoint.
- Verify the signed-in user on the backend before issuing short-lived LiveKit room tokens.
- Enforce room membership, host/moderator permissions and the 12-participant limit server-side.
- Add disconnect/reconnect handling, moderation, block/report and abuse controls.
- Never ship LiveKit API secrets or signing keys in the Android application.

### 3. Wallet, gifts and PK

- Replace the local demo balance with a server-authoritative wallet ledger.
- Validate every gift and coin debit in an atomic backend transaction.
- Add idempotency keys, transaction history, fraud checks and audit records.
- Implement PK sessions, opponent acceptance, server clock, score events and settlement on the backend.
- Never award coins based only on a client callback or UI event.

### 4. Google Play payments

- For virtual coins and other digital goods sold inside the Play-distributed app, implement Google Play Billing unless VoxMeet is eligible for and enrolled in a permitted alternative billing program.
- Verify purchase tokens on the backend with Google Play Developer API before crediting coins.
- Handle acknowledge/consume, refunds, chargebacks and duplicate purchase notifications.
- Do not add SSLCOMMERZ as an in-app virtual-coin checkout unless current Play policy explicitly permits the flow and the developer account is eligible.

### 5. Store release requirements

- Build and test a signed Android App Bundle (AAB); the current workflow produces a debug APK only.
- Configure release signing through protected CI secrets or a secure local signing process.
- Confirm target API requirements at release time.
- Prepare privacy policy, Data safety answers, content rating, app access instructions, screenshots, store listing and support contact.
- Complete Play Console testing requirements applicable to the developer account.
- Test on physical devices, different Android versions, poor networks and interrupted audio sessions.

## Current status

The project is a functioning development build, not yet production-ready for public release. The successful debug build confirms compilation only; it does not certify authentication, payments, backend security, privacy compliance or Play policy compliance.
