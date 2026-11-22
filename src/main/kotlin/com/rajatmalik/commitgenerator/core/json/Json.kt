package com.rajatmalik.commitgenerator.core.json

/**
 * Minimal, dependency-free JSON parser and writer. The product intentionally
 * ships zero third-party runtime libraries, so provider payloads are handled by
 * this small and fully tested implementation.
 */
sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

object Json {
    fun parse(text: String): JsonValue? = runCatching { Parser(text).parseDocument() }.getOrNull()

    fun stringify(value: Any?): String = buildString { Writer(this).write(value) }

    fun obj(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*fields)

    fun arr(vararg values: Any?): List<Any?> = values.toList()
}

fun JsonValue.asObjectOrNull(): Map<String, JsonValue>? = (this as? JsonValue.Obj)?.fields

fun JsonValue.asArrayOrNull(): List<JsonValue>? = (this as? JsonValue.Arr)?.items

fun JsonValue.asStringOrNull(): String? = (this as? JsonValue.Str)?.value

fun JsonValue.asNumberOrNull(): Double? = (this as? JsonValue.Num)?.value

fun JsonValue.asIntOrNull(): Int? = (this as? JsonValue.Num)?.value?.toInt()

fun JsonValue.asBooleanOrNull(): Boolean? = (this as? JsonValue.Bool)?.value

fun JsonValue.stringOrNull(): String? = asStringOrNull()

fun JsonValue.objectOrNull(): Map<String, JsonValue>? = asObjectOrNull()

fun Map<String, JsonValue>.string(key: String): String? = this[key]?.asStringOrNull()

fun Map<String, JsonValue>.int(key: String): Int? = this[key]?.asIntOrNull()

fun Map<String, JsonValue>.number(key: String): Double? = this[key]?.asNumberOrNull()

fun Map<String, JsonValue>.obj(key: String): Map<String, JsonValue>? = this[key]?.asObjectOrNull()

fun Map<String, JsonValue>.array(key: String): List<JsonValue> = this[key]?.asArrayOrNull() ?: emptyList()

private class Parser(private val text: String) {
    private var index = 0

    fun parseDocument(): JsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (index != text.length) {
            throw IllegalArgumentException("Trailing content at $index")
        }
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (index >= text.length) {
            throw IllegalArgumentException("Unexpected end of JSON")
        }
        return when (text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't' -> {
                expect("true")
                JsonValue.Bool(true)
            }

            'f' -> {
                expect("false")
                JsonValue.Bool(false)
            }

            'n' -> {
                expect("null")
                JsonValue.Null
            }

            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue {
        index++ // {
        val fields = linkedMapOf<String, JsonValue>()
        skipWhitespace()
        if (peek('}')) {
            index++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (!peek('"')) {
                throw IllegalArgumentException("Expected object key at $index")
            }
            val key = parseString()
            skipWhitespace()
            if (!peek(':')) {
                throw IllegalArgumentException("Expected ':' at $index")
            }
            index++
            fields[key] = parseValue()
            skipWhitespace()
            if (peek(',')) {
                index++
                continue
            }
            if (peek('}')) {
                index++
                return JsonValue.Obj(fields)
            }
            throw IllegalArgumentException("Expected ',' or '}' at $index")
        }
    }

    private fun parseArray(): JsonValue {
        index++ // [
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek(']')) {
            index++
            return JsonValue.Arr(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            if (peek(',')) {
                index++
                continue
            }
            if (peek(']')) {
                index++
                return JsonValue.Arr(items)
            }
            throw IllegalArgumentException("Expected ',' or ']' at $index")
        }
    }

    private fun parseString(): String {
        index++ // opening quote
        val out = StringBuilder()
        while (index < text.length) {
            val char = text[index++]
            when (char) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= text.length) {
                        throw IllegalArgumentException("Unterminated escape")
                    }
                    when (val escaped = text[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 > text.length) {
                                throw IllegalArgumentException("Invalid unicode escape")
                            }
                            val hex = text.substring(index, index + 4)
                            index += 4
                            out.append(hex.toInt(16).toChar())
                        }

                        else -> throw IllegalArgumentException("Invalid escape \\$escaped")
                    }
                }

                else -> out.append(char)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseNumber(): JsonValue {
        val start = index
        if (peek('-')) {
            index++
        }
        while (index < text.length && text[index].isDigit()) {
            index++
        }
        if (peek('.')) {
            index++
            while (index < text.length && text[index].isDigit()) {
                index++
            }
        }
        if (peek('e') || peek('E')) {
            index++
            if (peek('+') || peek('-')) {
                index++
            }
            while (index < text.length && text[index].isDigit()) {
                index++
            }
        }
        if (start == index) {
            throw IllegalArgumentException("Invalid value at $index")
        }
        return JsonValue.Num(text.substring(start, index).toDouble())
    }

    private fun expect(value: String) {
        if (!text.startsWith(value, index)) {
            throw IllegalArgumentException("Expected $value at $index")
        }
        index += value.length
    }

    private fun peek(char: Char): Boolean = index < text.length && text[index] == char

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
    }
}

private class Writer(private val out: StringBuilder) {
    fun write(value: Any?) {
        when (value) {
            null -> out.append("null")
            is JsonValue -> writeJsonValue(value)
            is String -> writeString(value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Number -> {
                val double = value.toDouble()
                if (double.isFinite()) {
                    if (double == double.toLong().toDouble()) {
                        out.append(double.toLong())
                    } else {
                        out.append(double)
                    }
                } else {
                    out.append("null")
                }
            }

            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, entry ->
                    if (index > 0) out.append(',')
                    writeString(entry.key.toString())
                    out.append(':')
                    write(entry.value)
                }
                out.append('}')
            }

            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item)
                }
                out.append(']')
            }

            is Array<*> -> write(value.asList())
            else -> writeString(value.toString())
        }
    }

    private fun writeJsonValue(value: JsonValue) {
        when (value) {
            is JsonValue.Obj -> {
                out.append('{')
                value.fields.entries.forEachIndexed { index, entry ->
                    if (index > 0) out.append(',')
                    writeString(entry.key)
                    out.append(':')
                    writeJsonValue(entry.value)
                }
                out.append('}')
            }

            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeJsonValue(item)
                }
                out.append(']')
            }

            is JsonValue.Str -> writeString(value.value)
            is JsonValue.Num -> write(value.value)
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            JsonValue.Null -> out.append("null")
        }
    }

    private fun writeString(value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char.code < 0x20) {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }
}
