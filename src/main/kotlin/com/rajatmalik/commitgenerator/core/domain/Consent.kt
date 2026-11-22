package com.rajatmalik.commitgenerator.core.domain

data class ConsentRoute(
    val providerId: String,
    val modelId: String,
)

data class EgressConsent(
    val origin: String,
    val providerId: String,
    val routedVia: ConsentRoute? = null,
    val grantedAt: Long,
)

data class ConsentRequest(
    val origin: String,
    val providerId: String,
    val model: String,
    val mode: GenerationMode,
    val estimatedTokens: Int,
    val routedVia: ConsentRoute? = null,
)

fun consentMatches(consent: EgressConsent, request: ConsentRequest): Boolean {
    if (consent.origin != request.origin || consent.providerId != request.providerId) {
        return false
    }
    val consentRoute = consent.routedVia?.let { "${it.providerId}/${it.modelId}" }
    val requestRoute = request.routedVia?.let { "${it.providerId}/${it.modelId}" }
    return consentRoute == requestRoute
}
