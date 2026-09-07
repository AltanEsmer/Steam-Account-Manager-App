# Issue #10 production CSFloat verification receipt

Status: **Machine/emulator verification PASS at application/test source `4473769`**

## Exact candidate

- Application/test source: `447376979d35f53863618fc3dd5ca6f13e451d8b`
- Starting checkpoint: `31f9fadbf16dd3aa1defc4c4aff132def1f738f7`
- Branch: `codex/geckoview-campaign`
- Variant: debug
- Preserved evidence directory:
  `C:\Users\esmer\AppData\Local\Temp\sam-gv10-4473769`
- `app-debug.apk`: 599,024,761 bytes; SHA-256
  `F440EFC3E73FB0904C624FDCAE669F1F08A6D865DC71DB43BF25183BBA26E544`
- `app-debug-androidTest.apk`: 2,296,758 bytes; SHA-256
  `95D269A90DBCFC16067081974D28F3ECBA93CF0681DB4434832449A37BB16F7D`
- Verification completed: 2026-09-07 11:53 CEST

## Approved extension artifact

- Source: `https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi`
- Extension ID: `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`
- Version: `5.17.0`
- Size: 7,011,169 bytes
- SHA-256:
  `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D`
- Gecko signature state required after installation: `2` (signed)
- GeckoView: `153.0.20260810162159`
- Update policy: the reviewed package/version is pinned; automatic extension updates
  are not enabled in this campaign.

The production flow downloads this exact package to app-private temporary storage,
verifies its size and SHA-256 while streaming, then asks GeckoView to install the
unmodified file. Installation is accepted only when GeckoView reports the exact
ID, version, and signed state above. The package was not forked, repacked, modified,
silently authorized, or replaced.

## What changed

The production Gecko browser now offers an explicit CSFloat install flow. Before
installation it displays the extension name and every capability, origin, and data
collection item reported by GeckoView. The user may deny or accept. Denial leaves
ordinary browsing usable. Acceptance installs the verified official package, and
the browser exposes the official signed popup without fabricating a Firefox-only
Steam permission prompt.

Install, disabled, update-policy, action-ready, record-ready, active, and failure
states are visible. Failed install/action operations remain retryable and retain
external-browser recovery. Tracking becomes active only after the official popup
loads and the user records its visible status. Extension state remains in the
approved isolated Gecko runtime for the selected `(account, website)` session.

The official Manifest V3 package declares `src/popup.html` as its action popup. The
browser derives the popup URL only from GeckoView's enabled extension object,
requires a root `moz-extension://` base, and appends that fixed reviewed path. It
does not use hidden APIs, reflection, a WebView shim, or a synthetic popup.

## Environment

- Host: Windows 11 Pro build 26200
- Android Studio: 2025.2.1, `AI-252.25557.131.2521.14432022`
- Dedicated AVD: `Codex_GeckoView_Campaign_API_36`
- Exact adb serial: `emulator-5580`
- Emulator model: `sdk_gphone64_x86_64`
- Android: 16 / API 36
- ABI: x86_64

Every device command selected `emulator-5580`. No personal AVD, physical device,
SDK, virtualization setting, firmware setting, or security setting was changed.
The AVD was not wiped. Its small data partition cannot stage the approximately
599 MB multi-ABI debug APK conventionally, so Android's supported incremental
installer was used for the main APK; the small test APK used streamed install.

## Automated and emulator results

The behavioral contract commit `58217a2` was genuinely red: the focused unit
command failed to compile because `CsfloatExtensionContract` did not exist. The
subsequent feature and bounded repair commits made the contract and public-behavior
journeys pass. The repairs preserve truthful action/recovery state and load the
verified official popup through a normal GeckoSession.

The fresh unit/build/lint command at exact source `4473769` was:

```powershell
$env:ANDROID_HOME = 'C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease --rerun-tasks --console=plain
```

Exit 0. All 66 unit tests passed with zero failures, errors, or skips. Debug and
Android-test APK assembly passed. Debug lint reported 121 warnings and zero errors;
release lint reported 53 warnings and zero errors. The result XML files are under
`app\build\test-results\testDebugUnitTest`, while lint XML is under
`app\build\reports`. An immediate exact-task confirmation also exited 0 with 103
actionable tasks (2 executed, 101 up-to-date).

The exact APK install commands were:

```powershell
$adb = 'C:\Users\esmer\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5580 install --incremental -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s emulator-5580 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Both exited 0. The incremental main install completed successfully in 3,775 ms;
the streamed test install also reported `Success`.

The final full emulator command was:

```powershell
& $adb -s emulator-5580 shell am instrument -w -r com.steamaccountmanager.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Exit 0; 31/31 tests passed in 290.281 seconds. The runner exercised the complete
production CSFloat denial and acceptance paths, official-popup rendering, visible
status recording, tracking transition, A-enabled/B-absent/A-enabled isolation,
ordinary browsing after denial, external recovery, production browser navigation,
process lifecycle, persistence, detector boundaries, and all earlier prototype
regressions. Accessibility assertions observed the real official popup label
`Offer Tracking Enabled`; no visible behavior is inferred from logs alone.

After the run, the app and test packages were force-stopped and `pidof` returned no
app-owned main, Gecko, or Gecko-child process. `git diff --check` passed and the
tracked worktree was clean at the application/test source checkpoint.

## Security, privacy, and limitations

- The install trust boundary checks the downloaded byte count and SHA-256 before
  GeckoView validates the manifest and Mozilla signature.
- Consent content comes from GeckoView's install prompt. Required permissions,
  origins (including Steam API access), and data-collection items are not replaced
  by an app-maintained summary.
- Dismissal or denial fails closed. Optional host and update prompts are denied;
  no Chromium-style optional Steam prompt is shown or simulated.
- The popup destination cannot come from website content or user input. It is built
  from the enabled, exact-ID signed extension base and the reviewed manifest path.
- State is not shared with another `(account, website)` runtime. The emulator test
  proves A/B separation through the production process/profile topology.
- No credentials, Steam Guard codes, QR payloads, cookies/tokens, account IDs,
  trades, payment data, browser storage, or authentication screenshots were
  requested, captured, logged, committed, or uploaded.
- The API-36 x86_64 emulator proves the automated production flow with synthetic
  public fixtures. It does not prove a real Steam authentication session, every
  vendor device, future CSFloat versions, or packages other than the exact approved
  CSFloat artifact. Other extensions remain outside issue #10 and this campaign.
