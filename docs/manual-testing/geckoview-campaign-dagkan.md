# GeckoView campaign manual testing for Dagkan

## Current status: INCOMPLETE / STAGNATED — do not run the old APK

Machine validation at source `1dc8802bc91e3f232580c607b5b114d36f12113e` is not
repeatable: the primary's full run passed 16/16 once, then only 14/16 with engine-load
and synthetic marker failures. Narrowing found no background entry after reinstall;
a diagnostics-free experimental repair then failed uninstall persistence in
116.596 seconds and was removed. See the [latest receipt](../verification/geckoview-issue-7-emulator-run.md)
for source attribution and safe local logs. No `GO` exists and production issue #8
remains blocked.

Do not install or test the historical `b81eeab` APK below now. The retained procedure
and metadata are historical preparation, not authorization or a current passing
build. Resume only after Codex supplies a newly verified exact build and explicitly
reopens the device gate; all acceptance criteria below remain required.

Codex gathers the emulator, build, APK-hash, automated-test, and safe technical
screenshot/log evidence. When the device gate is reopened, the user supplies only
the prescribed device-only observed PASS/FAIL results, device model/Android version,
and reauthentication notes. Do not send credentials, cookies, tokens, account
identifiers, trade/payment content, or authenticated screenshots. Missing evidence
remains incomplete rather than being inferred from emulator results.

This is the living human-test guide for the GeckoView migration campaign. Run only
the gate whose APK metadata is complete. A gate passes only when every required
expected result is observed. Mark an unexpected or missing result `FAIL`; do not
change the acceptance criteria to make a run pass.

# Issue #7 compatibility GO/NO-GO

## What changed

A separate debug prototype now opens the official, signed Firefox CSFloat extension
inside GeckoView. Browser state and extension state are stored separately for test
slots A and B, and only the selected slot's browser process remains active. This is
the final compatibility check before any production browser migration may begin.

## Before you start

**Historical prerequisites only — blocked pending a newly verified build. Do not
run the old APK below now.**

- Test exact source commit `b81eeab10ee0554850f51ff9702052ce96ddba19`.
- Use `C:\Users\esmer\AppData\Local\Temp\sam-gv7-navigation-repair-b81eeab\app-debug-4A5FF6D7.apk`.
- The APK is 598,810,380 bytes. Its SHA-256 must be
  `4A5FF6D78B1B9FF75343B2D0681FF473F29A935C811FE3B5DC63F1EE99610755`.
- Run the complete procedure once on the dedicated emulator and once on a supported
  physical Android device running Android 9/API 28 or newer. Record the exact device
  model, Android version, API level, and CPU ABI. Do not use a personal emulator or
  clear a physical device that you do not own for this test.
- Use two dedicated Steam test accounts, called A and B only in the evidence. Neither
  account may have a payment method or valuable inventory. Never type authentication
  information anywhere except Steam's page on the test device.
- Use a stable network and allow enough time for Steam, AMO, and CSFloat to respond.
  A third-party outage is an incomplete run, not a pass.
- The expected pinned components are GeckoView `153.0.20260810162159` and the official
  signed CSFloat `5.17.0`, ID `{194d0dc6-7ada-41c6-88b8-95d7636fe43c}`, from
  `https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi`.
  The XPI SHA-256 is
  `70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D`.
- Do not run against a valuable account. Do not intentionally create, accept, or
  modify a trade. Observe tracking/status only with harmless test-account data.

## Steps

Perform steps 1–33 on the emulator, then repeat steps 1–33 on the physical device.
Use the same target throughout one run.

1. Verify the APK checksum with PowerShell `Get-FileHash -Algorithm SHA256 <path>`.

   Expected: The checksum exactly matches the value in **Before you start**.

2. Install that APK on the current target without installing any other campaign APK.

   Expected: Android installs the debug app successfully.

3. Clear only the debug app's storage from Android Settings.

   Expected: The debug app returns to a clean first-run state; no other app or device
   data is changed.

