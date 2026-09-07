# GeckoView issue #11 emulator verification

## Build and environment

- Source/test checkpoint: `01e41faadd51619ff8a5506df4f6c3c129c26815`
- Production implementation checkpoint: `01e41faadd51619ff8a5506df4f6c3c129c26815`
- Starting checkpoint: `1a112ab71cd0ff41caad7164a65d1acb9949f20c`
- Verification timestamp: `2026-09-07T19:03:27+02:00`
- Host: Windows 11 Pro `10.0.26200`, build 26200
- Android Studio: 2025.2.1, `AI-252.25557.131.2521.14432022`
- Variant: `debug`
- Dedicated AVD: `Codex_GeckoView_Campaign_API_36`, serial `emulator-5580`,
  Android 16 / API 36, `sdk_gphone64_x86_64`, `x86_64`
- GeckoView: `153.0.20260810162159`
- CSFloat: official signed 5.17.0, ID
  `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, observed signed state `2`
- App APK: 599,601,742 bytes, SHA-256
  `BB78F6B0927008D2D292EA99CF092C44F138863CF416495A031C1E815F2DA2F2`
- Test APK: 2,421,623 bytes, SHA-256
  `B54ECB1A96FEDBCFE0CCA77526DF99B58C21B9C3F514FC3C093C24178F0C1A7C`

Every device command used `adb -s emulator-5580`. Installation initially lacked
temporary space, so only the prior scoped app and test packages were removed before
installing these APKs; no unrelated package or emulator state was changed.
The app used Android incremental installation because the universal APK exceeded the
package manager's temporary-space reserve. The earlier pulled `base.apk` matched its
local candidate byte-for-byte; both bounded-repair runs used clean incremental installs
of the exact final APK recorded above.

## Commands and results

```text
gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL (48s)

gradlew :app:assembleDebug :app:assembleDebugAndroidTest
BUILD SUCCESSFUL (1m01s)

gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest
BUILD SUCCESSFUL (1m09s)

am instrument ...#productionCsfloatCanBeDisabledEnabledUninstalledReinstalledAndKeepsOtherSessionAbsent
OK (1 test), 93.737s

am instrument ... five focused production CSFloat methods
OK (5 tests), 272.413s

post-repair amended lifecycle tracer, consecutive run 1
OK (1 test), 85.109s

post-repair amended lifecycle tracer, consecutive run 2
OK (1 test), 82.363s

post-repair five focused production CSFloat methods
OK (5 tests), 115.656s

clean-install fixed-profile default phase
OK (1 test), 36.953s

adb -s emulator-5580 shell am force-stop com.steamaccountmanager.app.debug

same method with -e issue11RestartPhase verify-after-force-stop
OK (1 test), 8.073s

bounded-inspection repair, clean exact install, lifecycle run 1
OK (1 test), 44.687s

bounded-inspection repair, second clean exact install, lifecycle run 2
OK (1 test), 44.640s

gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease --rerun-tasks
BUILD SUCCESSFUL (1m08s), 103/103 tasks; 70/70 unit tests; zero lint errors

fresh exact install; pulled app and test base APKs
local/device size and SHA-256 matched for both APKs

full instrumentation, first post-repair run
33/34 passed; the pre-existing navigation-gate test missed its Back readiness timeout

isolated navigationGateExposesControlsAndBlocksWithoutAutomaticHandoff
OK (1 test), 13.014s

clean exact reinstall; diagnosed full-suite retry
OK (34 tests), 573.607s

clean exact reinstall; fixed-profile default phase
OK (1 test), 48.660s

adb -s emulator-5580 shell am force-stop com.steamaccountmanager.app.debug

same method with -e issue11RestartPhase verify-after-force-stop
OK (1 test), 12.203s

durable restoration fence, clean exact install, lifecycle run 1
OK (1 test), 51.904s

