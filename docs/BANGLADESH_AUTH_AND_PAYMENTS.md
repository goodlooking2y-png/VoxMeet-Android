# VoxMeet Bangladesh authentication and payments foundation

This document records the implementation plan for the Bangladesh launch path. It is intentionally separate from production credentials and does not claim that payments or authentication are live.

## Current baseline (reviewed 2026-11-14)

- Android app: Kotlin + Jetpack Compose, applicationId `com.rakib.voxmeet`, minSdk 24.
- LiveKit audio manager currently uses the Development Token Server. This must be replaced with an authenticated backend token endpoint before production.
- Discover, Messages and Profile are currently placeholder destinations in the Compose UI.
- Wallet currently displays a demo balance. It is not a real balance and must not be used for purchases or withdrawals.
- GitHub Actions currently runs `:app:assembleDebug` and uploads a debug APK. A production release/signing workflow is still required.

## Bangladesh payment approach

Recommended first integration: **SSLCOMMERZ hosted checkout**, subject to merchant onboarding approval and confirmation of the payment channels enabled for VoxMeet. Its hosted checkout can present supported cards, internet banking and mobile banking channels (including bKash where enabled). Add direct bKash and Nagad integrations only if commercial/coverage requirements justify separate integrations.

Official references:
- SSLCOMMERZ integration documentation: https://developer.sslcommerz.com/doc/v4/
- SSLCOMMERZ onboarding requirements: https://sslcommerz.com/onboarding-requirements/
- bKash merchant portal: https://merchantportal.bkash.com/login
- Nagad merchant service: https://nagad.com.bd/services/?service=merchant-pay

### Payment security rules

1. The Android app calls VoxMeet's backend to create a payment intent; it never calls the gateway with a secret.
2. Backend creates a unique transaction ID and initiates the gateway session using server-only credentials.
3. App opens the gateway-hosted checkout URL.
4. A browser redirect is informational only. Backend validates the transaction with the gateway validation API and receives/validates IPN notifications.
5. Credit the user's coin ledger only after server-side validation confirms the expected transaction ID, amount, currency (BDT), and successful status.
6. Make IPN handling idempotent; duplicate notifications must never credit coins twice.
7. Store immutable transaction/ledger records. Never accept a balance or coin amount supplied by the client.
8. Keep gateway Store ID/password, webhook secrets, Firebase Admin credentials and LiveKit API secrets out of the Android app and Git repository.
9. Start in sandbox/test mode. Do not enable real-money purchases until merchant approval, legal/business checks, production credentials, refund handling and reconciliation are complete.

## Authentication and user profile

Proposed first version:
- New account registration with email + password, verified email address, and verified phone number using SMS OTP.
- Link email/password and phone credentials to one Firebase Auth user so a user does not accidentally create two accounts for the same person.
- Require explicit consent before collecting profile data; collect only what is needed (display name, unique user ID, optional avatar, country and optional bio).
- Do not request contacts, precise location, date of birth, national ID or address unless a documented feature/legal requirement needs it.
- Provide privacy policy, terms, account deletion and data correction flows.
- Apply Firestore/Database security rules so users can read/write only permitted records. Admin roles must be assigned server-side, never from client input.
- Phone verification requires Firebase project configuration, enabled phone provider, SMS region policy, and Android app SHA fingerprints. Test with Firebase test numbers before live SMS.

Official Firebase references:
- Email/password auth: https://firebase.google.com/docs/auth/android/password-auth
- Phone auth: https://firebase.google.com/docs/auth/android/phone-auth

## Backend services required

- Firebase Authentication for verified identity (or equivalent managed identity service).
- Firestore (or a properly secured backend database) for user profiles, room metadata, moderation and wallet ledger.
- A trusted server (Cloud Functions/Cloud Run or another hosted backend) for LiveKit token minting, payment initiation, gateway validation/IPN, admin actions and coin ledger updates.
- LiveKit production token endpoint; remove Development Token Server usage before release.
- Rate limits, audit logs, abuse monitoring, backups and server-side authorization.

## Delivery sequence

1. Create Firebase project and configure Android app SHA-1/SHA-256; enable email/password and phone auth; add the matching `google-services.json` securely to the build process (do not commit service-account private keys).
2. Implement registration/login, email verification, phone OTP, credential linking, profile and consent screens; test with Firebase test numbers.
3. Add backend identity verification and authenticated LiveKit token endpoint.
4. Add wallet UI backed only by server ledger; no demo balance in production.
5. Add SSLCOMMERZ sandbox hosted checkout, validation API and IPN; test success, failure, cancel, duplicate IPN and tampered amount cases.
6. Apply merchant production credentials only after VoxMeet's merchant account is approved; then conduct a small controlled production transaction and reconciliation.
7. Implement admin/moderation, privacy/account deletion, release signing, crash reporting and end-to-end QA.

## Inputs required before live integration

The repository owner must create/provide access to:
- Firebase project for Android package `com.rakib.voxmeet` (Firebase console access or a securely supplied `google-services.json`; never share Firebase Admin private keys in chat/repository).
- SSLCOMMERZ merchant account and sandbox credentials, then production credentials after approval.
- A backend hosting project/domain and deployment access.
- Business/legal details required by the selected gateway. SSLCOMMERZ onboarding requirements vary by business type and can include trade license, NID, TIN, bank account/cheque leaf and DBID.

Until those are configured, the app can be developed and tested in sandbox, but real OTP delivery, real payments and production wallet crediting cannot be honestly marked complete.