4. Open the launcher named **Gecko profile isolation prototype**.

   Expected: The router shows `GV-ROUTER-READY`, slot A and B controls, a reopen
   control, and a worker-stop control. This is GV-01.

5. Open synthetic slot A.

   Expected: The worker opens, shows slot A fixed `GV-*` markers including the
   independent navigation marker `nav=A-history`, and lists the pinned GeckoView and
   CSFloat metadata.

6. Select **Open public Steam listing**.

   Expected: The public Steam market listing opens in GeckoView before authentication.

7. Select **Review and install CSFloat**, then choose **Deny** on the consent prompt.

   Expected: The prompt names CSFloat, the exact ID/version, and all required access
   including Steam API host access; denial leaves `GV-CSFLOAT-STATE-ABSENT slot=A`
   with a safe explanation and an enabled retry. This covers GV-05 and GV-06.

8. Select **Reinstall CSFloat with consent**, review the prompt again, and choose
   **Accept**.

   Expected: GeckoView reports the exact official signed ID/version as enabled for
   slot A without claiming a separate optional-runtime permission. This covers GV-02
   and GV-07.

9. Sign in to Steam account A only inside the visible Steam page.

   Expected: Steam completes its normal interactive authentication, including Steam
   Guard if required. The prototype never asks for, echoes, or logs the credentials.
   This is GV-03.

10. Navigate to the harmless public listing or an appropriate test-account inventory
   page where CSFloat normally appears.

   Expected: The page remains inside GeckoView and shows recognizable CSFloat-added
   content such as float/wear information. This is GV-04.

11. Select **Open official CSFloat action**.

    Expected: The real CSFloat popup opens, rather than an app-made imitation.

12. Use the official popup's normal control to enable offer tracking without creating
    or accepting a trade.

    Expected: The popup visibly reports tracking enabled for test account A.

13. Close the popup and select **Record visible official status**.

    Expected: The prototype records a visible active status only after the official
    popup was shown. This is GV-08.

14. Return to the router and open synthetic slot B.

    Expected: The prior worker closes before B opens; B is signed out of account A,
    B's synthetic markers differ, and CSFloat is absent. No A identity or page state
    appears.

15. Install CSFloat in slot B by reviewing and accepting its independent prompt.

    Expected: CSFloat becomes enabled only in B; B required its own informed consent.

16. Sign in to Steam account B only inside the visible Steam page.

    Expected: B authenticates normally and no account A identity appears.

17. Open an appropriate page and enable tracking from the official CSFloat popup for
    account B.

    Expected: CSFloat injection and the official enabled tracking status are visible
    for B without revealing A. This completes the live portion of GV-11.

18. Switch A → B → A → B → A with the router controls.

    Expected: Each slot restores only its own Steam authentication, visible identity,
    page/storage marker, CSFloat install state, extension storage, and tracking state.
    No process-timeout message or cross-account flash appears. This covers GV-11 and
    GV-12.

19. In slot A, select **Disable CSFloat**.

    Expected: A changes to `GV-CSFLOAT-STATE-DISABLED slot=A`; injection and tracking
    stop for A.

20. Open slot B.

    Expected: B remains signed into B with CSFloat and tracking enabled. A's revoke
    did not change B. This is part of GV-13.

21. Return to A, select **Enable CSFloat**, then confirm the relevant page and popup.

    Expected: Only A returns to enabled/injected/tracking-capable state; B remains
    unchanged. This is part of GV-14.

22. In A, select **Uninstall CSFloat**.

    Expected: A changes to `GV-CSFLOAT-STATE-ABSENT slot=A`; injection and tracking
    are absent for A.

23. Open B once more.

    Expected: B is still independently authenticated with CSFloat enabled and its
    prior tracking state intact. This completes GV-13.

