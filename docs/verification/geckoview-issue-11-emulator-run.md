# GeckoView issue #11 emulator verification

## Build and environment

- Source/test checkpoint: `3982a95a1ee06fd4ddf4f798bb922dd34ab7e63e`
- Production implementation checkpoint: `9f02d320520f8b78b641fa9a2b5216ac877bf866`
- Starting checkpoint: `1a112ab71cd0ff41caad7164a65d1acb9949f20c`
- Variant: `debug`
- Emulator: `emulator-5580`, Android 16 / API 36, `sdk_gphone64_x86_64`
- GeckoView: `153.0.20260810162159`
- CSFloat: official signed 5.17.0, ID
  `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, observed signed state `2`
- App APK: 599,568,666 bytes, SHA-256
  `A01695F3752A5CB8355DA58D0EC40C12F65821C31A9D96F8BDE3E0EA078D20ED`
- Test APK: 2,411,747 bytes, SHA-256
  `33B23FB47096FB2ACCBE117B0731812FF180C15462A6F6D26B756B330B078092`

Every device command used `adb -s emulator-5580`. Installation initially lacked
temporary space, so only the prior scoped app and test packages were removed before
installing these APKs; no unrelated package or emulator state was changed.
The app used Android incremental installation because the universal APK exceeded the
package manager's temporary-space reserve. Pulling the installed `base.apk` produced
the same 599,568,666-byte size and SHA-256 as the local candidate.

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
failure. The repair retains quarantine after the mutation callback and, only when a
non-null list still contains the exact reviewed extension with its previous enabled
flag, performs one generation-guarded inspection 500ms later. It does not retry the
mutation. Null, absent, wrong-identity, and repeated previous-state results remain
recoverable fail-closed failures. The two consecutive amended passes and subsequent
five-test pass above are the post-repair proof.

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
- Uninstall reuses the hardened cleanup path and restores only after list-confirmed
  absence. Reinstall reuses the verified installer and Gecko prompt delegate.
- The debug update trigger calls the production prompt delegate's actual
  `onUpdatePrompt`; it remains denied and the reviewed version stays pinned.
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
