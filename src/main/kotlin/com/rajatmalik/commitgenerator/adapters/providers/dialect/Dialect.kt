package com.rajatmalik.commitgenerator.adapters.providers.dialect

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import com.rajatmalik.commitgenerator.core.ports.GenerateResult
import com.rajatmalik.commitgenerator.core.ports.ModelInfo

data class DialectContext(
    val profile: ProviderProfile,
    val authHeaders: Map<String, String>,
)

data class DialectRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

interface Dialect {
    val id: WireDialect
    fun buildGenerate(context: DialectContext, request: GenerateRequest): DialectRequest
    fun parseGenerate(status: Int, text: String): CwResult<GenerateResult>
    fun buildModels(context: DialectContext): DialectRequest?
    fun parseModels(status: Int, text: String): CwResult<List<ModelInfo>>
}

fun jsonHeaders(authHeaders: Map<String, String>): Map<String, String> =
    linkedMapOf<String, String>().apply {
        put("content-type", "application/json")
        putAll(authHeaders)
    }

fun baseUrlOf(profile: ProviderProfile): String = profile.baseUrl.trimEnd('/')

fun appendPath(base: String, path: String): String = base.trimEnd('/') + "/" + path.trimStart('/')
