# Firebase Firestore setup (Spark plan)

VoxMeet stores each signed-in user's profile and preference document at
`users/{Firebase Authentication UID}`. The Android app uses the Firebase
Firestore SDK and falls back to local device preferences if cloud access fails.

## One-time Firebase Console setup

1. Open Firebase Console and select the existing `voxmeet-a4332` project.
2. Open **Build → Firestore Database** and create a database if one does not
   already exist. Choose a region close to your users. The Spark free plan is
   sufficient for initial development within its quotas; do not enable billing.
3. In **Firestore Database → Rules**, publish the contents of
   [firestore.rules](firestore.rules).
4. Install the latest VoxMeet build and sign in. Profile and preference changes
   should then sync to the signed-in user's document.

## Scope and limitations

- Firestore rules restrict each user to their own profile/preferences document.
- Privacy switches are currently saved as preferences only. They do not yet
  enforce message blocking, profile visibility, or online-status hiding in
  server-side chat/call APIs.
- Notification, sound, and vibration preferences sync to Firestore, but actual
  push delivery still requires Firebase Cloud Messaging and notification
  permission/channel integration.
- If Firestore has not been created or rules are not published, the app reports
  a cloud-sync error and keeps local preferences on the device.
