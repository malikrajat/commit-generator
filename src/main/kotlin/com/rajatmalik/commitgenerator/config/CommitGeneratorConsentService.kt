package com.rajatmalik.commitgenerator.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.rajatmalik.commitgenerator.core.domain.ConsentRequest
import com.rajatmalik.commitgenerator.core.domain.EgressConsent
import com.rajatmalik.commitgenerator.core.domain.consentMatches
import com.rajatmalik.commitgenerator.core.ports.ConsentPort

@Service(Service.Level.APP)
class CommitGeneratorConsentService : ConsentPort {
    private val settings: CommitGeneratorSettings
        get() = service()

    override fun isGranted(request: ConsentRequest): Boolean =
        settings.consentRecords().any { consentMatches(it, request) }

    override fun record(request: ConsentRequest) {
        val existing = settings.consentRecords().filterNot { consentMatches(it, request) }
        settings.writeConsentRecords(
            existing + EgressConsent(
                origin = request.origin,
                providerId = request.providerId,
                routedVia = request.routedVia,
                grantedAt = CommitGeneratorClock.now(),
            ),
        )
    }

    override fun revokeAll() {
        settings.writeConsentRecords(emptyList())
    }

    override fun list(): List<EgressConsent> = settings.consentRecords()
}

object CommitGeneratorClock : com.rajatmalik.commitgenerator.core.ports.ClockPort {
    override fun now(): Long = System.currentTimeMillis()
}
