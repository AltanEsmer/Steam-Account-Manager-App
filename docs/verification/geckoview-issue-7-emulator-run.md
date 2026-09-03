# Issue #7 emulator verification receipt

Status: **INCOMPLETE / STAGNATED — latest machine resume failed repeatability**

## Latest resume (2026-09-03)

Current source checkpoint: `1dc8802bc91e3f232580c607b5b114d36f12113e`.
The primary's first full instrumentation run passed 16/16; its repeat passed only
14/16, with initial engine-load and synthetic extension-marker failures. A single
passing run is not current acceptance evidence. Issue #7 has no `GO`; production
issue #8 and subsequent migration work remain blocked.

Narrowed diagnostics on that source reproduced a later reinstall stall: installation
reported enabled 1.5, but the synthetic background had no `script-enter` phase and
the marker remained waiting. Earlier worker restarts did enter the background and
return results. This narrows the symptom, but does not establish a safe permanent
fix. Temporary diagnostic probes and console logging were removed.

A subsequent uncommitted experiment restarted the background after install, removed
the redundant post-enable install, and strengthened the enabled-state test wait.
Its instrumented narrow run passed once, but diagnostics-free validation failed in
116.596 seconds: after A's uninstall and an A→B→A switch, A was enabled when absence
was required (test line 245 in the experimental source). The experiment was rejected
and its three changes removed; source is restored to the checkpoint above. This is
not a passing current-head receipt or permission to proceed with device testing.

Safe local diagnostic artifacts (not committed full logs):

- Original narrowed result: `C:\Users\esmer\AppData\Local\Temp\gv7-marker-js-diag-result.log`
- Fixed-phase background evidence: `C:\Users\esmer\AppData\Local\Temp\gv7-marker-js-diag-phases.log`
- Instrumented experiment: `C:\Users\esmer\AppData\Local\Temp\gv7-marker-install-only-probe-result.log`
- Failed diagnostics-free experiment: `C:\Users\esmer\AppData\Local\Temp\gv7-marker-clean-candidate-result.log`

All runs used dedicated `emulator-5580`; no physical-device proof was collected.
The historical receipt below describes `b81eeab` only. Its APK, test counts, and
screenshots are historical evidence, not current authorization to run that APK.

## Historical b81eeab receipt — partial emulator pass only

This receipt records the autonomous emulator portion of the issue #7 compatibility
gate. It does not record an official `GO`. Steam authentication, authenticated CSFloat
injection/tracking, the two-live-account matrix, and the supported physical-device run
still require human testing before production issue #8 may begin.

## Exact build

- Source commit: `b81eeab10ee0554850f51ff9702052ce96ddba19`
- Starting campaign checkpoint: `099926bb5da2f2ef292e14757a8a54a7c9029bd7`
- Variant: debug
- Built APK: `app/build/outputs/apk/debug/app-debug.apk`
- Preserved APK: `C:\Users\esmer\AppData\Local\Temp\sam-gv7-navigation-repair-b81eeab\app-debug-4A5FF6D7.apk`
- Size: 598,810,380 bytes
- SHA-256: `4A5FF6D78B1B9FF75343B2D0681FF473F29A935C811FE3B5DC63F1EE99610755`
- GeckoView: `153.0.20260810162159`
- CSFloat: official signed Firefox artifact 5.17.0, ID
  `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`
- XPI SHA-256: `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D`

## Environment

- Host: Windows 11 build 26200
- Android Studio: 2025.2.1, build AI-252.25557.131.2521.14432022
- Dedicated AVD: `Codex_GeckoView_Campaign_API_36`
- Exact adb serial: `emulator-5580`
- Android: 16 / API 36
- ABI: x86_64
- Image: Google Play
- Test window: 2026-09-03 17:34–17:45 UTC

The AVD was started without wiping it. The debug app alone was cleared for the clean
launch. Every device command used `adb -s emulator-5580`; the personal
`Medium_Phone_API_36.0` AVD and all physical devices were untouched.

## Automated checks

The exact source commit passed this single command with exit code 0:

```powershell
$env:ANDROID_HOME = 'C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug lintDebug connectedDebugAndroidTest processReleaseMainManifest mergeReleaseAssets --rerun-tasks
```

Results:

