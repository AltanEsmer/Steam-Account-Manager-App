# GeckoView issue #11 emulator verification

## Build and environment

- Source checkpoint: `f96185f9e893a94b72fb2b0521bd232b86bf1dab`
- Starting checkpoint: `1a112ab71cd0ff41caad7164a65d1acb9949f20c`
- Variant: `debug`
- Emulator: `emulator-5580`, Android 16 / API 36, `sdk_gphone64_x86_64`
- GeckoView: `153.0.20260810162159`
- CSFloat: official signed 5.17.0, ID
  `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, observed signed state `2`
- App APK: 599,566,454 bytes, SHA-256
  `3F63809E3A3FB598F36C068F4C8EF2736840B542FFBB4CAC331876103B889C2D`
- Test APK: 2,410,371 bytes, SHA-256
  `9687397BC09E51A03B83B69AB3B4570AA59FA9319591B1167AC5058DE9B077A9`

Every device command used `adb -s emulator-5580`. Installation initially lacked
temporary space, so only the prior scoped app and test packages were removed before
installing these APKs; no unrelated package or emulator state was changed.

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
```

The five-test run covered install denial and acceptance, callback-derived required
permission display, unavailable/available/opened popup ownership, fail-closed denied
verification, cleanup failure/retry, exact disable/enable, exact uninstall/reinstall,
pinned update denial, and A/B profile isolation. The revocation tracer also stopped
and recreated the dedicated browser process and reopened both routes, proving A
restored enabled while B remained absent. Its amended screen-close/reopen assertion
compiled and the complete unit suite passed.

Two later attempts to rerun the amended tracer timed out waiting 30 seconds for the
initial Gecko install prompt after clicking Install. They failed before any issue #11
mutation and after the same official-network install path had passed six times in the
recorded runs above; no mutation assertion failed. This is recorded as an external
artifact/download nondeterminism rather than hidden.

## Acceptance mapping

- Visible installed/enabled state comes only from `WebExtensionController.list()`
  metadata. Disabled, absent, list failure, and popup failure never claim tracking is
  active; enabled state directs users to the official popup.
- Disable and enable durably quarantine and blank browsing before mutation, await the
  Gecko callback, then require a non-null list with the exact reviewed ID, version,
  signed state, and requested enabled flag before restoring browsing.
- Uninstall reuses the hardened cleanup path and restores only after list-confirmed
  absence. Reinstall reuses the verified installer and Gecko prompt delegate.
- The debug update trigger calls the production prompt delegate's actual
  `onUpdatePrompt`; it remains denied and the reviewed version stays pinned.
- Screen close/reopen, A/B process switching, explicit browser-process recreation,
  and route reopen are public production-shell checks. The existing production
  session lifecycle test separately covers repeated close/reopen, profile switching,
  prior-generation death, explicit worker stop/reopen, and latest-request routing.
- A separate force-stop of the instrumentation-host app process cannot resume the
  same test, so activity recreation and a literal whole-app force-stop were not
  independently automated in this scoped run. Persistent profile restoration was
  observed across browser-process death and production route reopen.

No credentials, cookies, tokens, trade contents, account identity, screenshots, or
URLs containing sensitive state were recorded.
