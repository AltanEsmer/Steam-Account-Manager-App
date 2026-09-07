package com.steamaccountmanager.app.browser

import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CsfloatExtensionContract {
    const val XPI_URL =
        "https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi"
    const val ID = "{194d0dc6-7ada-41c6-88b8-95d7636fe43c}"
    const val VERSION = "5.17.0"
    const val SIGNED_STATE = 2
    const val SIZE_BYTES = 7_011_169L
    const val SHA256 = "70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D"

    fun isExpected(id: String?, version: String?, signedState: Int): Boolean =
        id == ID && version == VERSION && signedState == SIGNED_STATE

    fun prompt(
        name: String?,
        id: String?,
        version: String?,
        permissions: List<String>,
        origins: List<String>,
        dataCollection: List<String>,
    ): String = buildList {
        add("Extension: ${name ?: "Unknown"}")
        add("ID: ${id ?: "Unknown"}")
        add("Version: ${version ?: "Unknown"}")
        add("Permissions (${permissions.size}):")
        addAll(permissions.map { "• $it" })
        add("Origins (${origins.size}):")
        addAll(origins.map { "• $it" })
        add("Data collection (${dataCollection.size}):")
        addAll(dataCollection.map { "• $it" })
    }.joinToString("\n")

    suspend fun downloadVerified(directory: File): File = withContext(Dispatchers.IO) {
        val target = File.createTempFile("csfloat-5.17.0-", ".xpi", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            URL(XPI_URL).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                useCaches = false
            }.getInputStream().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > SIZE_BYTES) error("CSFLOAT_ARTIFACT_MISMATCH")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02X".format(it) }
            if (size != SIZE_BYTES || hash != SHA256) error("CSFLOAT_ARTIFACT_MISMATCH")
            target
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }
}

enum class CsfloatTrackingState { UNAVAILABLE, READY, ACTIVE, FAILED }

class CsfloatTracking {
    var state = CsfloatTrackingState.UNAVAILABLE
        private set
    var pendingRequest: Long? = null
        private set
    var officialSurfaceOpened = false
        private set
    private var nextRequest = 1L

    fun unavailable() {
        state = CsfloatTrackingState.UNAVAILABLE
        pendingRequest = null
        officialSurfaceOpened = false
    }

    fun actionAvailable() {
        if (state == CsfloatTrackingState.UNAVAILABLE) state = CsfloatTrackingState.READY
    }

    fun request(click: () -> Unit): Long? {
        if (state !in setOf(CsfloatTrackingState.READY, CsfloatTrackingState.ACTIVE) || pendingRequest != null) return null
        return nextRequest++.also { pendingRequest = it; click() }
    }

    fun popupOpened(request: Long?): Boolean {
        if (request == null || request != pendingRequest) return false
        pendingRequest = null
        officialSurfaceOpened = true
        return true
    }

    fun failed(request: Long? = pendingRequest): Boolean {
        if (request == null || request != pendingRequest) return false
        pendingRequest = null
        officialSurfaceOpened = false
        state = CsfloatTrackingState.FAILED
        return true
    }

    fun discoveryFailed() {
        pendingRequest = null
        officialSurfaceOpened = false
        state = CsfloatTrackingState.FAILED
    }

    fun recover() = unavailable()

    fun recordVisibleStatus() {
        if (state == CsfloatTrackingState.READY && pendingRequest == null && officialSurfaceOpened) {
            state = CsfloatTrackingState.ACTIVE
        }
    }
}
