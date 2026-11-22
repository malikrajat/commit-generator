package com.rajatmalik.commitgenerator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.rajatmalik.commitgenerator.adapters.providers.AuthResolver
import com.rajatmalik.commitgenerator.adapters.providers.ModelCatalog
import com.rajatmalik.commitgenerator.adapters.providers.ProviderRegistry
import com.rajatmalik.commitgenerator.adapters.providers.http.JdkHttpTransport
import com.rajatmalik.commitgenerator.adapters.providers.http.RedactingHttpClient
import com.rajatmalik.commitgenerator.config.CommitGeneratorClock
import com.rajatmalik.commitgenerator.config.CommitGeneratorConsentService
import com.rajatmalik.commitgenerator.config.CommitGeneratorSettings
import com.rajatmalik.commitgenerator.services.generation.GenerateCommitMessageService
import com.rajatmalik.commitgenerator.services.generation.GenerateServiceDeps
import com.rajatmalik.commitgenerator.core.summary.SummaryCache
import com.rajatmalik.commitgenerator.logging.CommitGeneratorLogger
import com.rajatmalik.commitgenerator.secrets.PasswordSafeSecretStore

/**
 * Application-wide composition root. It owns the single HTTP choke point, the
 * provider registry, the model catalog, and the summary cache.
 */
@Service(Service.Level.APP)
class CommitGeneratorApplicationService {
    val settings: CommitGeneratorSettings
        get() = service()
    val secrets: PasswordSafeSecretStore
        get() = service()
    val consent: CommitGeneratorConsentService
        get() = service()
    val logger: CommitGeneratorLogger = CommitGeneratorLogger()
    val http: RedactingHttpClient = RedactingHttpClient(JdkHttpTransport())
    val auth: AuthResolver = AuthResolver(secrets)
    val registry: ProviderRegistry = ProviderRegistry(http, auth)
    val models: ModelCatalog = ModelCatalog(CommitGeneratorClock)
    val summaryCache: SummaryCache = SummaryCache(CommitGeneratorClock)
    val generation: GenerateCommitMessageService = GenerateCommitMessageService(
        GenerateServiceDeps(
            config = settings,
            registry = registry,
            logger = logger,
            clock = CommitGeneratorClock,
            summaryCache = summaryCache,
        ),
    )
}

fun commitGeneratorApplication(): CommitGeneratorApplicationService = service()
