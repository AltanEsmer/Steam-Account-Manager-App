# Issue #10 production CSFloat verification receipt

Status: **Machine/emulator verification PASS at application/test source `d98eedb`**

## Exact candidate

- Application/test source: `d98eedbe780e2af8fb4463b82c0c445c8fa68dde`
- Starting checkpoint: `31f9fadbf16dd3aa1defc4c4aff132def1f738f7`
- Branch: `codex/geckoview-campaign`
- Variant: debug
- Preserved evidence directory:
  `C:\Users\esmer\AppData\Local\Temp\sam-gv10-d98eedb`
- `app-debug.apk`: 599,057,529 bytes; SHA-256
  `4B89A78862BE64AAEEAC0692117BA2D7F2B1A036E05482FDBCCB5939BB63BF5B`
- `app-debug-androidTest.apk`: 2,300,722 bytes; SHA-256
  `05353B4021878C049FA68904EEAD3A722DA5C27B22A1F72219A4B5403D3845CD`
- Verification completed: 2026-09-07 13:07 CEST

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

Install, disabled, update-policy, popup-available, popup-opened, and failure states
are visible. Failed install/popup and cleanup operations remain retryable and retain
external-browser recovery. The app does not infer or self-record live tracking; it
directs the user to inspect tracking status inside the official popup. Extension
state remains in the approved isolated Gecko runtime for the selected
`(account, website)` session.

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
journeys pass. Review then rejected the app's unsupported action/active wording and
fire-and-forget cleanup. Commit `6b147c9` added a genuinely red truthful-popup
contract; `6e9a1a5` removed the app-owned tracking assertion; `8331f10` made cleanup
fail closed; and `d98eedb` removed stale tracking claims from proof names.

The fresh unit/build/lint command at exact source `d98eedb` was:

```powershell
$env:ANDROID_HOME = 'C:\Users\esmer\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease --rerun-tasks --console=plain
```

Exit 0 in 1 minute 2 seconds; all 103 tasks executed. All 67 unit tests passed with
zero failures, errors, or skips. Debug and Android-test APK assembly passed. Debug
lint reported 123 warnings and zero errors; release lint reported 55 warnings and
zero errors. The result XML files are under
`app\build\test-results\testDebugUnitTest`, while lint XML is under
`app\build\reports`.

The exact APK install commands were:

```powershell
$adb = 'C:\Users\esmer\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5580 install --incremental -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s emulator-5580 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

The dedicated AVD initially rejected an incremental replacement because only about
one GB was free. After verifying the exact package names, only the campaign-owned
debug and test packages were uninstalled; both removals exited 0. The exact main
APK then installed incrementally and the test APK installed by streaming, both with
exit 0. The final `d98eedb` commit changes test names only: its rebuilt main APK was
byte-identical to the already installed main APK, and its rebuilt test APK was
streamed again successfully before the final run.

The final full emulator command was:

```powershell
& $adb -s emulator-5580 shell am instrument -w -r com.steamaccountmanager.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Exit 0; 32/32 tests passed in 309.605 seconds. The runner exercised the complete
production CSFloat denial and acceptance paths; official-popup rendering;
popup-failure/retry; cleanup-failure, immediate safe blanking, retry, and
list-confirmed absence; A-enabled/B-absent/A-enabled isolation; ordinary browsing
after denial; external recovery; production browser navigation; process lifecycle;
persistence; detector boundaries; and all earlier prototype regressions.
Accessibility assertions observed the real official popup label `Offer Tracking
Enabled`. That label proves required permission in this artifact, not a live
tracking update, and the app does not claim otherwise.

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
- Rejected-package cleanup immediately blanks the embedded session, waits for
  uninstall completion, reinspects installed state, and restores browsing only
  after confirming absence. Failure stays blank and popup-unavailable with explicit
  cleanup retry and the prior safe HTTP(S) destination preserved for external use.
- The deterministic popup/cleanup failure controls exist only in debuggable builds;
  they exercise the production recovery paths and are absent from release builds.
- State is not shared with another `(account, website)` runtime. The emulator test
  proves A/B separation through the production process/profile topology.
- No credentials, Steam Guard codes, QR payloads, cookies/tokens, account IDs,
  trades, payment data, browser storage, or authentication screenshots were
  requested, captured, logged, committed, or uploaded.
- The API-36 x86_64 emulator proves the automated production flow with synthetic
  public fixtures. It does not prove a real Steam authentication session, live or
  long-duration tracking, every vendor device, future CSFloat versions, or packages
  other than the exact approved CSFloat artifact. Issue #7's official GO records
  the separate authenticated live tracking proof. Other extensions remain outside
  issue #10 and this campaign.
