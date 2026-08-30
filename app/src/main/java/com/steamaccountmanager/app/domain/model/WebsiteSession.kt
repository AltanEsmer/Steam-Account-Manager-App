package com.steamaccountmanager.app.domain.model

/**
 * Identifies exactly one isolated browser session: one account, on one website.
 *
 * This is the unit of isolation everywhere in the app -- NOT the account alone.
 * See [com.steamaccountmanager.app.browser.SessionManager] for how this maps to an
 * actual on-disk WebView data directory.
 */
data class SessionIdentifier(
    val accountId: String,
    val websiteId: String,
) {
    /**
     * A filesystem/process-safe suffix derived from account + website.
     * Passed to WebView.setDataDirectorySuffix() -- must contain no path separators,
     * so account/website ids (which are app-generated UUIDs or slugs) are lightly
     * sanitized defensively.
     */
    val dataDirectorySuffix: String
        get() = "acc_${sanitize(accountId)}_site_${sanitize(websiteId)}"

    private fun sanitize(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(48)

    companion object {
        fun fromSuffix(suffix: String): SessionIdentifier? {
            val regex = Regex("^acc_(.+)_site_(.+)$")
            val match = regex.find(suffix) ?: return null
            val (accountId, websiteId) = match.destructured
            return SessionIdentifier(accountId, websiteId)
        }
    }
}

/** Persisted metadata about a session (not the session's cookies/storage themselves). */
data class WebsiteSessionMeta(
    val accountId: String,
    val websiteId: String,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long,
    /** True once the WebView data directory for this session has actually been created. */
    val hasStoredData: Boolean,
)