durable restoration fence, second clean exact install, lifecycle run 2
OK (1 test), 51.074s
```

The five-test run covered install denial and acceptance, callback-derived required
permission display, unavailable/available/opened popup ownership, fail-closed denied
verification, cleanup failure/retry, exact disable/enable, exact uninstall/reinstall,
pinned update denial, and A/B profile isolation. The revocation tracer also stopped
and recreated the dedicated browser process and reopened both routes, proving A
restored enabled while B remained absent. Its amended screen-close/reopen assertion
passed twice consecutively after repair and the complete unit suite passed.

Before repair, the exact amended source failed twice after Disable, browser-screen
close/reopen, and explicit Enable: the test timed out at line 531 waiting for the
exact enabled state. A later diagnostic run passed in 96.525s, identifying an
intermittent Gecko callback/list propagation race rather than an install-network
failure. A single delayed inspection remained insufficient in a later exact-candidate
run, which reproduced the same line-543 failure. The bounded repair retains
quarantine after the mutation callback and, only while a non-null list still contains
the exact reviewed extension with its previous enabled flag, reinspects every 500ms
for at most 25 seconds. It does not retry the mutation. Null, absent, wrong-identity,
wrong-version/signature, and exhausted previous-state results remain recoverable
fail-closed failures. Two clean exact-install lifecycle runs passed consecutively in
44.687s and 44.640s after this repair.

Primary exact-head verification then rebuilt every required task and installed the
fresh universal app APK incrementally. Pulled device APKs matched the local files
byte-for-byte. The first full post-repair suite had one unrelated navigation timing
failure after 25 preceding tests; that same test passed alone in 13.014s. A clean,
diagnosed full-suite retry passed all 34 tests in 573.607s. The required external
whole-app force-stop sequence then passed in fresh target processes: the default
phase left A exact enabled and B absent, and the verification phase restored those
states after force-stop. The emulator network throttle was set to `full` before the
successful runs after a prior run showed only official-artifact download timeouts;
the host artifact returned HTTP 200 with the pinned 7,011,169-byte length, and the
emulator reported a validated network with the artifact host's port 443 reachable.

Immediately after the incremental reinstall, two setup attempts timed out before the
initial detector-consent screen because an Android `System UI isn't responding`
dialog covered the launcher; UI hierarchy confirmed no product activity was resumed.
After choosing the system dialog's Wait action, all required post-repair runs passed.

## Acceptance mapping

- Visible installed/enabled state comes only from `WebExtensionController.list()`
  metadata. Disabled, absent, list failure, and popup failure never claim tracking is
  active; enabled state directs users to the official popup.
- Disable and enable durably quarantine and blank browsing before mutation, await the
  Gecko callback, then require a non-null list with the exact reviewed ID, version,
  signed state, and requested enabled flag before restoring browsing.
- Enable and reinstall persist a profile-local pending-restoration marker before
  mutation. A replacement screen stays quarantined while exact metadata is stale or
  absent; only a fresh exact enabled re-list clears pending state and quarantine. The
  public lifecycle test closes/reopens immediately after Enable and accepted reinstall.
- Uninstall reuses the hardened cleanup path and restores only after list-confirmed
  absence. Reinstall reuses the verified installer and Gecko prompt delegate.
- The debug update trigger calls the production prompt delegate's actual
  `onUpdatePrompt`, awaits `DENY`, and re-lists the exact signed enabled package before
  reporting unchanged state.
- Screen close/reopen, A/B process switching, explicit browser-process recreation,
  and route reopen are public production-shell checks. The existing production
  session lifecycle test separately covers repeated close/reopen, profile switching,
  prior-generation death, explicit worker stop/reopen, and latest-request routing.
- The fixed-profile default phase ended with A exact enabled and B absent, then
  stopped the browser while preserving both Gecko profiles. After an external
  whole-app force-stop, the same test method's verification phase ran in a fresh
  target process, opened A and observed exact enabled state, switched to B and
  observed absent state, then stopped the browser. No app-owned process remained.

No credentials, cookies, tokens, trade contents, account identity, screenshots, or
URLs containing sensitive state were recorded.
