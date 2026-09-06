# ADR-0001: Adopt GeckoView for CSFloat extension support

- **Status:** Accepted after compatibility `GO`
- **Date:** 2026-09-01
- **Decision owner:** Steam Account Manager maintainers
- **Parent issue:** [#3](https://github.com/D4gkan/Steam-Account-Manager-App/issues/3)

## Context

The production browser uses `android.webkit.WebView`. It can display Steam and
CSFloat pages in isolated sessions, but it does not host Firefox or Chromium
extensions and does not expose the WebExtension runtime required by CSFloat trade
tracking. Desktop-mode or user-agent changes affect presentation only; they do not
provide extension background workers, alarms, storage, messaging, or host permissions.

Steam Account Manager must preserve its existing `(account, website)` isolation
boundary while adding CSFloat installation, explicit consent for Steam host access,
extension action/popup controls, background tracking, and restart-safe state.

Review of the official Firefox 5.17.0 artifact found that its build promotes
`*://*.steampowered.com/*` from an optional host permission to a required install-time
host permission. The AMO artifact reports no optional permissions. The Chromium
runtime grant/deny/revoke flow described by the parent issue therefore cannot be
assumed for the official Firefox package.

## Decision

GeckoView is the target production browser engine. The application will integrate the
official, unmodified, Firefox-compatible CSFloat extension and will own the browser
shell around GeckoView.

The browser shell remains responsible for:

- deterministic browser-session selection and isolation;
- back, forward, reload, loading, error, and external-open behavior;
- enforcement of the existing navigation policy;
- extension installation and update status;
- install and update permission prompts, plus optional-permission prompts only when
  the installed package actually declares optional permissions;
- access to the CSFloat extension action/popup or an equivalent consent-preserving
  control; and
- per-browser-session disable/uninstall and explicit enable/reinstall controls for
  revoking and restoring all CSFloat access; and
- recoverable errors and non-sensitive compatibility diagnostics.

GeckoView and the CSFloat extension will be pinned to versions that pass the
compatibility contract. Either dependency changing is a security-sensitive upgrade
that requires the contract to be rerun.

The first GeckoView release supports only the official CSFloat extension. Arbitrary
extensions and an extension marketplace remain out of scope.

## Mandatory prototype gate

Production migration is forbidden until a disposable GeckoView prototype satisfies
`docs/verification/geckoview-csfloat-prototype.md` and records a `GO` decision.

The prototype must prove the real official package in a custom GeckoView embedder. A
Firefox Android listing is supporting evidence, not acceptance evidence. The gate
must include a real supported Android device, two safe test accounts, restart and
background behavior, extension-storage isolation, and per-session extension
disable/uninstall and restore behavior.

A `NO-GO` decision stops the production migration tickets and requires this ADR to be
revisited. It must not be worked around by weakening the isolation or consent
requirements.

## Security and privacy constraints

- The browser session remains the `(account, website)` pair.
- Cookies, authenticated state, local storage, extension storage, and extension
  permissions must not cross browser sessions.
- The install prompt shows required capabilities and origins before consent.
- The current official Firefox package must not be documented or tested as exposing a
  runtime optional Steam-host permission when it does not.
- Revocation disables or uninstalls the extension only within the affected isolated
  browser session. Restoration explicitly enables or reinstalls it and repeats any
  required install-time consent; neither action may affect another browser session.
- The application must not log credentials, cookies, tokens, trade contents, or
  identifying screenshots.
- The official signed extension and its update/signature chain must be preserved.
- Extension and GeckoView upgrades require explicit compatibility evidence.

## Consequences

### Positive

- CSFloat can use a maintained Firefox-compatible extension runtime.
- The project does not maintain rendering, JavaScript, networking, or browser-security
  internals.
- Permission and extension behavior can be exposed through supported GeckoView APIs.
- One browser engine can eventually serve every supported website.

### Costs and migration effects

- APK size and memory use will increase compared with system WebView.
- Existing WebView state cannot be assumed to migrate; users may need to authenticate
  again.
- Runtime, profile, process, and activity lifecycles become application-owned design
  concerns.
- Extension action, consent, update, and recovery UI must be built by the app.
- Compatibility can regress when GeckoView or CSFloat changes.

## Rejected alternatives

- **Build a browser engine or Chromium fork:** unnecessary, unsafe, and outside the
  project's scope.
- **Emulate extension APIs in WebView:** incomplete and creates a security-sensitive
  compatibility layer the project cannot responsibly maintain.
- **Desktop mode or user-agent spoofing:** changes page presentation but does not add
  WebExtension APIs.
- **Permanent dual-engine support:** doubles lifecycle, storage, navigation, and
  security complexity while WebView still cannot satisfy the core requirement.
- **External-browser-only workflow:** remains useful recovery behavior but defeats the
  in-app session requirement.
- **Fork or auto-authorize CSFloat:** breaks the official update/signature chain or
  removes informed user consent.

## Compatibility-gate resolutions

Issue #7 recorded `GO` after the complete gate passed on the Android 16/API 36
`Codex_GeckoView_Campaign_API_36` AVD and a Samsung Galaxy S25 Ultra running Android
16. The authenticated A/B lifecycle matrix did not require Steam login or Steam
Guard again after initial authentication. These results select the following
production direction without copying the disposable prototype's activity or unit
model wholesale:

- Each `(account, website)` browser session maps deterministically to an opaque,
  persistent profile stored under app-owned no-backup storage.
- Only one dedicated Gecko worker and runtime is active at a time. Switching sessions
  performs bounded shutdown of the old worker and app child processes before the new
  profile starts.
- `contextId` alone is rejected as the isolation boundary because extension
  installation, storage, and enablement are runtime-wide. Profile-local
  disable/enable and uninstall/reinstall preserve the selected session boundary;
  reinstall repeats explicit consent.
- GeckoView is pinned to stable Maven version `153.0.20260810162159`. CSFloat is
  pinned to the official signed, unmodified Firefox artifact `5.17.0`, extension ID
  `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, observed signed state `2`, from the
  exact AMO artifact
  `https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi`.
  Its XPI SHA-256 is
  `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D`.
- Distribution accepts only that exact official AMO artifact after validating its
  source, ID, version, signed state, and checksum. Required permissions and origins
  are shown through explicit callback-derived consent; no permission is granted
  silently.
- There is no automatic blind upgrade. Any change to GeckoView, CSFloat, the artifact
  source, permission manifest, or supported Android range reruns GV-02 through GV-16
  on an emulator and supported physical device before either pin advances.

The exact debug APK was 598,810,380 bytes versus the 63,099,195-byte upstream
baseline, a delta of 535,711,185 bytes. Representative debug-only active PSS points
ranged from 410,523 to 590,931 KiB, with a router-only point of 133,659 KiB. These
are observations, not release benchmarks or performance guarantees.

The gate does not prove every Android device, long-duration background tracking,
release download or installed size, battery or production performance, final
production migration cleanup, or broad multi-window, `window.opener`, `postMessage`,
and `window.close` compatibility. Those limits remain production verification work.

## References

- [Issue #3: GeckoView migration decision](https://github.com/D4gkan/Steam-Account-Manager-App/issues/3)
- [Issue #7: compatibility gate `GO`](https://github.com/D4gkan/Steam-Account-Manager-App/issues/7)
- [Issue #1: original WebView request](https://github.com/D4gkan/Steam-Account-Manager-App/issues/1)
- [Issue #7 verification receipt](../verification/geckoview-issue-7-emulator-run.md)
- [Issue #7 manual test guide](../manual-testing/geckoview-campaign-dagkan.md)
- [Mozilla: interacting with Web content and WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [Mozilla: GeckoView extension management](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/managing-extensions.html)
- [Mozilla: WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)
- [Official CSFloat Firefox Android listing](https://addons.mozilla.org/en-US/android/addon/csgofloat/)
- [Official CSFloat Firefox manifest conversion](https://github.com/csfloat/extension/blob/master/webpack.config.js)
- [CSFloat extension source](https://github.com/csfloat/extension)
