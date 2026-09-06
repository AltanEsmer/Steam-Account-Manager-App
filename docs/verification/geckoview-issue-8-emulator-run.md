# Issue #8 production GeckoView verification receipt

Status: **Machine/emulator verification PASS at application/test source `0712562`; current-head independent review and focused physical-device Steam smoke test pending**

## Exact candidate

- Application/test source commit: `0712562f067087e08e32be5125be8326b38368f5`
- Branch: `codex/geckoview-campaign`
- Variant: debug, using the production Steam-to-GeckoView routing path
- GeckoView: `153.0.20260810162159`
- Preserved evidence directory:
  `C:\Users\esmer\AppData\Local\Temp\sam-gv8-0712562`
- `app-debug.apk`: 598,942,383 bytes; SHA-256
  `43E90ADB0CA7684FBDCBF5D4209A08EC1DC1276C224A775800E75007B42ACFB9`
- `app-debug-androidTest.apk`: 2,270,718 bytes; SHA-256
  `622BDE349CB6B86E61969EDD113DC05BFFB0AFD79ABF64CC064981E0AA343D29`
- Preserved full instrumentation XML: 4,745 bytes; SHA-256
  `9E7EA04AAC7BC74262F48B70A57ED05B670FA62E698954B0965451E01D331FF7`

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
continue** must synchronously record consent before Gecko activation. If the durable
write fails, the in-process value is restored to false, the disclosure remains open
with retry/cancel guidance, and no Gecko runtime, bridge, or page starts. Cancel or
Android Back closes the screen, loads nothing, starts no Gecko child process, and
stores no consent. A bridge retained from an earlier valid consent remains inert
until renewed consent is recorded. The async broadcast-to-Room update holds
Android's receiver lifecycle until completion. Existing account/session database
fields and schema are unchanged.

The production browser worker attempts graceful Gecko shutdown for three seconds and
the disposable prototype for six seconds, each within an eight-second total stop
budget. If Gecko does not exit, only the freshly revalidated exact captured worker
PID and process name may be terminated; allowlisted pinned-Gecko child processes are
then reaped and the empty process state must remain stable. The prototype's six-second
window lets a completed extension-state mutation flush before fallback termination.
This shared lifecycle repair was required by the full regression suite and does not
broaden the production migration scope.

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

Primary application/unit/lint build command at exact source `0712562`:

```powershell
$env:ANDROID_HOME = 'C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease --rerun-tasks --console=plain
```

Exit 0 in 57 seconds; 103/103 tasks completed. All 56 unit tests
passed with zero failures, errors, or skips. `assembleDebug`,
`assembleDebugAndroidTest`, and both lint variants passed. Debug lint reported zero
errors and 121 warnings; release lint reported zero errors and 53 warnings.

The final exact-source emulator command was:

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' connectedDebugAndroidTest --console=plain
```

It passed 27/27 instrumentation tests with zero failures, errors, or skips. Gradle
exited 0 in 17 minutes 53 seconds; the XML records 1054.493 test seconds. Result path:

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
and the clean tracked-worktree check passed at exact source `0712562`. An immediately
preceding run at `f2b3475` passed 26/27 but reproduced a prior prototype marker as
disabled after its interrupted reopen/stop cycle. A focused retry reproduced the
same failure, so it was repaired rather than treated as transient. The repaired
method then passed three consecutive runs (95.594, 109.575, and 103.694 seconds), and
the neighboring interrupted-screen and explicit-disable persistence cases passed
(92.203 and 69.422 seconds). Earlier failed runs exposed real graceful-shutdown and
test-foregrounding defects and were repaired; their results are superseded, not
counted as passing evidence. One earlier direct
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
- `6b1f4b5` — independently prove external foreground handoff and router recovery;
- `f2b3475` — fail closed when detector-consent persistence fails; and
- `0712562` — give completed prototype marker state time to flush within the same
  bounded shutdown contract.

The prior tester/reviewer result at `1f03397` was invalidated by these source repairs
and is not current acceptance evidence. Only the exact APK listed above may enter
the phone gate after current-head tester/reviewer acceptance.

## Packaging and rollback checks

The release merged manifest and unsigned intermediary bundle contain the production
browser activity/receiver, pinned Gecko child services, Gecko `omni.ja`, native
libraries, and the built-in Steam profile detector. Searches found zero release
matches for the debug prototype activity/process or issue-6 synthetic marker. The
non-Steam WebView path and dependency remain present for rollback.

At exact source `0712562`, `bundleRelease --rerun-tasks --console=plain` reached
`packageReleaseBundle` and then failed at `signReleaseBundle` because this checkout
has no release signing configuration. No signing key was requested, read, or
recorded. The unsigned intermediary artifacts are:

- `intermediary-bundle.aab`: 587,472,771 bytes; SHA-256
  `DE1CF49B064547EC1FFB7FFA652888965BE4366BA6BD60903067724ABDA0656D`
- `base.zip`: 530,542,133 bytes; SHA-256
  `FC1B1F7F4646ABE91BC4FEF6942B23D706CBC51FC997EA64A7EA66D9222F9441`

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
