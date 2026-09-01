# ADR-0001: Adopt GeckoView for CSFloat extension support

- **Status:** Accepted with a mandatory compatibility gate
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

## Decisions the prototype must resolve

The ADR deliberately does not prescribe these before evidence exists:

- Gecko runtime/profile/process topology per browser session;
- whether any Gecko context-partitioning mechanism is sufficient for extension
  storage and permission isolation;
- runtime and background-extension lifetime across process and app restart;
- signed package acquisition, signature verification, and update behavior;
- extension action/popup presentation in the Compose browser shell;
- exact disable/uninstall and enable/reinstall lifecycle behavior within one isolated
  browser session;
- reauthentication messaging and cleanup of obsolete WebView state; and
- acceptable APK-size, memory, and supported-device impacts.

The compatibility-gate record must amend this section with the selected behavior or
link to a follow-up ADR before production work begins.

## References

- [Issue #3: GeckoView migration decision](https://github.com/D4gkan/Steam-Account-Manager-App/issues/3)
- [Issue #1: original WebView request](https://github.com/D4gkan/Steam-Account-Manager-App/issues/1)
- [Mozilla: interacting with Web content and WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [Mozilla: GeckoView extension management](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/managing-extensions.html)
- [Mozilla: WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)
- [Official CSFloat Firefox Android listing](https://addons.mozilla.org/en-US/android/addon/csgofloat/)
- [Official CSFloat Firefox manifest conversion](https://github.com/csfloat/extension/blob/master/webpack.config.js)
- [CSFloat extension source](https://github.com/csfloat/extension)
