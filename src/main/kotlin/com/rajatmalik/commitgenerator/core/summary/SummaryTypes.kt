package com.rajatmalik.commitgenerator.core.summary

import com.rajatmalik.commitgenerator.core.context.FileCategory
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.PromptSegment
import com.rajatmalik.commitgenerator.core.ports.SummaryProgressListener

data class SummaryChunk(
    val index: Int,
    val groupKey: String,
    val segments: List<PromptSegment>,
    val files: List<String>,
    val tokens: Int,
    val categories: List<FileCategory>,
)

data class ChunkPlan(
    val chunks: List<SummaryChunk>,
    val totalTokens: Int,
    val tokenLimit: Int,
)

data class SummaryChunkReport(
    val index: Int,
    val groupKey: String,
    val files: List<String>,
    val tokens: Int,
    val text: String? = null,
    val cached: Boolean = false,
    val attempts: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class GroupSummary(
    val key: String,
    val files: List<String>,
    val text: String,
    val analyzed: Boolean,
    val notAnalyzed: List<String>,
)

data class SummaryCoverage(
    val files: Int,
    val analyzed: Int,
    val failed: Int,
    val omitted: Int,
    val truncatedFiles: Int,
)

data class ChangeSummary(
    val chunks: List<SummaryChunkReport>,
    val groups: List<GroupSummary>,
    val texts: List<String>,
    val consolidated: String,
    val coverage: SummaryCoverage,
    val limitations: List<String>,
)

data class SummaryModelRequest(
    val system: String,
    val user: String,
    val maxTokens: Int,
)

interface SummaryModel {
    /** Cache namespace, usually provider id + model id. */
    val cacheKey: String
    fun summarize(request: SummaryModelRequest, signal: CancelSignal): CwResult<String>
}

data class SummaryOptions(
    val system: String,
    val maxTokens: Int,
    val chunkTokens: Int,
    val maxAttempts: Int,
    val cache: SummaryCache? = null,
    val onProgress: SummaryProgressListener? = null,
)
