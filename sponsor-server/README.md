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
- No ad network SDK, persistent viewer/device identifiers, content URL, wallet information, automatic LBC transfer or tracking pixels. The HTTP server necessarily receives network requests. It stores aggregate campaign/day impressions and clicks, not viewer IPs. Failed admin login attempts are stored for 15 minutes for throttling.

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

Use the Android SDK/JDK environment documented in `../docs/PLAY_READINESS.md`. This build is `com.ebsmith2022.lbrycommunity`, version `0.18.2-sponsors-alpha.2` (17044), debug-signed. It is separate from the older public `io.lbry.browser` release. It is not a replacement signed release for that package.

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

## Daily, weekly and monthly performance reports

The owner-only **Ad performance** panel reports each campaign's impressions, clicks and click rate for a chosen day, Monday–Sunday week, or calendar month. Select any date within the period. Reporting timezone is **America/Detroit (Eastern Time, including daylight saving changes)**. Aggregation uses server receipt time, not phone clocks. A late click crossing midnight can appear on a different day from its impression, so a narrow period's click rate can exceed 100%.

An impression is client-reported after at least 50% of the image is visible in a focused foreground window for one continuous second, or upon a confirmed sponsor click. An off-screen banner, image download or dashboard preview is not an impression. A click means confirming **Open website** and successfully handing the URL to Android; it does not prove the website loaded or a purchase occurred. Cancelling the dialog does not send a click.

Each ad response carries a random five-minute receipt. Server-side hashing and deduplication permit at most one impression and one click per receipt. Confirmed fast clicks also establish the impression. The token does not identify a user/account/device and is not persisted by the app. Expired receipt records are purged on metrics/serving activity; daily aggregate counts are retained in SQLite. Temporary receipts are capped at 100,000; if full, banners can still serve without reporting a token.

A fresh delivery (including the 60-second refresh) may count again. These are **not unique-user counts**. Events are best effort; connectivity failures can undercount. A modified client/bot can request receipts and fabricate events; these reports are not fraud-audited or suitable as guaranteed pay-per-impression billing. No events are backfilled for older APKs. Daily totals remain after campaigns expire or are rejected. Public endpoints never expose reports, payment data or complimentary reasons.

The current server remains localhost-only. Before hosting, update the privacy notice with aggregate collection and hosting access-log retention, and add production request/rate limits. Keep the database in private backups. Never include it or login files in Git or APKs.

Validation: 13 backend scenarios passed (including date boundaries/DST, auth, receipts, duplicates and complimentary gates), and two emulator tests passed (banner lifecycle and actual off-screen/visible impression reporting). Android build/lint passed with the existing upstream translation baseline. A non-exported debug-only test activity is excluded from release builds. The controller's click callback is built; phone/browser handoff still needs manual confirmation on the new APK.

## Online deployment — 13 November 2026

The production service is https://lbrycommunity-ads.duckdns.org, separate from local test data. DigitalOcean Droplet 618775020 (SFO2, Ubuntu 24.04, 1 GB RAM) uses Node 24, nginx HTTPS, daily server backups and consistent daily SQLite backups. The app is loopback-only behind nginx, runs as `sponsors`, and restarts on failure. `SPONSOR_ORIGIN` enforces the canonical host/origin and Secure cookies. nginx overwrites proxy headers, limits requests/connections and login attempts, and disables routine access logs; error logs retain seven daily rotations. Only SSH/HTTP/HTTPS are permitted through UFW. Certificate auto-renewal and its nginx reload hook were verified with a dry run.

Production begins empty with delivery stopped. Local prototype campaigns/passwords were not migrated. The existing phone build still uses localhost: a new build with `-PsponsorBaseUrl=https://lbrycommunity-ads.duckdns.org` is needed for independent internet delivery. No new Android APK was released in this deployment.

Owner password changes are available under **Change dashboard password**. Enter the current password, then a different 16–128-character password twice. The server atomically persists a salted scrypt hash, invalidates all sessions, removes the original server first-login file, and records a private audit entry. Save the new password in a password manager. Old local copies of setup passwords become invalid. SSH-authorized recovery must replace the hash and invalidate sessions with the service stopped; never put a password in a shell argument, Git, or an APK.

Deployment templates are in `deploy/`. Service files use `/opt/lbry-sponsors` and private data `/var/lib/lbry-sponsors`; secrets and databases are never source artifacts. SQLite backup files in `/var/backups/lbry-sponsors` are private, retained for approximately eight days, and are also captured by daily Droplet backups. A server restore can restore old credentials/sessions; invalidate sessions and reset the owner password after restoring. No MFA or email password recovery is implemented.

## Shared video likes — 13 November 2026

`GET /v1/likes/:claim` returns `{count, liked}`; `POST` sets an explicit boolean `liked`. Video IDs are 40 lowercase hex characters. The optional GET / required POST `X-Like-Token` is a 64-hex video-scoped pseudonymous token. A random secret stays in private Android preferences (Android backup is disabled); HMAC-SHA256 derives independent tokens per claim. The server retains only claim IDs and token hashes for active likes. Unlike deletes that row. A composite primary key makes retries idempotent. Count is shared across fork users and survives server restarts; it is not an Odysee count or verified unique-person count. Reinstalls/data clearing lose previous-like control and allow another installation vote. A modified client can invent tokens; this is lightweight community feedback, not fraud-proof voting or a payment metric.

nginx limits like requests to 30/minute/IP with a burst of 20; count polling is once per minute while the screen is active. Shared IPs can hit throttling; the app offers retry and never blocks playback. Errors do not optimistically change the like or count. Video navigation and lifecycle cancellation discard stale responses. No personal wallet/account data is used.

Android version 0.18.3-community-alpha.3, code 17054, uses the HTTPS community service by default; loopback remains an explicit test override. The previous local service APK remains separate as an old output. Debug-only instrumentation fixtures are non-exported and excluded from release builds.

Validation for the likes update: 16 backend tests passed locally/on-server; a two-voter HTTPS smoke test passed and removed its synthetic data. Two isolated emulator tests passed (LikeController and sponsor impressions), 11 existing URI unit tests passed, and Android lint passed with the existing translation baseline. Final debug APK uses the live HTTPS endpoint and the existing debug signing certificate. A clean temporary Gradle build directory avoided an iCloud-generated duplicate class file blocking the old output directory.
