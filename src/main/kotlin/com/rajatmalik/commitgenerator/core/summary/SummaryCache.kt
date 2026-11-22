package com.rajatmalik.commitgenerator.core.summary

import com.rajatmalik.commitgenerator.core.ports.ClockPort

/** FNV-1a 32-bit hash plus length; deterministic and dependency-free. */
fun hashText(input: String): String {
    var hash = 0x811c9dc5u
    for (char in input) {
        hash = hash xor char.code.toUInt()
        hash *= 0x01000193u
    }
    return hash.toString(16).padStart(8, '0') + "-" + input.length.toString(16)
}

private data class CacheEntry(
    val value: String,
    val at: Long,
)

/**
 * Bounded LRU cache with a TTL. Used to avoid re-summarizing an unchanged
 * chunk for the same model and prompt version.
 */
class SummaryCache(
    private val clock: ClockPort,
    private val maxEntries: Int = 200,
    private val ttlMs: Long = 30 * 60 * 1000L,
) {
    private val entries = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {}

    @Synchronized
    fun get(key: String): String? {
        val entry = entries[key] ?: return null
        if (clock.now() - entry.at > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun set(key: String, value: String) {
        entries[key] = CacheEntry(value, clock.now())
        while (entries.size > maxEntries) {
            val eldest = entries.entries.firstOrNull() ?: break
            entries.remove(eldest.key)
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