24. Return to A, select **Reinstall CSFloat with consent**, choose **Deny**, then use
    the same control again and choose **Accept**.

    Expected: Denial leaves A absent, the retry repeats the full consent, acceptance
    restores only A, and B stays unchanged. This completes GV-14.

25. While A is open, rotate the device or otherwise trigger Android activity
    recreation without clearing app data.

    Expected: A restores the correct authentication, CSFloat state, tracking state,
    page marker, and extension marker without a duplicate install prompt.

26. Select **Close worker screen**, then select **Reopen selected slot** in the router.

    Expected: A restores the same state after screen close/reopen. This is GV-09.

27. Select **Stop worker process** in the router, wait for `GV-WORKER-STOPPED`, then
    select **Reopen selected slot**.

    Expected: Stop completes without a timeout, and A restores the correct isolated
    profile after a new worker starts.

28. Fully stop the debug app from Android Settings, then launch **Gecko profile
    isolation prototype** and select **Reopen selected slot**.

    Expected: A is still selected and restores its authentication, CSFloat install,
    injection, and tracking/alarm state without showing B. This is GV-10.

29. Repeat the activity recreation, screen close/reopen, worker stop/reopen, and full
    app restart with B selected.

    Expected: Every transition restores B only, no A identity appears, and any failure
    is recoverable. This completes the lifecycle matrix.

30. Follow an allowed Steam authentication redirect or link within the configured
    Steam site.

    Expected: Allowed Steam pages and authentication redirects load in-app.

31. While an allowed page is loaded, select **Test blocked navigation**, choose
    **Stay here**, then repeat and choose **Open in external browser**. Also use
    **Test unavailable external handoff** and choose **Open in external browser**.

    Expected: The fixed unrelated destination does not load in the embedded browser;
    the current page remains visible, the message contains no destination details, and
    an external browser opens only after the explicit choice. If it opens in-app, opens
    externally without confirmation, or no external choice exists, mark GV-15 `FAIL`.
    The unavailable-handler fixture must instead show the fixed recoverable
    `GV-EXTERNAL-HANDOFF-UNAVAILABLE` state without exposing the destination.

32. Exercise normal back, forward, and reload behavior on the allowed fixture, then
    activate **Open allowed fixture window**.

    Expected: Each control behaves predictably and never crosses from A to B. Missing
    required controls or broken history means GV-15 `FAIL`. The new-window request
    is routed into the existing selected session in this single-window prototype;
    an unrelated new-window target remains
    blocked with the same explicit external-browser choice.
    Authentication flows using `window.opener`, `postMessage`, or `window.close`
    still require human verification; same-session routing does not prove them.

33. Exercise the labeled popup failure and recovery controls, then observe one real
    recoverable network/load failure if it occurs naturally.

    Expected: Failures show actionable, non-sensitive diagnostics, never falsely show
    tracking active, preserve an external-browser recovery path, and recover after a
    fresh CSFloat query. A natural outage is not required; do not disrupt the device
    or network to manufacture one. This is GV-16.

## If it fails

Stop the current target's run and mark the affected GV row `FAIL` or `INCOMPLETE`.
Record the step number, target metadata, UTC time, visible fixed `GV-*` message, and
the last safe action. Do not retry more than twice for an apparent Steam, AMO, network,
Gradle, adb, or emulator transient; note what changed between retries. Recover by
closing the popup, returning to the router, and using the explicit reopen or recovery
control. If the wrong account appears, stop immediately, close the worker, and do not
continue until the run has been reviewed.

A `FAIL` in any required GV-01–GV-16 row produces `NO-GO`. Missing physical-device,
reviewer, maintainer, or official-issue evidence produces `INCOMPLETE`; it cannot be
treated as `GO`.

## Evidence to share

Share a compact record with:

- `PASS`, `FAIL`, or `INCOMPLETE` for every GV-01 through GV-16 on the emulator and
  physical device;
