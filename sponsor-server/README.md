# LBRY Community sponsor desk — local prototype

Google Play preparation is paused. This feature is being developed for the independent fork on branch `feature/sponsor-banners`.

## What works

- One owner login, private campaign list and image preview.
- Upload static PNG/JPEG banners (up to 2 MB, 3:1–8:1 aspect ratio; recommended 960×150).
- Name, HTTPS destination, start/end time, and agreed LBC amount.
- Manual payment confirmation with a unique transaction ID, or an owner-only complimentary/test exception with a 5–300 character reason, followed by separate approval.
- Pause, resume, permanently reject/remove, automatic schedule expiry and a master stop switch.
- Decision history stored in SQLite. Rejecting removes a campaign from delivery; it retains the record for accounting.
- Public API returns only currently approved, paid or complimentary, in-date campaigns while delivery is enabled. Multiple eligible ads are selected randomly; no impression guarantee.
- Android displays a labeled banner below the video in the scrollable details area. No overlay or pre-roll. Sponsor links require a confirmation showing the destination hostname.
- Ads are hidden on network errors, invalid responses, app backgrounding and expiry; playback never waits for them. Refresh is at most 60 seconds. Pause cannot instantly recall an already displayed banner.
- No ad network SDK, viewer identifiers, content URL, wallet information, automatic LBC transfer or tracking pixels. The HTTP server necessarily receives network requests; it does not store viewer IPs or impressions. Failed admin login attempts are stored for 15 minutes for throttling.

## Local use

Requires Node 24 or newer. From this directory:

```sh
npm ci
npm start
```

Open http://127.0.0.1:8787 on the Mac. The server listens on loopback only. On its first run it generates a random password and writes `.local-data/first-login.txt` with owner-only file permissions. The server keeps a salted scrypt password hash in `.local-data/admin.json`. Keep the first-login file private; never include `.local-data` in a release or Git commit. No password or admin token is embedded in the Android APK.

SQLite campaign/session data is in `.local-data/sponsors.sqlite`. A private backup must include this database and any WAL/SHM files consistently (prefer a SQLite backup or a backup while the server is stopped). The database includes transaction references; do not publish it.

The initial local workspace contains one clearly labeled, unpaid DEMO draft. Global ad delivery was stopped after UI testing. No sponsor has paid and no real campaign is live.

## Owner workflow

1. Agree on the banner placement, LBC amount and dates with the sponsor outside the app.
2. Give the sponsor your receiving address privately as appropriate. This prototype does not generate invoices or receive funds for you.
3. Upload and review the banner and destination. Save for review.
4. Independently verify the confirmed transaction, full amount and receiving address in your own wallet/explorer. Record the transaction ID and payment checkbox. The server validates its format and prevents reusing a transaction ID, but DOES NOT query the blockchain or verify funds.
5. Click Approve; if the start is in the future, it remains scheduled. Enable delivery when ready.
6. Pause or remove campaigns as needed. Refunds, cancellations and agreements remain manual.

Campaign creative, destination and dates are immutable in this first version so approval cannot silently apply to changed content. Remove the original and create a replacement for corrections. A rejected campaign cannot resume. There is no automatic refund or payment reassignment. No creator revenue sharing or advertiser self-service has been implemented.

## Android build

Ads are disabled when `sponsorBaseUrl` is absent. Build the local debug prototype with:

```sh
bash gradlew :app:assemble__64bitDebug -PsponsorBaseUrl=http://127.0.0.1:8787
```

Use the Android SDK/JDK environment documented in `../docs/PLAY_READINESS.md`. This build is `com.ebsmith2022.lbrycommunity`, version `0.18.1-sponsors-alpha.1` (17034), debug-signed. It is separate from the older public `io.lbry.browser` release. It is not a replacement signed release for that package.

For a connected test device, explicitly select its serial and forward the local server:

```sh
adb -s DEVICE_SERIAL reverse tcp:8787 tcp:8787
```

Only do this on a deliberately selected test device. The phone still needs a separately authorized app installation. Local banners require the Mac server and the forwarding connection; they will not work over ordinary mobile data. A release build ignores HTTP loopback configuration and only accepts HTTPS. The public server URL, once available, must be set in a new build; do not ship the localhost APK as a public release.

## Checks

```sh
npm test
```

Backend tests cover authentication, session invalidation, CSRF/origin checks, throttling, duplicate transaction IDs, approval/payment gating, dates, global stop, image normalization and invalid inputs. `npm install` reported no known dependency vulnerabilities at implementation time; this is not a security audit.

`SponsorBannerTest` runs on a disposable Android emulator with the local debug URL. It owns port 8787 inside the emulator, so remove any adb reverse mapping before running it. It tests actual network responses, image display, expiry, invalid JSON, and stop behavior. Do not run connected instrumentation tests on a personal wallet device: the test runner installs/uninstalls packages.

## Before internet hosting

This is deliberately localhost-only. A hosting deployment still needs a chosen account/domain, TLS termination, strict trusted-host/proxy configuration, Secure cookies, production rate/resource limits, backups, credential rotation/recovery and preferably MFA/access protection for the admin dashboard. Review hosting costs and retention/privacy disclosures before launch. Review advertiser content/destination rules and sponsorship agreements. No hosting was purchased, no ports exposed, and no app or ads publicly released.

## Complimentary / test campaigns

Enter 0 LBC for a new free campaign (existing unpaid drafts may also be marked complimentary). Save, expand **Complimentary / test campaign**, enter a reason, and click **Mark complimentary / test**. Then approve separately. The private history records the reason; the public API does not expose it. Payment and complimentary status are mutually exclusive, and paid, approved or rejected campaigns cannot be reclassified. Existing databases migrate automatically without changing campaigns or passwords.
