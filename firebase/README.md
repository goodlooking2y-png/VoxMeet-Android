# VoxMeet Firebase setup

## Realtime Database security rules

The reviewed rules for the VoxMeet data model are in `firebase/database.rules.json`.

They cover:
- Authenticated reads of public profiles and numeric-ID mappings.
- Each signed-in user can create/update only their own profile.
- Numeric IDs are eight digits and cannot be claimed by another UID.
- Follow/unfollow edges must match the signed-in account.
- Follow notifications can only be created by the follower and read by the recipient.
- All other paths are denied by default.

## Publish the rules to the Firebase project

The GitHub repository is not connected to Firebase project-admin credentials, so committing the rules file does not publish it automatically.

1. Open Firebase Console: https://console.firebase.google.com/
2. Select project `voxmeet-a4332`.
3. Open **Build → Realtime Database → Rules**.
4. Replace the editor contents with the complete JSON from `firebase/database.rules.json`.
5. Click **Publish**.
6. Return to VoxMeet and test account registration, profile creation, ID lookup, follow and unfollow with two separate test accounts.

Do not use public rules such as `.read: true` or `.write: true`.

## Free-plan note

These rules and the Android database integration do not require enabling Blaze billing. Keep the Firebase project on Spark unless the project owner explicitly approves a paid-plan change.
