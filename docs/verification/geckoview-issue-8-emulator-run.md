# Issue #8 production GeckoView verification receipt

Status: **Repaired machine/emulator verification PASS at `efa9bbe`; current-head independent review and focused physical-device Steam smoke test pending**

## Exact candidate

- Source commit: `efa9bbe49fc9f25924621ce66f71759b538e3bb9`
- Branch: `codex/geckoview-campaign`
- Variant: debug, using the production Steam-to-GeckoView routing path
- GeckoView: `153.0.20260810162159`
- Preserved evidence directory:
  `C:\Users\esmer\AppData\Local\Temp\sam-gv8-efa9bbe`
- `app-debug.apk`: 598,925,999 bytes; SHA-256
  `37B6BD5FAB54517D017C7C2299B0EA102B114EE12282B3E3D430E4932D5C5434`
- `app-debug-androidTest.apk`: 2,265,834 bytes; SHA-256
  `3D802CEC4E50DF41A908EA11A286DF2B27CDE2BFF5C1D2A8F7599AC1CDD4CC7A`
- Preserved full instrumentation XML: 4,740 bytes; SHA-256
  `92BA4FAFFBC454A5D83AE5A15594F00AC5C746D7A88B85700E89847259745B8B`

The APK is tied to the source commit above. The later documentation commit does not
change application or test sources. The phone gate must use this exact APK; the
issue #7 prototype APK is not interchangeable.

## What changed

Selecting Steam for an account now opens the production browser worker with a
persistent GeckoView profile derived from the exact `(account, website)` pair.
Different sessions are serialized through one worker and separate opaque profiles.
The old WebView implementation remains available for non-Steam sites and rollback;
this issue does not claim the final WebView removal.

Steam avatar/profile detection now parses engine-neutral public page metadata from a
tightly scoped app-owned Gecko bridge. Before the Gecko runtime is created, the
helper is activated, or a Steam page is composed, a versioned per-profile dialog
discloses the exact Steam origins,
visible public avatar/profile links, private connection back to the app, and possible
WebView reauthentication. **Allow and continue** is required; Cancel or Back closes
the screen, loads nothing, does not invoke helper installation, and stores no
consent. The async broadcast-to-Room update holds Android's receiver lifecycle until
completion.
Existing account/session database fields and schema are unchanged.

## Environment

- Host: Windows 11 Pro `10.0.26200`, build 26200
- Android Studio: 2025.2.1,
  `AI-252.25557.131.2521.14432022`
- Dedicated AVD: `Codex_GeckoView_Campaign_API_36`
- Exact adb serial: `emulator-5580`
- Android: 16 / API 36
- ABI: x86_64
- Verification date: 2026-09-06

Every adb command selected `emulator-5580`; no personal AVD, physical device, SDK,
virtualization setting, or security setting was modified.

## Automated and emulator results

Primary exact-HEAD build command:

```powershell
$env:ANDROID_HOME = 'C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease --rerun-tasks
```

Exit 0 in 1 minute 4 seconds; 103 tasks executed. All 52 unit tests passed with zero
failures, errors, or skips. `assembleDebug`, `assembleDebugAndroidTest`, and
both lint variants passed. Debug lint reported zero errors and 120 warnings; release
lint reported zero errors and 52 warnings.

The issue-specific `ProductionGeckoSessionTest` passed once before commit and twice
against the committed exact HEAD (53.757 and 57.433 seconds). Its three tests use fixed synthetic account labels,
random run markers, safe public-format detector values, and loopback-only pages. They
verify:

- no page load before versioned helper consent;
- explicit Allow and independent A/B consent;
- Cancel/Back denial, no page load, and a repeated prompt on reopen;
- cookie, localStorage, and IndexedDB persistence after closing and reopening A;
- A to B isolation and B to A restoration;
- prior browser-generation PID/name disappearance;
- bounded production stop and reopen;
- rapid A/B requests ending in only the latest authorized B session; and
- exactly one production `:browser` worker; and
- detector result broadcast through lifecycle-safe receiver completion into the
  synthetic account's Room row, followed by cleanup.

