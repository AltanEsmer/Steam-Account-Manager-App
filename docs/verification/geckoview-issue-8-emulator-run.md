# Issue #8 production GeckoView verification receipt

Status: **Machine and independent emulator verification PASS at `752a8d0`; focused physical-device Steam smoke test pending**

## Exact candidate

- Source commit: `752a8d07fa694ea643b6e020af0ce4973523a73f`
- Branch: `codex/geckoview-campaign`
- Variant: debug, using the production Steam-to-GeckoView routing path
- GeckoView: `153.0.20260810162159`
- Preserved evidence directory:
  `C:\Users\esmer\AppData\Local\Temp\sam-gv8-752a8d0`
- `app-debug.apk`: 598,925,999 bytes; SHA-256
  `00F506D6BFAD1209FB980331AC78B691E577D39245C013A69482FE129788E0DC`
- `app-debug-androidTest.apk`: 2,255,190 bytes; SHA-256
  `FAEBE456B0080668A98E27F27E10DDC30560CA57C0BA534C2DDDE3F5FD0944F0`

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
tightly scoped built-in Gecko content script. The app shows a one-time notice that
existing WebView authentication cannot be copied and may require a new sign-in.
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
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --rerun-tasks
```

Exit 0 in 50 seconds; 79 tasks executed. All 52 unit tests passed with zero
failures, errors, or skips. `assembleDebug`, `assembleDebugAndroidTest`, and
`lintDebug` passed. Debug lint reported zero errors and 120 warnings; independent
release lint reported zero errors and 52 warnings.

The issue-specific `ProductionGeckoSessionTest` passed twice after the final source
change and twice again against the committed exact HEAD. It uses two fixed synthetic
account labels, a random run marker, and a loopback-only page. It verifies:

- the one-time migration notice and explicit acknowledgement;
- cookie, localStorage, and IndexedDB persistence after closing and reopening A;
- A to B isolation and B to A restoration;
- prior browser-generation PID/name disappearance;
- bounded production stop and reopen;
- rapid A/B requests ending in only the latest authorized B session; and
- exactly one production `:browser` worker.

The primary full instrumentation run passed 25/25 with zero failures, errors, or
skips in 352.108 seconds. The independent medium tester repeated the focused test
and the full suite on the same serial. Its full run passed 25/25 with zero failures,
errors, or skips in 603.704 seconds. Result path:

`app/build/outputs/androidTest-results/connected/debug/TEST-Codex_GeckoView_Campaign_API_36(AVD) - 16-_app-.xml`

The independent tester found zero app-owned processes after the run. Both primary
and independent `git diff --check` and clean-worktree checks passed at the exact
candidate SHA.

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
- No credentials, Steam identifiers, authentication pages, cookies, tokens, trades,
  payments, or browser-storage exports were used or captured.

The physical-device procedure is in
[Dagkan's campaign guide](../manual-testing/geckoview-campaign-dagkan.md#issue-8-production-steam-login-smoke-test).
Issue #9 remains blocked until that exact-build smoke test is reported PASS and its
sanitized evidence is accepted.
