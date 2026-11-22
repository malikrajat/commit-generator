package com.rajatmalik.commitgenerator.core.ports

import com.rajatmalik.commitgenerator.core.domain.CancelSignal

enum class SummaryPhase {
    SUMMARIZING,
    AGGREGATING,
}

data class SummaryProgress(
    val phase: SummaryPhase,
    val completed: Int,
    val total: Int,
    val label: String,
)

fun interface SummaryProgressListener {
    fun onProgress(event: SummaryProgress)
}

fun interface CancelWatcher {
    fun watch(signal: CancelSignal): AutoCloseable
}
