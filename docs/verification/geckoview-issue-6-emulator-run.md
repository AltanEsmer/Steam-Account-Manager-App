# GeckoView issue #6 emulator run

This is the non-sensitive machine-verification record for
[issue #6](https://github.com/D4gkan/Steam-Account-Manager-App/issues/6). It
proves per-`(account, website)` Gecko profile isolation and lifecycle behavior
without using a Steam account. It does not claim authenticated tracking or an
issue #7 `GO` decision.

## Run metadata

| Field | Value |
| --- | --- |
| App source commit and build variant | `9095a4ddf886973b0c517833d83f1783ab191d9b`, `debug` |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, 598,877,061 bytes, SHA-256 `FE7BFCBCD851D8AE74A1201A3F0E443E2836E159D56C2E632EC761712E22ADE1`; preserved on the campaign host at `C:\Users\esmer\AppData\Local\Temp\sam-gv6-issue7\app-debug-FE7BFCBC.apk` |
| Upstream baseline APK | Commit `8de0e23443250326afbf087346b8c6a7eef302b7`, `debug`, 63,099,195 bytes, SHA-256 `352A9BDA1FDFA2E11942F7BC9C794FB00C1F27762A9C591274230CE6BBED8FD2` |
| GeckoView | `153.0.20260810162159`, stable Maven artifact |
| CSFloat | Official signed Firefox artifact, ID `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, version `5.17.0`, GeckoView signed state `2` |
| CSFloat artifact | `https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi`, 7,011,169 bytes, SHA-256 `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D` |
| Host | Windows 11 Pro 64-bit, version `10.0.26200`, build `26200`; Android Studio 2025.2.1 build `AI-252.25557.131.2521.14432022` |
| Android target | Dedicated AVD `Codex_GeckoView_Campaign_API_36`, serial `emulator-5580`, Android 16/API 36, `x86_64`, Google Play image |
| Emulator tooling | Android Emulator `36.2.12.0` build `14214601`; adb `36.0.0-13206524` |
| Final report timestamps | Unit tests 2026-09-02 17:03:44 UTC; lint 17:04:06 UTC; APK 17:11:14 UTC; instrumentation 17:15:41 UTC |
| Gate result | Issue #6 machine scenarios pass. Authenticated Steam state, live CSFloat tracking, physical-device behavior, navigation policy, and the issue #7 decision remain `NOT RUN`. |

## Selected isolation topology

Each `(account, website)` pair maps to a fixed-width opaque `gv_` identifier. The
identifier hashes length-prefixed UTF-8 input so neither account nor website text
appears in the profile name and ambiguous concatenations cannot collide. Each
identifier owns a persistent directory below the app's no-backup storage at
`gecko-prototype-profiles`.

Only one reusable `:gecko_prototype` worker and one Gecko runtime are resident at
a time. The worker starts with Gecko's `--profile <absolute profile path>` argument.
Changing the selected pair stops the prior worker, waits a bounded eight seconds for
the worker and recognized Gecko children to exit, then starts the new profile. It
fails closed if shutdown cannot be proven. This avoids keeping every possible
browser session resident while preserving each profile on disk.

Gecko `contextId` was rejected for this contract because extension installation,
storage, and enablement are runtime-wide. It cannot by itself isolate CSFloat state.
A persistent Gecko profile and a fresh runtime boundary are therefore required for
each selected pair.

The debug-only synthetic marker extension exercises extension storage and background
worker restart behavior. It is not present in release merged assets or manifests.
The production package remains the official, unmodified CSFloat artifact.

## Commands and results

Commands ran from the repository root. `ANDROID_SERIAL=emulator-5580` constrained
Gradle instrumentation to the dedicated AVD, and every direct adb command used
`-s emulator-5580`.

```powershell
$env:ANDROID_HOME='C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL='emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug lintDebug connectedDebugAndroidTest --rerun-tasks
# exit 0: BUILD SUCCESSFUL; 34 unit tests, 10 instrumentation tests,
# debug assembly, and lint all passed

# The complete instrumentation suite was then run twice consecutively by the
# implementation worker and once independently by the orchestrator.
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' connectedDebugAndroidTest --rerun-tasks
# exit 0 each time: 10/10 tests passed in 1m06s, 1m04s, and 1m06s

$adb='C:\Users\esmer\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5580 install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -s emulator-5580 shell pm clear com.steamaccountmanager.app.debug
& $adb -s emulator-5580 shell am start -W -n com.steamaccountmanager.app.debug/com.steamaccountmanager.app.prototype.GeckoViewPrototypeActivity
# all exit 0: exact APK installed, only the debug package was cleared, and the
# prototype cold-launched

& $adb -s emulator-5580 shell am force-stop com.steamaccountmanager.app.debug
& $adb -s emulator-5580 shell am start -W -n com.steamaccountmanager.app.debug/com.steamaccountmanager.app.prototype.GeckoViewPrototypeActivity
# both exit 0: a full package restart restored the selected A profile

& $adb -s emulator-5580 shell pidof com.steamaccountmanager.app.debug
& $adb -s emulator-5580 shell pidof com.steamaccountmanager.app.debug:gecko_prototype
# after the explicit prototype stop, only the router process was present;
# reopening created one worker plus Gecko child processes
```

Generated reports:

- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html` — 34 tests,
  zero failures, errors, or skips.
- Instrumentation: `app/build/reports/androidTests/connected/debug/index.html` —
  10 tests, zero failures, errors, or skips.
- Instrumentation XML:
  `app/build/outputs/androidTest-results/connected/debug/TEST-Codex_GeckoView_Campaign_API_36(AVD) - 16-_app-.xml`.
- Lint: `app/build/reports/lint-results-debug.html` — zero errors and 108 warnings.

## Acceptance-to-proof result

| Contract | Result | Proof |
| --- | --- | --- |
| Stable isolated profile identity | PASS | Unit tests cover deterministic IDs, ambiguous input pairs, Unicode, distinct empty-value encodings, and fixed-width redacted output. On-device A/B/A switching restored the correct engine-observed marker for A. |
| Cookies, local storage, and IndexedDB | PASS | Instrumentation writes distinct engine-observed values for A and B, switches A → B → A, recreates the activity, closes/reopens the screen, and restarts the worker. A separate exact-serial adb check force-stops/restarts the app. Values restore only in their owning profile. |
| Extension storage | PASS | Debug marker version `1.4` writes distinct native-message-observed values in each profile. A restores its value after B, activity recreation, screen close/reopen, worker recreation, and full app restart. |
| Official CSFloat installed/enabled state | PASS | A and B independently began absent, independently displayed the exact install consent, and independently enabled the same signed ID/version. Disabling or uninstalling A did not change B. Explicit enable restored only A. Reinstalling A repeated consent; denial left A absent and a later accepted retry restored only A. |
| Repeated switching | PASS | Instrumentation and the manual emulator matrix switch repeatedly without cross-profile marker exposure. The runtime/process design keeps one active profile resident rather than all stored profiles. |
| Activity and screen lifecycle | PASS | Activity recreation and browser screen close/reopen restored A's engine-observed page and extension markers without a duplicate install prompt. |
| Worker process recreation | PASS | Explicit stop reported `GV-WORKER-STOPPED` only after the worker and recognized Gecko children were absent. Reopening restored A from the same persistent profile. |
| Full app restart | PASS | Force-stop left no package process. A cold router launch retained the selected pair, and reopening the worker restored A's engine and CSFloat state. |
| Failure and recovery | PASS | Worker shutdown has an eight-second bound and fails closed. Marker native-message reconnection is bounded to 30 attempts at two-second intervals. Install denial remains absent and retry is explicit. |
| Release exclusion | PASS | Release merged assets contain no issue #6 marker extension, and the release merged manifest contains no prototype marker or prototype process entry. Release packaging remains unverified because the local release signing store is unavailable. |

## A/B and lifecycle matrix

| Transition | A observed state | B observed state | Result |
| --- | --- | --- | --- |
| Clean A | A page/storage marker; CSFloat absent | Not active | PASS |
| Install and enable A | A markers; CSFloat enabled | Not active | PASS |
| A → B | Not resident | B marker; CSFloat absent | PASS |
| Install and enable B | Not resident | B markers; CSFloat enabled | PASS |
| B → A | A markers; CSFloat enabled | Not resident | PASS |
| Disable A, inspect B | A disabled | B remains enabled | PASS |
| Re-enable then uninstall A, inspect B | A absent | B remains enabled | PASS |
| Reinstall A | Consent denial leaves A absent; accepted retry restores A | B remains enabled | PASS |
| Activity recreation | A markers and CSFloat enabled restore | Not resident | PASS |
| Screen close/reopen | A markers and CSFloat enabled restore | Not resident | PASS |
| Worker stop/reopen | No worker/recognized child, then A restores | Not resident | PASS |
| Full package restart | A selection, markers, and CSFloat state restore | Not resident | PASS |

## Representative process and size observations

These are point samples from `adb shell dumpsys meminfo`, not benchmarks or release
performance claims. Stored profiles did not cause the process count to grow.

| State | Processes | Total proportional set size |
| --- | ---: | ---: |
| A active after restart and more than six cross-profile switches | 7 | 559,628 KiB |
| B active after the isolation matrix | 7 | 618,323 KiB |
| Router only after a proven worker stop | 1 | 126,366 KiB |
| A active after worker recreation | 7 | 633,374 KiB |

The issue #6 debug APK is 535,777,866 bytes larger than the upstream-main debug
baseline. This is a fat debug artifact containing GeckoView native binaries; it is
not a release-download or installed-size claim.

## Reviewable evidence and privacy

Every PNG is an original 1080×2400 emulator capture. The captures and retained UI
hierarchy XML were visually inspected before commit. They contain only the debug
prototype, public fixture text, fixed `GV-*` status labels, and the official CSFloat
consent surface. They contain no credentials, Steam Guard codes, QR login material,
cookies, tokens, account identifiers, inventory, trade details, payment information,
or authentication UI.

- [A with official CSFloat enabled](evidence/issue-6/gv6-a-enabled.png): SHA-256
  `342699F7FFB37B0E8CC3F379B37A7952688037C6BFFC6B6A2B0295098B3BDD75`
- [B remains absent](evidence/issue-6/gv6-b-absent.png): SHA-256
  `9D591596DFDE4D1B7C6B49F4F849D6D68CFB7EFC7699F3121CB69A892DDCDD3E`
- [A repeats consent on reinstall](evidence/issue-6/gv6-reinstall-consent.png):
  SHA-256 `F59A5A2775CF3AF123981456BE6923D44A7AFC8EF4E1EAE4E6215D1EBF817F8B`
- [Worker fully stopped](evidence/issue-6/gv6-worker-stopped.png): SHA-256
  `1F90D0AD09AE1D011FEC2D45668EF3261784DC4FAB9BEFB880997620ABC8500C`
- [A restored after worker recreation](evidence/issue-6/gv6-worker-restored.png):
  SHA-256 `532475589CB79CB88AF76A641A2207E60C80E58EE7592BA8B97624991C5C3FE2`
- [A restored after full app restart](evidence/issue-6/gv6-app-restart-restored.png):
  SHA-256 `AA4CC68FA00B2594399773CDC94A557107B662CB666897F1F6357DFCBC0A0D5D`
- [A synthetic engine and extension markers](evidence/issue-6/gv6-a-synthetic-markers.png):
  SHA-256 `177498974B00356AEF676C7A7F7D890B4F8D2FF755637F25A99FCA422D43B9BD`

The XML evidence in `evidence/issue-6/` records fixed UI labels for A/B extension
state, disable/uninstall isolation, lifecycle restoration, worker shutdown, and the
synthetic marker result. It contains accessibility attributes such as
`password="false"`; those attributes are not captured credentials.

The debug marker's source checksums are
`18C2080D9B272CF887563C95780312E4AF6E715CB37D543DC5688C0A9BE2D579`
for `background.js` and
`E735B2DDBC125C63A02AF5617B3EEE165257B4A2B1457E72828387C545E7D1D8`
for `manifest.json`.

## Repair history

The bounded issue repair budget was fully used:

1. Explicit stop initially left a Gecko crash helper. Shutdown now waits for the
   worker and recognized Gecko children before acknowledging completion.
2. Hosting the fixture in the persistent router process allowed Android to freeze the
   server while the worker was active. The loopback fixture moved into the worker.
3. Full-suite contention exposed a too-short native marker reconnection window. The
   bounded window increased to 30 two-second attempts and marker version `1.4`.

After the third repair, two consecutive writer runs and one orchestrator run passed
the complete 10-test instrumentation suite. No further source repair is available
for issue #6 without declaring the issue budget exhausted.

## Known limitations and non-claims

- This run used one x86_64 emulator and no Steam account.
- Synthetic markers prove browser-engine and extension-storage boundaries. They do
  not prove authenticated Steam identity, live CSFloat injection, offers, alarms,
  or background tracking.
- Physical-device screen, activity, browser-process, and full-app recreation remain
  required by issue #7.
- Navigation allowlisting, blocked external handoff, and full browser controls are
  not implemented by the prototype. GV-15 must be tested honestly at the issue #7
  gate; a failure is a `NO-GO`, not a reason to weaken the contract.
- Representative memory values are noisy point samples. Release packaging and
  release installed size were not measured because the local signing store is absent.
- This record is not an ADR amendment and does not authorize issue #8. Production
  work remains blocked until issue #7 has an official acknowledged `GO`.
