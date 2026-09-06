# Issue #8 production GeckoView verification receipt

Status: **Machine/emulator verification PASS at application/test source `6b1f4b5`; current-head independent review and focused physical-device Steam smoke test pending**

## Exact candidate

- Application/test source commit: `6b1f4b5e34e3e01fa7ed96824563bd8a33561533`
- Branch: `codex/geckoview-campaign`
- Variant: debug, using the production Steam-to-GeckoView routing path
- GeckoView: `153.0.20260810162159`
- Preserved evidence directory:
  `C:\Users\esmer\AppData\Local\Temp\sam-gv8-6b1f4b5`
- `app-debug.apk`: 598,942,383 bytes; SHA-256
  `D17F8D09EB6EBB2FE5BECB1CDED5C00559AD9541CE51EB4BA81E7B823273FC63`
- `app-debug-androidTest.apk`: 2,270,718 bytes; SHA-256
  `622BDE349CB6B86E61969EDD113DC05BFFB0AFD79ABF64CC064981E0AA343D29`
- Preserved full instrumentation XML: 4,735 bytes; SHA-256
  `D64303815195ACF3497742D151163DD470B9A9FB77A6C8DBC1A359FA484215B7`

The APK is tied to the application/test source commit above. A following
documentation-only checkpoint does not change the APK or its source. The phone gate
must use this exact APK; the issue #7 prototype APK is not interchangeable.

## What changed

Selecting Steam for an account now opens the production browser worker with a
persistent GeckoView profile derived from the exact `(account, website)` pair.
Different sessions are serialized through one worker and separate opaque profiles.
The old WebView implementation remains available for non-Steam sites and rollback;
this issue does not claim the final WebView removal.

Steam avatar/profile detection parses engine-neutral public page metadata through a
tightly scoped app-owned Gecko bridge. Before the Gecko runtime is created, a stored
bridge is activated, a new bridge is installed, or a Steam page is loaded, a
versioned per-profile dialog discloses the exact Steam origins, visible public
avatar/profile links, and the private connection back to the app. **Allow and
continue** synchronously records consent before Gecko activation. Cancel or Android
Back closes the screen, loads nothing, starts no Gecko child process, and stores no
consent. A bridge retained from an earlier valid consent remains inert until renewed
consent is recorded. The async broadcast-to-Room update holds Android's receiver
lifecycle until completion. Existing account/session database fields and schema are
unchanged.

Both the production browser worker and the disposable prototype now attempt graceful
Gecko shutdown for three seconds within an eight-second total stop budget. If Gecko
does not exit, only the freshly revalidated exact captured worker PID and process
name may be terminated; allowlisted pinned-Gecko child processes are then reaped and
the empty process state must remain stable. This shared lifecycle repair was required
by the full regression suite and does not broaden the production migration scope.

## Environment

- Host: Windows 11 Pro `10.0.26200`, build 26200
- Android Studio: 2025.2.1, `AI-252.25557.131.2521.14432022`
- Dedicated AVD: `Codex_GeckoView_Campaign_API_36`
- Exact adb serial: `emulator-5580`
- Android: 16 / API 36
- ABI: x86_64
- Verification date: 2026-09-06

Every adb command selected `emulator-5580`; no personal AVD, physical device, SDK,
virtualization setting, or security setting was modified. After repeated suites
caused Android's package service and system UI to become unresponsive, the exact
dedicated AVD was stopped and cold-launched with `-no-snapshot-load`. It was not
wiped. The final clean run followed that recovery.

## Automated and emulator results

Primary application/unit/lint build command at exact source `6b1f4b5`:

```powershell
$env:ANDROID_HOME = 'C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease --rerun-tasks --console=plain
```

Exit 0 in 1 minute 8 seconds; 103/103 tasks completed. All 52 unit tests
passed with zero failures, errors, or skips. `assembleDebug`,
`assembleDebugAndroidTest`, and both lint variants passed. Debug lint reported zero
errors and 120 warnings; release lint reported zero errors and 52 warnings.

