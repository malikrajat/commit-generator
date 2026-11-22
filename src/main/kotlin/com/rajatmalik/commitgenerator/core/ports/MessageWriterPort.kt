package com.rajatmalik.commitgenerator.core.ports

enum class WritePolicy {
    REPLACE,
    APPEND,
}

data class WriteOutcome(
    val written: Boolean,
    val previous: String = "",
    val failure: String? = null,
)

/**
 * The only component allowed to touch the commit message field. It has no
 * access to commit, stage, amend or restore operations.
 */
interface MessageWriterPort {
    fun isAvailable(): Boolean
    fun readCurrent(): String?
    fun write(message: String, policy: WritePolicy): WriteOutcome
    fun restore(previous: String)
}