- exact APK commit, size, and checksum;
- emulator AVD/API/Android/ABI and physical device model/API/Android/ABI;
- GeckoView version and CSFloat source, ID, version, signature state, and checksum;
- UTC test window and a short non-sensitive note for every failed or retried step;
- `PASS` or `FAIL` for screen, activity, browser-process, and full-app recreation;
- representative APK-size and memory observations if available;
- whether either safe account required reauthentication after each lifecycle event;
- redacted screenshots containing only the relevant app/extension state; and
- a maintainer statement of `I acknowledge this evidence and recommend GO` or
  `I acknowledge this evidence and recommend NO-GO`.

Do not share passwords, Steam Guard codes, QR login screens, cookies, tokens, account names, trades, payment information, or unredacted authentication screenshots.

Use only labels A and B in notes. Crop or redact status bars and any identifying page
content. Do not export browser storage or full logcat. The independent tester and
reviewer will check the record before an official decision is requested on issue #7.
The campaign cannot continue until issue #7 itself records the acknowledged `GO` and
the repository's required gate transition is complete.

## What this test does not prove

- One emulator and one physical device do not prove behavior on every Android model,
  vendor image, network, or memory class.
- A short tracking observation does not prove long-running background reliability.
- Passing the prototype does not prove the later production UI, migration, upgrade,
  rollback, or final release build.
- Debug APK size and point-in-time memory readings are not release download size,
  installed size, battery, or performance guarantees.
- The result does not authorize a modified, repacked, forked, silently authorized, or
  substituted CSFloat package.
- Human evidence alone does not change ADR-0001 or unblock issue #8. The official
  repository gate, reviewer acknowledgement, maintainer acknowledgement, and explicit
  `GO` are still required.

# Issue #8 production Steam-login smoke test

## What changed

This section will test the first production GeckoView session only after issue #7 has
an official acknowledged `GO`. No issue #8 APK exists yet.

## Before you start

Do not run this gate yet. The exact issue #8 commit, production-path test APK, size,
SHA-256, supported Android range, and safe-account requirements will replace this
paragraph after issue #8 machine verification.

## Steps

1. Wait for this section to name an exact issue #8 APK and checksum.

   Expected: No production-path physical-device test is performed from a placeholder
   or from the issue #7 prototype APK.

## If it fails

Do not improvise an APK or reuse stale evidence. Report that the guide is incomplete
for issue #8 so the campaign remains at `HUMAN_GATE_REQUIRED`.

## Evidence to share

When this section is activated, share only the requested `PASS`/`FAIL`, exact build
metadata, Android/device information, and redacted screenshots or bounded logs.

Do not share passwords, Steam Guard codes, QR login screens, cookies, tokens, account names, trades, payment information, or unredacted authentication screenshots.

## What this test does not prove

Until the exact issue #8 procedure is committed, this placeholder proves nothing and
does not release issue #9.

# Issue #12 final supported-device acceptance

## What changed

This section will test the final production cutover after issues #8–#12 have passed
their preceding gates and machine verification. No final APK exists yet.

## Before you start

Do not run this gate yet. The exact final commit, release-path test APK, size, SHA-256,
supported-device matrix, and safe-account requirements will replace this paragraph
after issue #12 machine verification.

## Steps

1. Wait for this section to name the exact final APK, checksum, and supported-device
   matrix.

   Expected: No final acceptance run is performed against a placeholder or stale APK.

## If it fails

Do not improvise a build or copy results from an earlier gate. Report that the guide
is incomplete for issue #12 so the Draft PR remains unready.

## Evidence to share

When this section is activated, share only the requested `PASS`/`FAIL`, exact build
metadata, Android/device information, and redacted screenshots or bounded logs.

Do not share passwords, Steam Guard codes, QR login screens, cookies, tokens, account names, trades, payment information, or unredacted authentication screenshots.

## What this test does not prove

Until the exact final procedure is committed, this placeholder proves nothing and the
Draft PR must not be marked ready for upstream review.
