# LBRY Community — US submission preparation

Prepared November 11, 2026. Draft engineering handoff, not a submitted listing or an approved privacy policy.

## Confirmed owner choices

- Name: LBRY Community
- Initial country: United States
- Public app support: lbrycommunity@gmail.com
- Organization and D-U-N-S number: neither exists yet
- Owner phone testing: signed-in playback and publishing with a custom thumbnail passed on Galaxy S24+

## Store listing copy for review

Short description: Browse, watch, and publish videos on the LBRY network.

LBRY Community is an independent, community-maintained fork of the open-source LBRY Android app. Browse videos, follow channels, and publish videos with your own custom thumbnails on the LBRY network.

The app includes a LBRY wallet and uses third-party LBRY services for network and account features. LBRY Community is not an official release from LBRY Inc. or Odysee.

Support: lbrycommunity@gmail.com

Do not submit this copy until the wallet/payment policy review, moderation, account deletion, privacy policy, and remaining release checks are complete. Screenshots must come from the final submitted build. Content rating and age audience must reflect the actual content and controls; neither has been selected.

## Preliminary data inventory from source

This inventory is not a completed Data safety declaration. Backend retention, processing, encryption properties, and deletion capability need confirmation from the relevant operators. No personal wallet data was inspected.

| Flow | Evidence in source | Remaining verification |
| --- | --- | --- |
| Account and sign-in | `utils/Lbryio.java` uses api.lbry.com; user/new, user/me and authentication tokens | Backend operator, email/identifier retention, deletion process and who can fulfill it |
| Optional wallet synchronization | `tasks/wallet/SyncSetTask.java` sends old_hash, new_hash and data to sync/set; SyncGetTask retrieves sync data | Inspect SDK encryption/key lifecycle and service retention before describing custody or confidentiality |
| Comments | `utils/Comments.java` uses comments.lbry.com/api/v2 | Identifier/content collection, moderation and deletion responsibilities |
| Publishing and thumbnails | Public LBRY publishing plus third-party thumbnail upload | Public visibility, host retention/removal, account linkage and disclosure |
| Playback and images | SDK, network hubs and CDN requests | IP/request metadata, logs, purposes and retention |
| Analytics | `utils/LbryAnalytics.java` initializes only with a configured Firebase app; default build omits fork Firebase configuration | Verify final merged manifest/configuration and SDK telemetry settings; do not claim all telemetry is absent |
| Local imports | `utils/DocumentFiles.java` copies picked documents into private imports storage | Draft-aware cleanup remains; no broad Android 29+ storage permission |
| Blockchain actions | Publishing, wallet transactions, tips and paid content | Explain public/immutable records and policy treatment accurately |

Source paths in this table are relative to `app/src/main/java/io/lbry/browser/`.

## Services needed before release

**Moderation:** a report intake endpoint/operator, content and user reporting UI, local user blocking across feeds/search/related content, enforcement workflow, acceptable-use terms, and abuse handling. Existing copyright reporting is not a substitute. A working support email alone does not establish these capabilities.

**Account deletion:** establish a verified request and fulfillment process with the actual account backend. Add an in-app path and a public web request page once that process exists. Do not implement a fake success response or delete local wallet storage as a substitute for deleting the remote account. Explain which public blockchain records cannot be removed.

**Privacy:** identify the legal operator and service responsibilities, retention and deletion terms; approve and host a policy, then complete Data safety against the final build. Publishing a generic policy before those facts are known would misrepresent the app.

## Owner registration steps

1. Establish the organization that will operate the app. Its legal identity and contact details must be real and consistent.
2. Obtain its D-U-N-S number through Dun & Bradstreet. Google's guidance says the normal application is free.
3. Register and verify an organization Play Console account, then register the package `com.ebsmith2022.lbrycommunity` if available.
4. Complete the financial features declaration for the United States based on verified wallet behavior and applicable requirements.

An organization account is required for cryptocurrency software wallet providers under Google's current policy. A personal account does not resolve that requirement.

References:
- https://support.google.com/googleplay/android-developer/answer/10788890
- https://support.google.com/googleplay/android-developer/answer/13634885
- https://support.google.com/googleplay/android-developer/answer/16329703
- https://support.google.com/googleplay/android-developer/answer/9876937
- https://support.google.com/googleplay/android-developer/answer/13327111

## Release gate

Use PLAY_READINESS.md for technical checks and known limitations. Keep the signed-in/custom-thumbnail phone results associated with the previously installed alpha; the branding-only follow-up has build/lint verification, not another phone run. The deliverable APK is debug-signed and the AAB is unsigned. Neither is a production Play release. Configure upload signing after account setup without uninstalling the owner's wallet app or losing its data.