The final exact-source emulator command was:

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' connectedDebugAndroidTest --console=plain
```

It passed 27/27 instrumentation tests with zero failures, errors, or skips. Gradle
exited 0 in 8 minutes 5 seconds; the XML records 467.105 test seconds. Result path:

`app/build/outputs/androidTest-results/connected/debug/TEST-Codex_GeckoView_Campaign_API_36(AVD) - 16-_app-.xml`

The final suite includes all three `ProductionGeckoSessionTest` cases plus the
prototype regression coverage. The production cases verify:

- no page, bridge activation, or Gecko child process before versioned helper consent;
- a previously installed helper remains dormant across a forced consent-version
  transition until renewed consent is recorded;
- explicit Allow and independent A/B consent;
- Cancel/Back denial, no page load, and a repeated prompt on reopen;
- cookie, localStorage, and IndexedDB persistence after closing and reopening A;
- A-to-B isolation and B-to-A restoration;
- prior browser-generation PID/name disappearance;
- bounded production stop and reopen;
- rapid A/B requests ending in only the latest authorized B session;
- exactly one production `:browser` worker; and
- detector result broadcast through lifecycle-safe receiver completion into the
  synthetic account's Room row, followed by cleanup.

Before the final source checkpoint, the production class passed three repeated
stress runs after its lifecycle repair (241.303, 228.775, and 263.444 seconds). The
final full suite is the exact-source acceptance result; the independent tester must
still repeat focused and full verification at the documentation checkpoint.

The final suite also verifies external-browser handoff by requiring the resolved
Chrome activity to become top-resumed, then foregrounds the exact prototype router
for cleanup. The corrected external-handoff plus marker-disable sequence passed
three consecutive runs (47.815, 45.762, and 51.195 seconds).

The primary found zero app-owned processes after the final run. `git diff --check`
and the clean tracked-worktree check passed at exact source `6b1f4b5`. Earlier failed
runs exposed real graceful-shutdown and test-foregrounding defects and were repaired;
their results are superseded, not counted as passing evidence. One earlier direct
`adb am instrument` command targeted the non-debug test package and exited 1 without
starting a test; package inspection identified the correct
`com.steamaccountmanager.app.debug.test` runner.

## Review finding and repair record

Earlier independent review and full-suite evidence found the following blockers:

- Gecko runtime creation occurred before informed detector consent;
- detector consent was not yet durable when runtime startup could force-stop the
  activity process;
- a previously installed helper was not explicitly tested across consent renewal;
- graceful Gecko shutdown could exceed the bounded stop contract;
- the prototype shared that lifecycle flaw; and
- the external-browser regression test did not independently prove foreground handoff.

The bounded repair commits are:

- `3b28923` — defer Gecko runtime creation until explicit consent;
- `efa9bbe` — cover Cancel, Android Back, and repeated prompting;
- `e4ec048` — add bounded exact-PID production shutdown fallback;
- `a10824e` — commit detector consent before Gecko activation;
- `4d09ef3` — exercise renewed consent with a previously installed helper;
- `1d1edc1` — apply the same bounded shutdown contract to the prototype;
- `bd483d9` — isolate external-handoff recovery; and
- `6b1f4b5` — independently prove external foreground handoff and router recovery.

The prior tester/reviewer result at `1f03397` was invalidated by these source repairs
and is not current acceptance evidence. Only the exact APK listed above may enter
the phone gate after current-head tester/reviewer acceptance.

## Packaging and rollback checks

The release merged manifest and unsigned intermediary bundle contain the production
browser activity/receiver, pinned Gecko child services, Gecko `omni.ja`, native
libraries, and the built-in Steam profile detector. Searches found zero release
matches for the debug prototype activity/process or issue-6 synthetic marker. The
non-Steam WebView path and dependency remain present for rollback.

At exact source `6b1f4b5`, `bundleRelease --rerun-tasks --console=plain` reached
`packageReleaseBundle` and then failed at `signReleaseBundle` because this checkout
has no release signing configuration. No signing key was requested, read, or
recorded. The unsigned intermediary artifacts are:

- `intermediary-bundle.aab`: 587,465,928 bytes; SHA-256
  `1B74FDA870113665D392207740500040F49F651630849E4407FCCD394631919C`
- `base.zip`: 530,541,757 bytes; SHA-256
  `C0AE47F3B16394A65037013E8F75EEAFE69DC4986193D8A3439AE59CC0A10F3B`

The signing failure is an expected environment limitation, not a successful release
build. Issue #8's gate artifact is the exact debug APK above.

## Acceptance notes and limitations

- Deterministic profile mapping and contained no-backup paths have unit coverage.
- The app-owned detector is fixed APK code, not a supported or replaceable browser
  extension. Its Steam origins, public fields, and app connection require explicit
  consent per profile before activation, installation, or navigation; denial grants
  nothing.
- Production browser storage isolation is directly exercised for cookies,
  localStorage, and IndexedDB. Extension and permission isolation relies on the same
  approved per-profile topology proven in issue #7; production CSFloat installation
  intentionally belongs to issue #10 and is not pulled into issue #8.
- Steam profile/avatar inputs are bounded and restricted to approved HTTPS Steam
  hosts/CDN values. Detection failure is recoverable and does not block browsing.
- The emulator uses a synthetic page. It does not prove real Steam authentication,
  Steam Guard behavior, live avatar markup, vendor-specific behavior, or physical
  device persistence. Those are the focused phone gate below.
- The child-process allowlist matches pinned GeckoView 153's merged manifest and must
  be reviewed when GeckoView is upgraded.
- No credentials, real Steam account identifiers, authentication pages, cookies,
  tokens, trades, payments, or browser-storage exports were used or captured.

The physical-device procedure is in
[Dagkan's campaign guide](../manual-testing/geckoview-campaign-dagkan.md#issue-8-production-steam-login-smoke-test).
Issue #9 remains blocked until that exact-build smoke test is reported PASS and its
sanitized evidence is accepted.
