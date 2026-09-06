# Steam Account Manager domain context

## Purpose

Steam Account Manager is a local, offline-first Android application for opening
multiple Steam-related websites without leaking authenticated browser state between
accounts. The app does not operate a backend and must not retain Steam credentials
after the login flow. Any app-owned transient login buffer follows the encrypted
storage and clearing contract in `SecureCredentialStore`; authenticated browser state
remains owned by the browser engine.

## Ubiquitous language

Use these terms consistently in code, tests, issues, and documentation.

- **Account**: the local user-defined identity represented by an `Account` record.
  It is not itself a browser-storage boundary.
- **Website**: a configured web destination and its navigation allowlist.
- **Browser session**: exactly one `(account, website)` pairing. This is the unit of
  browser-state isolation and persistence.
- **Session identity**: the stable `SessionIdentifier` derived from an account ID and
  website ID. It must map deterministically to the browser engine's isolation unit.
- **Browser profile**: engine-owned persistent state for a browser session, including
  cookies, authenticated site state, local storage, and extension storage. The exact
  GeckoView runtime/profile topology is decided by prototype evidence, not assumed.
- **Browser shell**: application-owned UI and lifecycle behavior around the browser
  engine: navigation controls, loading and error state, permission prompts, extension
  controls, and external handoff.
- **Navigation policy**: the rule allowing the configured primary and authentication
  domains while blocking other in-app navigation and offering external handoff.
- **CSFloat extension**: the official, unmodified, Firefox-compatible CSFloat package.
  It is the only supported third-party extension in the first GeckoView release.
- **Compatibility prototype**: a disposable integration used to test real GeckoView
  and CSFloat behavior before production browser code is migrated.
- **Prototype gate**: the binary `GO` or `NO-GO` decision recorded after every required
  compatibility scenario passes or fails with evidence.

## Browser-session invariants

1. Browser state is isolated by `(account, website)`, not by account alone.
2. One browser session must never observe another session's cookies, authenticated
   Steam identity, site storage, extension storage, or permission state.
3. The Room database stores session metadata only. Credentials, cookies, tokens, and
   browser storage are not Room data. Authenticated browser state remains owned by the
   browser engine; a transient login buffer may exist only under the clearing contract
   in `SecureCredentialStore`.
4. Extension installation and every requested permission require explicit user
   consent; access is never granted silently. The current official CSFloat Firefox
   package promotes its Steam API host permission to a required install-time
   permission and declares no optional runtime permissions. Revoking access therefore
   disables or uninstalls CSFloat only within the affected isolated browser session;
   restoration requires an explicit enable or reinstall with install-time consent.
5. Navigation outside the configured policy is not loaded in-app. The browser shell
   offers an external-browser handoff as recovery behavior.
6. Browser and extension diagnostics must not contain Steam credentials, cookies,
   tokens, trade data, or personally identifying account evidence.
7. Production GeckoView migration work cannot start until the prototype gate records
   `GO` using `docs/verification/geckoview-csfloat-prototype.md`.
8. A permanent WebView fallback is not part of the target architecture. Temporary
   coexistence is allowed only while the gated migration is in progress.

## Current architecture

Production is in a staged engine migration:

- `BrowserProcessController` selects the browser session and restarts the dedicated
  browser process when its engine-owned profile changes.
- `BrowserActivity` opens Steam sessions with a persistent GeckoView profile and
  temporarily retains WebView for other websites as the reversible migration path.
- `GeckoBrowserScreen` and `BrowserScreen` own their engine-specific browser shell.
- `WebsitePolicy` contains the reusable host allowlist behavior; each engine adapts
  it at its navigation boundary.
- `SteamLoginDetector` accepts bounded public profile metadata from either the
  consented GeckoView content-script bridge or the temporary WebView path.
- `SessionRepository` persists only browser-session metadata.

This staged architecture is current truth until the final GeckoView cutover removes
the temporary WebView path.

## Target direction

Issue [#3](https://github.com/D4gkan/Steam-Account-Manager-App/issues/3)
selects GeckoView as the target browser engine because the CSFloat tracking flow
requires Firefox-compatible WebExtension capabilities that WebView does not provide.
The migration is conditional on the compatibility prototype.

The prototype must establish rather than guess:

- the Gecko runtime/profile/process topology for each browser session;
- isolation and persistence of extension storage and permission state;
- behavior of background alarms and tracking across screen, activity, process, and
  app recreation;
- the official signed extension acquisition, verification, and update path;
- the extension action/popup integration and user-consent experience; and
- the exact disable/uninstall and enable/reinstall lifecycle for one isolated browser
  session; and
- acceptable APK-size, memory, and supported-device effects.

## Boundaries and non-goals

- Do not build or fork a browser engine.
- Do not emulate WebExtension APIs with JavaScript bridges on WebView.
- Do not use desktop-mode or user-agent spoofing as an extension solution.
- Do not support arbitrary extensions or an extension marketplace in the first
  release.
- Do not fork, modify, or silently grant access to the CSFloat extension.
- Do not rewrite accounts, the database, the home screen, or unrelated application
  UI as part of the browser-engine migration.
- Do not claim compatibility from desktop Firefox or the Android add-on listing; the
  custom GeckoView embedder must pass the prototype gate.

## Decision and verification sources

- Architecture decision: `docs/adr/0001-adopt-geckoview-for-csfloat.md`
- Prototype contract: `docs/verification/geckoview-csfloat-prototype.md`
- Parent implementation decision: [GitHub issue #3](https://github.com/D4gkan/Steam-Account-Manager-App/issues/3)
- Original request: [GitHub issue #1](https://github.com/D4gkan/Steam-Account-Manager-App/issues/1)
