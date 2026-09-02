# GeckoView issue #4 emulator run

This is the non-sensitive evidence record for the machine-verification portion of
[issue #4](https://github.com/D4gkan/Steam-Account-Manager-App/issues/4). It does
not record an issue #7 `GO` decision and does not replace the physical-device gate.

## Run metadata

| Field | Value |
| --- | --- |
| App source commit and build variant | `208a68d20dee380116f112d475652fd574d238d3`, `debug` |
| Evidence-run HEAD | `dc6fdb140b12ad8b2cc2f51512de26054e9ee9a3`; its only difference after the app source commit is this text record |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, 598,678,168 bytes, SHA-256 `8151386B1A94B720473258F2EF28E1F3643E782D1EA1D592CCFC8A508C92802F`; preserved on the campaign host for independent verification |
| GeckoView version/channel | `153.0.20260810162159`, stable Maven artifact |
| CSFloat source | `https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi` |
| CSFloat identity | ID `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, version `5.17.0` |
| CSFloat artifact | 7,011,169 bytes, SHA-256 `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D` |
| Observed signature state | GeckoView signed state `2`, reported after supported-API installation |
| Host | Windows 11 Pro 64-bit, version `10.0.26200`, build `26200` |
| Android Studio | 2025.2.1 build `AI-252.25557.131.2521.14432022` |
| Emulator tooling | Android Emulator `36.2.12.0` build `14214601`; adb `36.0.0-13206524` |
| Android target | Dedicated AVD `Codex_GeckoView_Campaign_API_36`, serial `emulator-5580`, Android 16/API 36, `x86_64`, medium-phone hardware profile |
| System image | `system-images/android-36/google_apis_playstore/x86_64`; fingerprint `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys` |
| Test window | 2026-09-02 12:43:56–12:55:09 UTC |
| Runtime/profile/process topology | Debug-only launcher activity in `com.steamaccountmanager.app.debug:gecko_prototype`; one `GeckoRuntime`, one `GeckoSession`, default prototype profile; production WebView activity unchanged |
| Representative memory sample | Total PSS 272,543 KiB before extension installation and 231,954 KiB after installation and reload, a two-point delta of -40,589 KiB. This noisy emulator sample is recorded for reproduction and is not a performance claim. |
| Gate result | `INCOMPLETE` overall: issue #4 machine scenarios pass; GV-03 and the full issue #7 emulator/physical-device gate remain pending by design. |

## Commands and results

Commands ran from the repository root. `ANDROID_SERIAL=emulator-5580` constrained
Gradle instrumentation to the dedicated AVD. Every direct adb command used
`-s emulator-5580`.

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SERIAL='emulator-5580'
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' testDebugUnitTest assembleDebug lintDebug --rerun-tasks --stacktrace
# exit 0: BUILD SUCCESSFUL; 14 unit tests, 0 failures; debug APK assembled; lint passed

.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr' connectedDebugAndroidTest --rerun-tasks --stacktrace
# exit 0: BUILD SUCCESSFUL; 9 instrumentation tests on Codex_GeckoView_Campaign_API_36, 0 failures

$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5580 install -r -t .\app\build\outputs\apk\debug\app-debug.apk
# exit 0: Success

& $adb -s emulator-5580 shell am start -W -n 'com.steamaccountmanager.app.debug/com.steamaccountmanager.app.prototype.GeckoViewPrototypeActivity'
# exit 0: Status ok; LaunchState COLD

& $adb -s emulator-5580 shell input tap 540 146
# exit 0: requested the supported GeckoView extension-install flow

& $adb -s emulator-5580 shell input tap 690 1780
# exit 0: denied the first GeckoView install prompt

& $adb -s emulator-5580 shell input tap 540 146
& $adb -s emulator-5580 shell input tap 875 1780
# both exit 0: retried and explicitly accepted the second GeckoView install prompt

& $adb -s emulator-5580 shell am force-stop com.steamaccountmanager.app.debug
& $adb -s emulator-5580 shell am start -W -n 'com.steamaccountmanager.app.debug/com.steamaccountmanager.app.prototype.GeckoViewPrototypeActivity'
# both exit 0: cold process restart; prototype resumed without a crash
```

Generated reports:

- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Instrumentation: `app/build/reports/androidTests/connected/debug/index.html`
- Lint: `app/build/reports/lint-results-debug.html`

## Reviewable evidence

The evidence directory contains only the debug prototype, callback-derived consent
text, a public Steam market page, and bounded logs for the prototype PID. It was
visually inspected before commit. It contains no credentials, cookies, tokens,
account identifiers, private inventory, trade details, payment information, or
authentication UI.

- [Launch UI hierarchy](evidence/issue-4/gv4-launch.xml)
- [Install prompt screenshot](evidence/issue-4/gv4-prompt.png) and
  [UI hierarchy](evidence/issue-4/gv4-prompt.xml)
- [Denial screenshot](evidence/issue-4/gv4-denied.png) and
  [UI hierarchy](evidence/issue-4/gv4-denied.xml)
- [Installed-state screenshot](evidence/issue-4/gv4-installed.png) and
  [UI hierarchy](evidence/issue-4/gv4-installed.xml)
- [Visible injection screenshot](evidence/issue-4/gv4-injection.png)
- [Post-restart injection screenshot](evidence/issue-4/gv4-relaunch-injection.png)
- [Bounded warning-or-higher prototype-process log](evidence/issue-4/gv4-bounded-warning-log.txt)

## Consent evidence

The first install request presented an app-owned dialog titled `Install-time access
request`, identified `CSFloat Market Checker`, and rendered the callback-derived
origin list before any grant:

- `*://*.steamcommunity.com/market/listings/730/*`
- `*://*.steamcommunity.com/id/*/inventory*`
- `*://*.steamcommunity.com/id/*/tradehistory*`
- `*://*.steamcommunity.com/profiles/*/inventory*`
- `*://*.csfloat.com/*`
- `*://*.steampowered.com/*` (includes the Steam API host)
- `*://*.steamcommunity.com/profiles/*/tradehistory*`
- `*://*.steamcommunity.com/tradeoffer/*`
- `*://*.steamcommunity.com/*/tradeoffers/*`
- `*://*.steamcommunity.com/id/*`
- `*://*.steamcommunity.com/profiles/*`

GeckoView reported zero named permissions and zero data-collection entries for this
install callback. The prototype displayed those zero counts exactly instead of
inventing capabilities. It made no claim that the Firefox artifact exposes a runtime
optional Steam-host permission.

Denial returned to the still-usable public page, kept the install button enabled, and
showed `Extension installation failed. Retry or open the page externally.` The next
explicit attempt presented the same access list and allowed acceptance. Success
reported `CSFloat Market Checker 5.17.0`, GeckoView signed state `2`, disabled the
install button, and reloaded the listing.

## Scenario results

| ID | Result | Evidence |
| --- | --- | --- |
| GV-01 | PASS | The [launch hierarchy](evidence/issue-4/gv4-launch.xml) records the separate debug launcher; it cold-started in the `:gecko_prototype` process while production activity and WebView remained present and unchanged. |
| GV-02 | PASS | The [installed-state hierarchy](evidence/issue-4/gv4-installed.xml) and [screenshot](evidence/issue-4/gv4-installed.png) show that GeckoView's supported install API fetched, signature-validated, installed, and started the pinned official XPI. Identity, versions, source, checksum, and signed state were visible. |
| GV-03 | NOT RUN | Authenticated evidence is explicitly deferred to issue #7. No account was used in this run. |
| GV-04 | PASS | The [injection screenshot](evidence/issue-4/gv4-injection.png) shows CSFloat `Pattern Template`, `Wear Rating`, and wear-bar content on the public listing. The [post-restart screenshot](evidence/issue-4/gv4-relaunch-injection.png) shows the same injected content after a full app-process restart. |
| GV-05 | PASS | The [prompt hierarchy](evidence/issue-4/gv4-prompt.xml) and [screenshot](evidence/issue-4/gv4-prompt.png) show the callback-derived identity and all 11 origins, including `*://*.steampowered.com/*`, before consent. |
| GV-06 | PASS | The [denial hierarchy](evidence/issue-4/gv4-denied.xml) and [screenshot](evidence/issue-4/gv4-denied.png) show the safe failure explanation, enabled retry, and still-loaded public listing after denial. |
| GV-07 | PASS | The prompt and installed-state captures together show a later explicit acceptance and the exact signed Firefox artifact at signed state `2`; no silent grant or optional-runtime-permission claim occurred. |

## Diagnostics and privacy

The [bounded warning-or-higher logcat read](evidence/issue-4/gv4-bounded-warning-log.txt)
used only the live prototype PID. It showed
expected emulator/Gecko initialization warnings (x86 CPU variant, HWUI format,
Android hidden-API denial, sandboxed sysfs/netlink denial, and unhandled browser-action
notifications). It contained no crash, app exception, credential, cookie, token,
account identifier, or trade data. No full logcat or browser storage was collected.

Screenshots and UI hierarchy captures were inspected before commit. They contain only
the debug prototype, its consent dialog, and a public Steam listing. Integrity hashes
for the committed screenshots are:

- Consent prompt PNG: `10BA6CDB2C64FE231D6B689E8C76AC83BC36A9DC4B50A7D1C165F9D9F480CBDD`
- Denial-state PNG: `CD008FC24B104C8517659C09B11D9C4AE4BC048C1FC52F47F2B5927F0D6E04FF`
- Installed-state PNG: `D0EF97D26ABF25A78C4619A6BD609DCAAE023D31E9EFA363AB2C219037C8BCFA`
- Injection PNG: `E86D0948BCBC3779570117E8BF5981E50CAF4ED924428167DB49EB161A9FF9EB`
- Post-restart injection PNG: `716A566DB43CB0F7AC45A51A5A54A9C1DAE700ED099A1C87700D91B65954D755`

## Known limitations

- This run used a single x86_64 emulator and a public, unauthenticated page.
- Network content and listing values are nondeterministic; the observed run does not
  prove future Steam, AMO, or CSFloat availability.
- The memory figures are two settled point samples, not a benchmark.
- Authentication, two-session isolation, revoke/restore, tracking controls, lifecycle
  matrices, and physical-device behavior remain assigned to issues #5–#7.
- This record is not an ADR amendment, maintainer acknowledgement, or permission to
  begin production migration.