The repaired exact-HEAD full instrumentation run passed 27/27 with zero failures,
errors, or skips in 564.242 test seconds. Gradle exited 0 in 9 minutes 38 seconds.
Result path:

`app/build/outputs/androidTest-results/connected/debug/TEST-Codex_GeckoView_Campaign_API_36(AVD) - 16-_app-.xml`

The primary found zero app-owned processes after the run. `git diff --check` and the
clean-worktree check passed at the exact candidate SHA. The previous medium pass and
xhigh review at the superseded `4bdcfce` documentation checkpoint were invalidated
by the source repairs and are not current acceptance evidence.

The first direct `adb am instrument` attempt used the non-debug test package and
exited 1 without starting a test. `adb -s emulator-5580 shell pm list instrumentation`
identified `com.steamaccountmanager.app.debug.test`; both corrected exact-class runs
then exited 0. This was a command-target error, not an application or test failure.

## Review finding and repair record

The first xhigh review rejected `4bdcfce` because the app-owned detector installed
before informed consent and the avatar receiver returned before its asynchronous
database update completed. The repaired commits are:

- `502b7de` — require versioned, purpose-specific helper consent before Gecko
  composition, with a denial path;
- `4f6e361` — hold the broadcast lifecycle through the Room update; and
- `25f0a1d` — cover allow/deny behavior and detector-to-account persistence;
- `3b28923` — defer Gecko runtime creation until explicit consent; and
- `efa9bbe` — exercise Cancel, Android Back, and repeated prompting.

These repairs invalidate the old `00F506...E0DC` and `F63EF6...4334F` APKs. Only the exact current APK
listed above may enter the phone gate after current-head tester/reviewer acceptance.

## Packaging and rollback checks

The release merged manifest and unsigned intermediary bundle contain the production
browser activity/receiver, pinned Gecko child services, Gecko `omni.ja`, native
libraries, and the built-in Steam profile detector. Searches found zero release
matches for the debug prototype activity/process or issue-6 synthetic marker. The
non-Steam WebView path and its dependency remain present for rollback.

Signed release packaging cannot finish on this host because no release keystore is
configured. Compilation, shrinking, manifest/assets merge, and unsigned bundle
packaging complete before signing. Issue #8's gate artifact is the debug APK above;
no signing key was requested, read, or recorded.

## Acceptance notes and limitations

- Deterministic profile mapping and contained no-backup paths have unit coverage.
- The app-owned detector is fixed APK code, not a supported or replaceable browser
  extension. Its Steam origins, public fields, and app connection require explicit
  consent per profile before installation or navigation; denial grants nothing.
- Production browser storage isolation is directly exercised for cookies,
  localStorage, and IndexedDB. Extension and permission isolation relies on the same
  approved per-profile topology proven in issue #7; the production CSFloat install
  intentionally belongs to issue #10 and is not pulled into issue #8.
- Steam profile/avatar inputs are bounded and restricted to approved HTTPS Steam
  hosts/CDN values. Detection failure is recoverable and does not block browsing.
- The emulator uses a synthetic page. It does not prove real Steam authentication,
  Steam Guard behavior, live avatar markup, vendor-specific behavior, or physical
  device persistence. Those are the focused phone gate below.
- The child-process allowlist matches pinned GeckoView 153's merged manifest and must
  be reviewed when GeckoView is upgraded.
- No credentials, real Steam identifiers, authentication pages, cookies, tokens, trades,
  payments, or browser-storage exports were used or captured.

The physical-device procedure is in
[Dagkan's campaign guide](../manual-testing/geckoview-campaign-dagkan.md#issue-8-production-steam-login-smoke-test).
Issue #9 remains blocked until that exact-build smoke test is reported PASS and its
sanitized evidence is accepted.
