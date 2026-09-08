# Final Samsung verification

Allow about 30 minutes, longer for first-time sign-ins. Use the Samsung Galaxy S25 Ultra / Android 16 used for the
earlier gates, two safe test accounts called A and B, and the exact candidate
APK/hash in [the final verification receipt](../verification/geckoview-issue-12-emulator-run.md).
Do not send passwords, Steam Guard codes, cookies, tokens, account names/IDs,
trade contents or screenshots of signed-in pages.

## Steps

1. **Install the candidate APK.** Install the supplied Samsung debug APK over the
   previous campaign debug build. Keep existing app data. The unsigned release
   build cannot be installed. Open the campaign app.
2. **Check every website.** Under A, open Steam, CSFloat, CS.MONEY, Skins.com and
   CSGOEmpire. Add `https://example.com` as a custom website and open it. Confirm
   each opens, scrolls and returns to the account list. Record any challenge or
   service error by website name only. Check Back, Forward, Refresh and an outside
   link: choose Stay here first, then explicitly Open externally.
3. **Check consent.** Steam alone asks about the public profile detector when it
   has no saved consent. Cancel once, reopen and allow. In Steam and each marketplace,
   press Install for its named extension. Deny once and confirm browsing still
   works; install again, read the permissions and accept. Confirm the named
   extension reports enabled and signed. No extension controls should appear on
   CSGOEmpire or the custom website. If an extension is already installed, record
   its persisted state first; check deny/accept on a fresh test profile or during
   the step 7 reinstall. Do not clear app data to expose Install.
4. **Sign in separately per website.** Steam, CSFloat, CS.MONEY and Skins.com have
   separate profiles even under A. Authenticate interactively in each needed
   profile. Older WebView sign-ins cannot be imported; existing Gecko sign-ins
   should survive. Open each official extension popup. CS.MONEY's Steam sign-in
   should open a usable login window and return correctly. Skins.com's Enable
   control may not show an access prompt in this app. If so, close the popup and
   use Website access: dismiss once, then allow the listed origins and reopen the
   popup. Allow Android notifications for CS.MONEY when asked.
5. **Check real extension features.** On Steam, verify CSFloat item injection and
   the official popup's tracking state. On CS.MONEY and Skins.com, verify the site
   recognizes its extension and the popup recognizes the correct session. Check
   account refresh, relevant status/notification behavior and a normal safe
   marketplace workflow you already use. Do not make a payment or trade solely
   for this test. Mark unavailable workflows UNTESTED, never PASS.
6. **Check isolation and persistence.** Under B, repeat sign-in for the websites
   being tested, install/enable its own extensions, and confirm it never shows A. Switch A -> B -> A three times.
   Close/reopen the browser, rotate the phone, switch away for five minutes, then
   reopen. Force-stop the app through Android Settings and reopen A and B. Each
   must restore its own sign-in, extension state and tracking status. Include the
   same website under both accounts, with both extensions installed.
7. **Check revocation.** Under A, disable an extension and confirm its features
   stop. B must retain its own state. Restart: A stays disabled. Enable A, then
   uninstall/reinstall A; installation consent must appear again. Repeat for
   CSFloat, CS.MONEY and Skins.com. For Skins.com, use Website access to revoke
   the listed optional origins, restart, and confirm access stays revoked until
   you explicitly allow it again.
8. **Check lock and recovery.** Check PIN/biometric app lock. Disconnect the network
   while loading a public page, reconnect, then use Try again/Refresh. The app
   must recover without changing the selected account.

Only the active browser session has a running Gecko worker. Switching profiles or
force-stopping the app stops the previous worker; this release does not promise
simultaneous tracking for every saved account or tracking while force-stopped.

## Send back this sanitized result

```text
APK filename / SHA-256:
Device / Android version:
Steam / CSFloat / CS.MONEY / Skins.com / CSGOEmpire / custom page: PASS or failure
CSFloat injection + signed-in popup/tracking:
CS.MONEY login helper + extension recognition + refresh/notifications:
Skins.com host permission deny/allow + connection/refresh:
A/B isolation + rotation + five-minute return + force-stop:
Disable / enable / uninstall / reinstall (each extension):
Unexpected reauthentication (which website, no account identifiers):
PIN/biometric + offline recovery:
Untested workflows:
Overall: PASS / FAIL / INCOMPLETE
```

Trade Token Sync is outside this release and tracked in
[#17](https://github.com/D4gkan/Steam-Account-Manager-App/issues/17).
The PR remains Draft until required physical-device acceptance and final independent
checks pass. Merge and public APK publication are later maintainer actions.
