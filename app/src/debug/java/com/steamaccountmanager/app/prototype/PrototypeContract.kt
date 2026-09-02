package com.steamaccountmanager.app.prototype

enum class ConsentDecision { PENDING, DENIED, ACCEPTED }

@ConsistentCopyVisibility
data class PrototypeConsent private constructor(
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollectionPermissions: List<String>,
    val decision: ConsentDecision,
) {
    val isAccepted: Boolean get() = decision == ConsentDecision.ACCEPTED

    fun deny() = copy(decision = ConsentDecision.DENIED)

    fun allow() = copy(decision = ConsentDecision.ACCEPTED)

    companion object {
        fun pending(
            permissions: List<String>,
            origins: List<String>,
            dataCollectionPermissions: List<String>,
        ) = PrototypeConsent(
            permissions.toList(),
            origins.toList(),
            dataCollectionPermissions.toList(),
            ConsentDecision.PENDING,
        )
    }
}

object PrototypeDiagnostic {
    fun installFailure(@Suppress("UNUSED_PARAMETER") cause: Throwable?) =
        "Extension installation failed. Retry or open the page externally."

    const val LOAD_FAILURE = "Steam page failed to load. Retry or open it externally."
}
