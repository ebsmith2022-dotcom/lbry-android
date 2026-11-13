# LBRY Community — Play preparation checkpoint

Google Play work paused by owner November 12, 2026. Current work is the independent fork sponsor prototype; see `../sponsor-server/README.md`. The build details below describe the earlier Play checkpoint.

Status: engineering alpha; **not ready for Google Play submission or a public release**.
Prepared November 11, 2026. The working GitHub release v0.17.1-fork.3 is unchanged.

## Identity and build

- Independent fork name approved by owner: LBRY Community.
- Public support email approved by owner: lbrycommunity@gmail.com (Google account creation verified November 11, 2026).
- Application ID: `com.ebsmith2022.lbrycommunity` (availability not yet checked in Play Console).
- Release version: `0.18.0-play-alpha.1`, version code 17024, ARM64, min SDK 24, target SDK 36, compile SDK 37.
- AGP 9.5.1 / Gradle 9.6.0; tested with Android Studio's bundled JDK 25.
- App has its own storage and loopback ports 5379/5380. It does not migrate or read the installed LBRY app's wallet. A later migration needs a separately designed, user-controlled process.
- SDK native runtime is the checksum-pinned upstream 0.102.0 AAR. Four Java service classes are maintained in `sdk/`, with upstream license and revision recorded there.
- APK is debug-signed for emulator and owner phone testing. AAB is unsigned; no upload key or Play signing setup has been performed for this package.

## Implemented

- Current Android target, bundle generation, explicit exported flags, immutable notification intents, private dynamic receivers, package-scoped internal messages.
- Foreground data-sync service type, early foreground notification, Android background timeout handler, notification runtime permission.
- Updated ExoPlayer to its final legacy release 2.19.1 to resolve the target-31+ startup crash. Media3 migration remains follow-up work.
- FFmpegKit 7.1.7, including a 16 KB-compatible C++ runtime.
- System document picker imports for videos, custom thumbnails, channel images, and shared files; no broad modern media-library permission.
- Modern downloads exported via MediaStore; app-owned source files remain available if export fails.
- Legacy paid verification product removed. Previously removed Buy Credits/Bittrex links stay removed.
- Firebase is disabled by default until this fork has its own configuration (`-PenableFirebase=true` is an explicit opt-in). Missing Firebase no longer crashes About or analytics initialization.
- Automatic SDK telemetry opt-in removed; existing user preference controls remain.
- Automatic Android backup disabled. No personal wallet files, seeds, or keys were accessed or packaged during this work.
- Process-lifetime media cache in its own cache subdirectory, avoiding blocking cache release and directory races during video changes.
- Floating controls adjusted for navigation-bar insets.
- Welcome and About identify LBRY Community as an independent fork; About includes the approved support email. This does not replace the outstanding fork-specific terms/privacy work.

## Verification and limits

- Debug APK and release AAB build successfully.
- 11 existing URI unit tests passed; 2 new document-provider instrumentation tests passed on an isolated Android 17 ARM64 emulator.
- Emulator startup, welcome screen, notifications prompt, and main browsing screen exercised. The isolated SDK completed initial wallet sync with zero blocks behind.
- Both APK and AAB contain 50 ELF libraries/extensions checked for 16 KB-compatible LOAD alignment. APK ZIP alignment also passed. This is **not** a successful 16 KB device runtime test.
- Full lint: no unbaselined errors; approximately 1,180 warnings and one third-party lint compatibility hint. Exactly 54 unchanged upstream translation findings are recorded in `app/lint-baseline-translations.xml`; code errors are not suppressed. Baseline entries were checked against the parent commit.
- Owner reports successful signed-in playback and publishing with a custom thumbnail on the Galaxy S24+ using this alpha (November 11, 2026). These are user-reported results.
- Camera capture, download export, long background playback, older Android devices, tablet/rotation layouts, and 16 KB runtime testing remain necessary.
- Imported upload files are retained in app-private storage for the current publishing flow; a draft-aware cleanup policy still needs implementation.
- Old Python 3.7/OpenSSL 1.1 and other native dependencies require a security/lifecycle review. The local SDK HTTP API needs a threat review before recommending this wallet build for general use.

## Release blockers requiring owner/service decisions

The owner approved lbrycommunity@gmail.com as the public support email on November 11. The owner selected a United States launch and confirmed that no organization or D-U-N-S number exists yet. Organization developer registration is blocked on establishing that identity. Do not invent those details or publish placeholder policies.

1. **Developer account type:** Google's Play Console requirements say providers of cryptocurrency software wallets must register as an organization. Resolve organization identity and D-U-N-S requirements before paying/registering. The 12-testers/14-days rule quoted earlier applies to qualifying new personal accounts; it is not a substitute for the required organization account.
2. **Wallet declaration and launch countries:** Complete Financial Features declarations accurately. Google's current form provides a non-custodial-wallet option; verify applicability and supporting obligations rather than asserting a licensing exemption. Check how optional wallet sync affects the description.
3. **User-content moderation:** Existing reporting opens the original LBRY copyright complaint page. Build actual in-app content/user reporting, user blocking, moderation handling and enforcement, and clear acceptable-use terms. A mail link alone is not a completed moderation system. Review content filtering and age eligibility.
4. **Account deletion:** Verify deletion support for the actual LBRY account backend; provide an in-app request path and publicly reachable request page. Explain immutable public blockchain data accurately. Signing out is not account deletion.
5. **Privacy and Data safety:** Inventory account/email, uploads, public blockchain transactions, analytics, third-party API/CDN traffic, optional wallet sync, and diagnostics. Publish an owner-approved privacy policy and verified contact. Do not claim the app collects no data.
6. **Payments:** Review paid content, tips/supports, publishing fees, and reward messaging against applicable Play policies. Removal of the old Google Billing verification product alone does not settle these flows.
7. **Branding/licenses:** Distinguish the fork in onboarding, About and store listing; review LBRY name/logo use and FFmpeg GPL/source-distribution obligations.
8. **Release QA/signing:** Finish device tests above, prepare store graphics/screenshots, content rating, reviewer access, foreground-service declarations and the testing track required by the account. Configure upload/app signing, build from a reviewed commit, and submit only after the remaining blockers are resolved.

## Reproduce

From the checkout, with Android SDK platform 37 and build tools 36 installed:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
bash gradlew :app:assemble__64bitDebug :app:bundle__64bitRelease :app:test__64bitDebugUnitTest :app:lint__64bitRelease
python3 scripts/check-native-alignment.py app/build/outputs/apk/__64bit/debug/app-__64bit-debug.apk
python3 scripts/check-native-alignment.py app/build/outputs/bundle/__64bitRelease/app-__64bit-release.aab
```

Run instrumentation only on a **disposable emulator**: the Gradle device-test workflow installs and removes its test app. Never point this test command at a personal wallet installation.

```sh
ANDROID_SERIAL=emulator-5580 bash gradlew :app:connected__64bitDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.lbry.browser.utils.DocumentFilesTest
```

## Official references checked

- https://support.google.com/googleplay/android-developer/answer/11926878 — target Android requirements.
- https://developer.android.com/guide/practices/page-sizes — 16 KB compatibility.
- https://support.google.com/googleplay/android-developer/answer/10788890 — required account type for wallet providers.
- https://support.google.com/googleplay/android-developer/answer/16329703 — wallet declarations and country-specific options.
- https://support.google.com/googleplay/android-developer/answer/9876937 — reporting, blocking and moderation.
- https://support.google.com/googleplay/android-developer/answer/13327111 — account deletion requirements.
