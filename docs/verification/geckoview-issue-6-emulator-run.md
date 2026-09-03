# GeckoView issue #6 emulator run

This is the non-sensitive machine-verification record for
[issue #6](https://github.com/D4gkan/Steam-Account-Manager-App/issues/6). It
proves per-`(account, website)` Gecko profile isolation and lifecycle behavior
without using a Steam account. It does not claim authenticated tracking or an
issue #7 `GO` decision.

## Run metadata

| Field | Value |
| --- | --- |
| App source commit and build variant | `bbe36da1aea680354750bd5ab2b7bf0fcc6d763d`, `debug` |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, 598,777,460 bytes, SHA-256 `1AD629D1E103A8568F203A92DB465A2236920A884EB51E04157AC970224D92CE`; preserved on the campaign host at `C:\Users\esmer\AppData\Local\Temp\sam-gv6-issue7-cycle5-final\app-debug-1AD629D1.apk` |
| Upstream baseline APK | Commit `8de0e23443250326afbf087346b8c6a7eef302b7`, `debug`, 63,099,195 bytes, SHA-256 `352A9BDA1FDFA2E11942F7BC9C794FB00C1F27762A9C591274230CE6BBED8FD2` |
| GeckoView | `153.0.20260810162159`, stable Maven artifact |
| CSFloat | Official signed Firefox artifact, ID `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, version `5.17.0`, GeckoView signed state `2` |
| CSFloat artifact | `https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi`, 7,011,169 bytes, SHA-256 `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D` |
| Host | Windows 11 Pro 64-bit, version `10.0.26200`, build `26200`; Android Studio 2025.2.1 build `AI-252.25557.131.2521.14432022` |
| Android target | Dedicated AVD `Codex_GeckoView_Campaign_API_36`, serial `emulator-5580`, Android 16/API 36, `x86_64`, Google Play image |
| Emulator tooling | Android Emulator `36.2.12.0` build `14214601`; adb `36.0.0-13206524` |
| Final report timestamps | Unit tests 2026-09-03 09:40:21 UTC; APK 09:40:26 UTC; instrumentation 09:39:52 UTC; lint 09:40:36 UTC; emulator matrix completed 09:52:49 UTC |
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
the worker and all app-owned colon subprocesses to exit, then starts the new profile. It
fails closed if shutdown cannot be proven. This avoids keeping every possible
browser session resident while preserving each profile on disk.

Profiles remain below `noBackupFilesDir/gecko-prototype-profiles` while their browser
session exists. Cleanup may delete only the owning profile after that account/website
session is explicitly removed or revoked, after its worker has stopped, and after
contained-path validation. It must never bulk-clear other profiles. This prototype
demonstrates persistence but does not yet expose session-removal or cleanup UI.

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
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug lintDebug connectedDebugAndroidTest processReleaseMainManifest mergeReleaseAssets --rerun-tasks
# exit 1: all unit/build/lint/release inputs and the four new router-race tests
# passed; the legacy synthetic marker reported ENABLED but its native result message
# did not reconnect before the test timeout

.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' connectedDebugAndroidTest --rerun-tasks
# exit 0 after a verified fresh Gradle install: BUILD SUCCESSFUL in 1m52s;
# 14 instrumentation tests passed with zero failures, errors, or skips

.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug lintDebug processReleaseMainManifest mergeReleaseAssets --rerun-tasks
# exit 0: BUILD SUCCESSFUL in 33s; 39 unit tests, debug assembly, lint, and
# release-exclusion inputs passed

$adb='C:\Users\esmer\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5580 install -r C:\Users\esmer\AppData\Local\Temp\sam-gv6-issue7-cycle5-final\app-debug-1AD629D1.apk
& $adb -s emulator-5580 shell am start -W -n com.steamaccountmanager.app.debug/com.steamaccountmanager.app.prototype.GeckoPrototypeRouterActivity
# exit 0: the exact APK installed and the exported router cold-launched. Select
# Open synthetic slot A; the non-exported worker can be launched only with a slot.

& $adb -s emulator-5580 shell am force-stop com.steamaccountmanager.app.debug
& $adb -s emulator-5580 shell am start -W -n com.steamaccountmanager.app.debug/com.steamaccountmanager.app.prototype.GeckoPrototypeRouterActivity
# both exit 0: select Reopen selected slot in the router. The cycle-5 restart
# evidence records the selected A profile and its enabled CSFloat state restored.

& $adb -s emulator-5580 shell ps -A
# after Stop worker process reported GV-WORKER-STOPPED, filtering the output to
# com.steamaccountmanager.app.debug returned only the router process

& $adb -s emulator-5580 logcat -d -t 200 'AndroidRuntime:E' 'GeckoRuntime:E' 'GeckoSession:E' 'GeckoView:E' '*:S'
# exit 0 with no matching error records
```

Every direct adb command in the final cycle-5 acceptance run used `-s emulator-5580`
and returned
exit code 0. UI actions were driven with `adb shell input`; each result was checked
with a fresh `uiautomator dump` and a sanitized screenshot. The current-source run
observed A and B independently absent, independently consented and enabled, A
disabled while B remained enabled, A re-enabled and uninstalled while B remained
enabled, A denial remaining absent, accepted retry restoring A, complete worker
shutdown, worker reopen, and full package restart through the exported router.
It also exercised rapid B-to-A selection, B-to-Stop, Stop-to-Reopen, and pending
B-to-router-recreation ordering. The final case retained A, suppressed stale B
authorization, and removed every app-owned child process within the existing bound.

Before the successful instrumentation retry, `adb uninstall` returned
`DELETE_FAILED_INTERNAL_ERROR` for both test and app packages because the failed
Gradle run had already removed them; `pm list packages` confirmed neither package
was installed, and the next Gradle task performed a fresh install. Two exact-source
diagnostic runs observed the same synthetic marker native-message reconnection
timeout while the extension state was visibly enabled. The second permitted retry
passed all 14 tests; no source or timing workaround was added.

Generated reports:

- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html` — 39 tests,
  zero failures, errors, or skips.
- Instrumentation: `app/build/reports/androidTests/connected/debug/index.html` —
  14 tests, zero failures, errors, or skips.
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
| Repeated switching | PASS | Instrumentation and the manual emulator matrix switch repeatedly without cross-profile marker exposure. Latest selection supersedes an older pending switch, explicit Stop supersedes a pending selection, Reopen supersedes a pending Stop, and destroyed routers cannot deliver stale authorization. The runtime/process design keeps one active profile resident rather than all stored profiles. |
| Activity and screen lifecycle | PASS | Activity recreation and browser screen close/reopen restored A's engine-observed page and extension markers without a duplicate install prompt. |
| Worker process recreation | PASS | Explicit stop reported `GV-WORKER-STOPPED` only after the worker and all app-owned colon subprocesses were absent. Reopening restored A from the same persistent profile. Recreating the router during a pending switch invalidated authorization while allowing its bounded process cleanup to finish. |
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
| Pending B → latest A reopen | A restores; no B marker appears | Not resident | PASS |
| Pending B → explicit Stop | Router retains A; no child process remains | Not resident | PASS |
| Pending Stop → A reopen | A restores after proven worker death | Not resident | PASS |
| Pending B → router recreation | Router retains A; stale B is not authorized; no child process remains | Not resident | PASS |
| Disable A, inspect B | A disabled | B remains enabled | PASS |
| Re-enable then uninstall A, inspect B | A absent | B remains enabled | PASS |
| Reinstall A | Consent denial leaves A absent; accepted retry restores A | B remains enabled | PASS |
| Activity recreation | A markers and CSFloat enabled restore | Not resident | PASS |
| Screen close/reopen | A markers and CSFloat enabled restore | Not resident | PASS |
| Worker stop/reopen | No worker/app-owned child, then A restores | Not resident | PASS |
| Full package restart | A selection, markers, and CSFloat state restore | Not resident | PASS |

## Representative process and size observations

These are point samples from `adb shell dumpsys meminfo`, not benchmarks or release
performance claims. Stored profiles did not cause the process count to grow.

| State | Processes | Total proportional set size |
| --- | ---: | ---: |
| A active after the current-head isolation matrix | 7 | 593,032 KiB |
| Router only after a current-head proven worker stop | 1 | 73,413 KiB |

The issue #6 debug APK is 535,678,265 bytes larger than the upstream-main debug
baseline. This is a fat debug artifact containing GeckoView native binaries; it is
not a release-download or installed-size claim.

## Reviewable evidence and privacy

Every PNG is an original 1080×2400 emulator capture. The first seven captures below
record the pre-cycle-4 baseline at `9095a4d`; the first `gv6-cycle4-*` group records
the intermediate cycle-4 source commit `9db111a`; and the `gv6-cycle4-final-*` group
records source commit `8384c77`. Those groups are historical. Only the
`gv6-cycle5-final-*` group at `bbe36da` is current proof.
All retained PNGs were visually inspected, and the matching UI hierarchy XML was
reviewed for sensitive fields before commit. They contain only the debug
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
- [Cycle 4 A enabled after repeated consent](evidence/issue-6/gv6-cycle4-a-final-enabled.png):
  SHA-256 `72E65430D4DF12C38BEBAC7DCBDCED986C18AEBC8B18FD30BABE306499D69B1A`
- [Cycle 4 B remains enabled after A is disabled](evidence/issue-6/gv6-cycle4-b-after-a-disable.png):
  SHA-256 `536CAAA59F8408E0D1FE778122DF4B63CA231608C62A862A25BA8C951DA172C0`
- [Cycle 4 revoked CSFloat state](evidence/issue-6/gv6-cycle4-revoked-state.png):
  SHA-256 `7DD07A883EB0280FBD234994B5A92AD2A9E6B1AD1CD3D6D32D12B5EBEE20C35A`
- [Cycle 4 revoked action is unavailable](evidence/issue-6/gv6-cycle4-revoked-action.png):
  SHA-256 `8F6073FCF680EF82C3DCBA057A3D875E604E9ACE9FF79179434F1564FB2451EF`
- [Cycle 4 complete worker stop](evidence/issue-6/gv6-cycle4-worker-stopped.png):
  SHA-256 `C23BA1D3BB158D4207A682FFCC4526748F800AD8757EF95654295115D0702703`
- [Cycle 4 full restart restoration](evidence/issue-6/gv6-cycle4-full-restart-restored.png):
  SHA-256 `4976335A87DED855AA45A831BEDD5649137BD183FAC40298574E2124C7086429`
- [Cycle 4 repeated install consent](evidence/issue-6/gv6-cycle4-consent.png):
  SHA-256 `4DE383BCBADC3020A5E554C0F7913A6B9C5415993BCD0BA6745160A2B0B5091C`
- [Final cycle 4 install consent](evidence/issue-6/gv6-cycle4-final-consent.png):
  SHA-256 `E724153A2FF55810BF1968B88D2F90138811AE63369B7B52EA4090CD967CC7FE`
- [Final cycle 4 A enabled](evidence/issue-6/gv6-cycle4-final-a-enabled.png):
  SHA-256 `AA8CBDDD9108EF7D280551715D47623B88484F4428C7E7DBC6E822D527E3AC96`
- [Final cycle 4 B remains independently enabled](evidence/issue-6/gv6-cycle4-final-b-isolated.png):
  SHA-256 `A5EDCF8EE163042C5E5B31D792770AAD749F0C048087950E0DCD602977752444`
- [Final cycle 4 revoked CSFloat state](evidence/issue-6/gv6-cycle4-final-revoked-state.png):
  SHA-256 `EFEE11F733AE8A876C9EEA929A75645BD97E887863994EF3A468E0A1D950C9D0`
- [Final cycle 4 revoked action controls](evidence/issue-6/gv6-cycle4-final-revoked-action.png):
  SHA-256 `29DAD572A7ED89A141992E60700AAE16033D857FE7F7101616E46BA37CA7ABFC`
- [Final cycle 4 complete worker stop](evidence/issue-6/gv6-cycle4-final-worker-stopped.png):
  SHA-256 `0ACEABC574EB29EAB7CA27A8E417275229875E964D0AAF4E8C8F44960EB435C9`
- [Final cycle 4 full restart restoration](evidence/issue-6/gv6-cycle4-final-full-restart.png):
  SHA-256 `980AF88DDB5562B178B98BEF029D348A1F0643B3B7E2B4AC8998682C9B3C56B8`
- [Final cycle 5 exact consent](evidence/issue-6/gv6-cycle5-final-consent.png):
  SHA-256 `15308D33F00A17FBBABE07A37D846FB02F5623922D8BA28898D05A4367CFD9D3`
- [Final cycle 5 A enabled](evidence/issue-6/gv6-cycle5-final-a-enabled.png):
  SHA-256 `99DF3ADEAC9340BEC1165B90D386EB4DF6D92A852F9B262E05EF3136C4E50A15`
- [Final cycle 5 latest selection wins](evidence/issue-6/gv6-cycle5-final-latest-wins.png):
  SHA-256 `972D938B504A387C424C3933F68795927E735F7BFBA463BECCECD54C22846CFF`
- [Final cycle 5 Stop supersedes selection](evidence/issue-6/gv6-cycle5-final-stop-supersedes.png):
  SHA-256 `859145742881937A57BCAD1BD518C29359AC77BE19E6DBE504B603A8750DB78B`
- [Final cycle 5 router recreation cleanup](evidence/issue-6/gv6-cycle5-final-router-recreate.png):
  SHA-256 `EEB159412C763CFDE7900015CD70357A4DD702C754AF124F9E75BE7BDC874914`
- [Final cycle 5 B remains enabled after A uninstall](evidence/issue-6/gv6-cycle5-final-b-after-a-uninstall.png):
  SHA-256 `F8F08A2E650CFFD8DCBCD441C648E1B0F2BA0D10A1AFB8C87A4C4C65D2E5CBBC`
- [Final cycle 5 revoked action fails closed](evidence/issue-6/gv6-cycle5-final-a-revoked-action.png):
  SHA-256 `CFBD372497E7C1BC0FD69E14DBC5BEB1F0B8288455C70C3E7E4F4672392127E6`
- [Final cycle 5 denied install remains absent](evidence/issue-6/gv6-cycle5-final-a-denied.png):
  SHA-256 `D7D7331327C83F8823886582A00C6E7A23347F326BE6D3C13EAEB36D7E0B5925`
- [Final cycle 5 full restart restoration](evidence/issue-6/gv6-cycle5-final-full-restart.png):
  SHA-256 `92E0C16CA18A519F45E1A47EDD2CA567C440C36D19DDDCBA9E00049DF7C1CF95`

The current cycle-5 XML evidence in `evidence/issue-6/` records fixed UI labels for
A/B extension state, all four ordering races, disable/uninstall isolation, denial
and retry, activity and screen recreation, bounded worker shutdown, and full restart.
It contains accessibility attributes such as
`password="false"`; those attributes are not captured credentials.

The debug marker's source checksums are
`18C2080D9B272CF887563C95780312E4AF6E715CB37D543DC5688C0A9BE2D579`
for `background.js` and
`E735B2DDBC125C63A02AF5617B3EEE165257B4A2B1457E72828387C545E7D1D8`
for `manifest.json`.

## Repair history

The original three-cycle repair budget was exhausted:

1. Explicit stop initially left a Gecko crash helper. Shutdown now waits for the
   worker and every app-owned colon subprocess before acknowledging completion.
2. Hosting the fixture in the persistent router process allowed Android to freeze the
   server while the worker was active. The loopback fixture moved into the worker.
3. Full-suite contention exposed a too-short native marker reconnection window. The
   bounded window increased to 30 two-second attempts and marker version `1.4`.

After the third repair, two consecutive writer runs and one orchestrator run passed
the complete 10-test instrumentation suite. The user then explicitly authorized
exceptional repair cycle 4, which corrected fail-closed CSFloat action/tracking state
after disable, uninstall, a non-enabled refresh, mutation failure, or extension-list
failure and expanded the process barrier to cover every app-owned Gecko child process
while preserving graceful worker shutdown.

The user then explicitly authorized exceptional repair cycle 5. A red real-router
test reproduced stale profile authorization when B was requested and A was reopened
before shutdown completed. One coordinator generation now owns profile selection,
explicit Stop, timeout, and process-death completion, so only the latest exact request
can authorize a worker. Tests then exposed and fixed B-to-Stop and Stop-to-Reopen
ordering. Final emulator review found that router recreation correctly invalidated
authorization but canceled cleanup, leaving a crash helper. The final red test and
fix retain authorization invalidation while allowing only the already-started bounded
subprocess cleanup to finish. Marker install failure paths also release their in-flight
guard so recovery remains possible. No further issue #6 repair cycle is currently
authorized.

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