- 42/42 unit tests passed.
- 16/16 instrumentation tests passed on the dedicated AVD.
- `assembleDebug` passed.
- `lintDebug` passed with 0 errors and 113 warnings.
- Release merged-manifest search found no prototype activity or worker.
- Release merged-assets search found no synthetic marker/prototype asset.
- `git diff --check` passed.

Instrumentation result:
`app/build/outputs/androidTest-results/connected/debug/TEST-Codex_GeckoView_Campaign_API_36(AVD) - 16-_app-.xml`.

## Credential-free emulator scenarios

- Clean router launch displayed `GV-ROUTER-READY`, A/B selectors, reopen, router
  recreation, and worker-stop controls. This is emulator proof for GV-01.
- Slot A displayed the fixed cookie/local-storage/IndexedDB/history marker
  `GV6|slot=A|cookie=A|local=A|idb=A|nav=A-history`.
- The synthetic extension returned
  `GV-MARKER-RESULT slot=A prior=A current=A` after its explicit fixture install.
- The complete instrumentation class exercised A/B isolation, revocation/restoration,
  activity recreation, screen reopen, worker stop/reopen, router recreation, and the
  retained marker upgrade path without a failure.
- The navigation policy allowed the exact loopback fixture and configured HTTPS Steam
  and Steam-auth hosts. Unit tests at this captured build rejected deceptive suffixes,
  unrelated hosts, insecure remote HTTP, and non-web destinations; later regression
  coverage adds user-info and malformed inputs without retroactively expanding this run.
- The deterministic unrelated destination stayed out of GeckoView. The app remained
  foreground, retained the A page marker, displayed the fixed redacted
  `GV-NAVIGATION-BLOCKED` message, and offered **Stay here** and
  **Open in external browser**. No external browser opened automatically.
- Back, Forward, and Reload controls were present and their GeckoSession calls compiled;
  live allowed-page history behavior remains in the human matrix.

Representative active-state memory after the blocked-navigation scenario comprised
seven app-owned processes with summed PSS of 410,523 KiB. This point-in-time emulator
measurement is not a release, battery, or physical-device performance claim.

## Reviewable evidence

- `evidence/issue-7/gv7-emulator-router-ready.png` — SHA-256
  `930AD08E2FB9190D47AB042F100EEEE231E20E0A27E8A06AED56376637D1D585`
- `evidence/issue-7/gv7-emulator-router-ready.xml` — SHA-256
  `0EE5BEF0EAA365BA799A62CC71FD5F7B52C1B826C39CFC756229FCDBBDB3E9CB`
- `evidence/issue-7/gv7-emulator-navigation-blocked.png` — SHA-256
  `D7B484F7F052D03DB71A2B6CD0E7150B1F61329360B7015292584FDC747CEDB0`
- `evidence/issue-7/gv7-emulator-navigation-blocked.xml` — SHA-256
  `2D88B0C5FBE392D727E9974439777243EEE71576CD7929EA4B6AFC150CA0BFAF`

The dedicated AVD's status-bar icons and clock were disabled during capture and the
policy was restored immediately afterward, so both screenshots exclude the status
bar. Both screenshots were visually inspected. Both XML dumps contain zero password nodes.
The only cookie-like text is the fixed synthetic `cookie=A` marker. No Steam account,
authentication, trade, payment, cookie/token, QR, or Steam Guard content was captured.

## Human proof still required

The current debug prototype is single-window: approved new-window links are routed
into the existing selected GeckoSession with the navigation policy checked again.
This machine preparation does not prove authentication flows that depend on
`window.opener`, `postMessage`, or `window.close`; those semantics remain human-gate work.

The following are deliberately **NOT RUN** and cannot be inferred from this receipt:

- Steam authentication or Steam Guard on the emulator;
- authenticated CSFloat injection and official tracking/alarm behavior;
- two live Steam-account identities and their revoke/restore/restart matrix;
- allowed live authentication redirects and actual explicit external-browser handoff;
- the complete GV-01–GV-16 run on a supported Android 9/API 28+ physical device;
- physical-device screen, activity, browser-process, and full-app recreation;
- reviewer acknowledgement, maintainer acknowledgement, ADR update, or official issue
  #7 `GO`.

Do not share passwords, Steam Guard codes, QR login screens, cookies, tokens, account
names, trades, payment information, or unredacted authentication screenshots.

Production issues #8–#12 remain blocked until the remaining human evidence passes,
the independent tester and reviewer accept the complete record, ADR-0001 records only
proven decisions, and issue #7 receives its official acknowledged `GO`.
