package com.rajatmalik.commitgenerator.logging

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.rajatmalik.commitgenerator.config.CommitGeneratorSettings
import com.rajatmalik.commitgenerator.core.domain.LogLevel
import com.rajatmalik.commitgenerator.core.ports.LoggerPort
import com.rajatmalik.commitgenerator.core.security.redactSecrets

/**
 * Metadata-only logger. Prompts, diffs, headers, and credentials are never
 * passed here; any stray string value is redacted before it reaches the IDE log.
 * The configured log level is enforced here.
 */
class CommitGeneratorLogger(private val name: String = "Commit Generator") : LoggerPort {
    private val log: Logger = Logger.getInstance(name)

    override fun debug(message: String, metadata: Map<String, Any?>) {
        if (enabled(LogLevel.DEBUG) && log.isDebugEnabled) {
            log.debug(format(message, metadata))
        }
    }

    override fun info(message: String, metadata: Map<String, Any?>) {
        if (enabled(LogLevel.INFO)) {
            log.info(format(message, metadata))
        }
    }

    override fun warn(message: String, metadata: Map<String, Any?>) {
        if (enabled(LogLevel.WARN)) {
            log.warn(format(message, metadata))
        }
    }

    override fun error(message: String, metadata: Map<String, Any?>) {
        if (enabled(LogLevel.ERROR)) {
            log.error(format(message, metadata))
        }
    }

    private fun enabled(level: LogLevel): Boolean {
        val configured = runCatching {
            service<CommitGeneratorSettings>().read().logLevel
        }.getOrDefault(LogLevel.WARN)
        if (configured == LogLevel.OFF) {
            return false
        }
        return level.ordinal <= configured.ordinal
    }

    private fun format(message: String, metadata: Map<String, Any?>): String {
        if (metadata.isEmpty()) {
            return message
        }
        val fields = metadata.entries.joinToString(", ") { (key, value) ->
            val safe = when (value) {
                null -> "null"
                is Number, is Boolean -> value.toString()
                else -> redactSecrets(value.toString()).text.replace(Regex("\\s+"), " ").take(160)
            }
            "$key=$safe"
        }
        return "$message [$fields]"
    }
}
