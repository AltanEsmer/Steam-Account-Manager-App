package com.steamaccountmanager.app.browser

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import org.json.JSONObject

/**
 * Best-effort detection of a successful Steam login inside the isolated Steam
 * WebView session, so the app can automatically fetch the account's avatar
 * (Section 6). This deliberately does NOT call any Steam Web API (which would
 * require an API key / backend) -- it just reads the logged-in page's own DOM,
 * the same information already visible to the user on screen.
 *
 * This is inherently a heuristic: Steam can change its page markup at any time,
 * in which case avatar detection will simply fail silently and the account will
 * show its default placeholder avatar. The account itself is never blocked or
 * broken by a detection failure -- this is a nice-to-have, not a requirement for
 * the session to work.
 */
object SteamLoginDetector {

    const val ACTION_STEAM_PROFILE_DETECTED = "com.steamaccountmanager.app.action.STEAM_PROFILE_DETECTED"
    const val EXTRA_ACCOUNT_ID = "extra_account_id"
    const val EXTRA_AVATAR_URL = "extra_avatar_url"
    const val EXTRA_STEAM_PROFILE_ID = "extra_steam_profile_id"

    private const val DETECTION_SCRIPT = """
        (function() {
            try {
                var avatarEl = document.querySelector('.playerAvatar img, .persona_name_text_content img, a.user_avatar img');
                var avatarUrl = avatarEl ? avatarEl.src : null;
                var profileEl = document.querySelector('a.user_avatar, a.persona_name');
                var profileUrl = profileEl ? profileEl.href : null;
                return JSON.stringify({avatarUrl: avatarUrl, profileUrl: profileUrl});
            } catch (e) {
                return JSON.stringify({avatarUrl: null, profileUrl: null});
            }
        })();
    """

    /** Only worth attempting on steamcommunity.com pages that look like a logged-in view. */
    fun looksLikeLoggedInSteamPage(url: String?): Boolean {
        if (url == null) return false
        return url.contains("steamcommunity.com") &&
            !url.contains("/login") &&
            (url.contains("/id/") || url.contains("/profiles/") || url == "https://steamcommunity.com/")
    }

    fun tryDetect(webView: WebView, accountId: String, appContext: Context) {
        webView.evaluateJavascript(DETECTION_SCRIPT) { rawResult ->
            val unquoted = rawResult?.trim('"')?.replace("\\\"", "\"") ?: return@evaluateJavascript
            if (unquoted == "null" || unquoted.isBlank()) return@evaluateJavascript
            try {
                val json = JSONObject(unquoted)
                val avatarUrl = json.optString("avatarUrl", null.toString()).takeIf { it != "null" && it.isNotBlank() }
                val profileUrl = json.optString("profileUrl", null.toString()).takeIf { it != "null" && it.isNotBlank() }
                if (avatarUrl == null && profileUrl == null) return@evaluateJavascript

                val steamProfileId = profileUrl
                    ?.substringAfter("/id/", missingDelimiterValue = "")
                    ?.ifBlank { profileUrl.substringAfter("/profiles/", missingDelimiterValue = "") }
                    ?.trimEnd('/')

                val intent = Intent(ACTION_STEAM_PROFILE_DETECTED).apply {
                    setPackage(appContext.packageName)
                    putExtra(EXTRA_ACCOUNT_ID, accountId)
                    putExtra(EXTRA_AVATAR_URL, avatarUrl)
                    putExtra(EXTRA_STEAM_PROFILE_ID, steamProfileId)
                }
                appContext.sendBroadcast(intent)
            } catch (_: Exception) {
                // Steam changed its markup or returned something unexpected -- ignore.
            }
        }
    }
}
