package io.github.zegamingx.isqu.tokenizer.format.cbor

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

private const val MAX_CBOR_CONTAINER_DEPTH = 4
private const val MAX_CBOR_MAP_ENTRIES = 10
private const val MAX_SPECIAL_TOKEN_CAPACITY = 32

internal sealed class CborValue {
    data class UInt(val value: Long) : CborValue()
    data class Text(val value: String) : CborValue()
    data class Bytes(val value: ByteSlice) : CborValue()
    data class Array(val items: List<CborValue>) : CborValue()
    data class Map(
        val pairs: List<Pair<String, CborValue>>,
        val encodedStart: Int = -1,
        val encodedEnd: Int = -1,
    ) : CborValue() {
        val entries: kotlin.collections.Map<String, CborValue> =
            LinkedHashMap<String, CborValue>().also { result ->
                for ((key, value) in pairs) {
                    result[key] = value
                }
            }
    }
}

/**
 * A view into the caller's encoded artifact. It avoids allocating byte strings
 * before schema-specific limits have been checked.
 */
internal class ByteSlice(
    private val source: ByteArray,
    private val offset: Int,
    val length: Int,
) {
    fun copyToByteArray(): ByteArray = source.copyOfRange(offset, offset + length)

    companion object {
        fun owned(bytes: ByteArray): ByteSlice = ByteSlice(bytes, 0, bytes.size)
    }
}

internal object DeterministicCborWriter {
    fun encode(value: CborValue): ByteArray {
        val output = ByteArrayOutputStream()
        writeValue(value, output)
        return output.toByteArray()
    }

    private fun writeValue(value: CborValue, output: ByteArrayOutputStream) {
        when (value) {
            is CborValue.UInt -> writeUnsigned(0, value.value, output)
            is CborValue.Text -> {
                val bytes = encodeUtf8(value.value)
                writeUnsigned(3, bytes.size.toLong(), output)
                output.write(bytes)
            }
            is CborValue.Bytes -> {
                val bytes = value.value.copyToByteArray()
                writeUnsigned(2, bytes.size.toLong(), output)
                output.write(bytes)
            }
            is CborValue.Array -> {
                writeUnsigned(4, value.items.size.toLong(), output)
                value.items.forEach { writeValue(it, output) }
            }
            is CborValue.Map -> {
                val encodedEntries = value.pairs.map { pair ->
                    EncodedMapEntry(encode(CborValue.Text(pair.first)), pair.second)
                }.sortedWith { first, second ->
                    compareUnsigned(first.keyBytes, second.keyBytes)
                }
                writeUnsigned(5, encodedEntries.size.toLong(), output)
                for (entry in encodedEntries) {
                    output.write(entry.keyBytes)
                    writeValue(entry.value, output)
                }
            }
        }
    }

    private fun writeUnsigned(
        majorType: Int,
        value: Long,
        output: ByteArrayOutputStream,
    ) {
        if (value < 0L) {
            throw TokenizerFormatException("CBOR version-1 integers must be unsigned")
        }
        when {
            value < 24L -> output.write((majorType shl 5) or value.toInt())
            value <= 0xffL -> {
                output.write((majorType shl 5) or 24)
                output.write(value.toInt())
            }
            value <= 0xffffL -> {
                output.write((majorType shl 5) or 25)
                output.write((value ushr 8).toInt())
                output.write(value.toInt())
            }
            value <= 0xffff_ffffL -> {
                output.write((majorType shl 5) or 26)
                for (shift in 24 downTo 0 step 8) {
                    output.write((value ushr shift).toInt())
                }
            }
            else -> {
                output.write((majorType shl 5) or 27)
                for (shift in 56 downTo 0 step 8) {
                    output.write((value ushr shift).toInt())
                }
            }
        }
    }

    private fun encodeUtf8(value: String): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val encoded = encoder.encode(java.nio.CharBuffer.wrap(value))
            val output = ByteArray(encoded.remaining())
            encoded.get(output)
            output
        } catch (exception: CharacterCodingException) {
            throw TokenizerFormatException("Snapshot text contains malformed UTF-16", exception)
        }
    }

    private data class EncodedMapEntry(
        val keyBytes: ByteArray,
        val value: CborValue,
    )

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        val commonLength = minOf(first.size, second.size)
        for (index in 0 until commonLength) {
            val a = first[index].toInt() and 0xff
            val b = second[index].toInt() and 0xff
            if (a != b) {
                return a.compareTo(b)
            }
        }
        return first.size.compareTo(second.size)
    }
}

internal class DeterministicCborReader(
    private val data: ByteArray,
    private val limits: TokenizerLoadLimits,
) {
    private var offset = 0
    private var provenanceBudgetStart: Int? = null

    fun readDocument(): CborValue {
        val value = readValue(0)
        if (offset != data.size) {
            throw TokenizerFormatException("Trailing bytes follow the CBOR document")
        }
        return value
    }

    private fun readValue(depth: Int): CborValue {
        if (depth > MAX_CBOR_CONTAINER_DEPTH) {
            throw TokenizerFormatException("CBOR container nesting exceeds depth four")
        }
        val initial = readByte()
        val majorType = initial ushr 5
        val additionalInfo = initial and 0x1f
        val argument = readArgument(additionalInfo)
        ensureProvenanceBudget(offset)

        return when (majorType) {
            0 -> CborValue.UInt(argument)
            2 -> {
                val length = boundedLength(argument, "byte string")
                ensureRemaining(length)
                ensureProvenanceBudget(offset + length)
                val value = ByteSlice(data, offset, length)
                offset += length
                CborValue.Bytes(value)
            }
            3 -> {
                val length = boundedLength(argument, "text string")
                if (length > limits.maxTextStringBytes) {
                    throw TokenizerResourceLimitException(
                        "CBOR text string exceeds maxTextStringBytes ${limits.maxTextStringBytes}",
                    )
                }
                ensureRemaining(length)
                ensureProvenanceBudget(offset + length)
                val text = decodeUtf8(data, offset, length)
                offset += length
                CborValue.Text(text)
            }
            4 -> {
                val length = boundedLength(argument, "array")
                val maxArrayLength = maxOf(
                    MAX_SPECIAL_TOKEN_CAPACITY,
                    limits.maxMergeablePieceCount,
                )
                if (length > maxArrayLength || length > data.size - offset) {
                    throw TokenizerResourceLimitException("CBOR array exceeds configured bounds")
                }
                ensureProvenanceBudget(offset + length)

                val items = ArrayList<CborValue>(length)
                repeat(length) {
                    items.add(readValue(depth + 1))
                }
                CborValue.Array(items)
            }
            5 -> readMap(argument, depth)
            else -> throw TokenizerFormatException(
                "CBOR major type $majorType is not permitted by the version-1 profile",
            )
        }
    }

    private fun readMap(argument: Long, depth: Int): CborValue.Map {
        val length = boundedLength(argument, "map")
        if (length > MAX_CBOR_MAP_ENTRIES || length > (data.size - offset) / 2) {
            throw TokenizerFormatException("CBOR map exceeds the version-1 schema bounds")
        }

        val mapHeaderLength = headerLengthForArgument(argument)
        val start = offset - mapHeaderLength
        ensureProvenanceBudget(offset + (length * 2))
        val entries = ArrayList<Pair<String, CborValue>>(length)
        val seen = HashSet<String>()
        var previousEncodedKey: ByteArray? = null

        repeat(length) {
            val keyStart = offset
            val key = readValue(depth + 1) as? CborValue.Text
                ?: throw TokenizerFormatException("CBOR map keys must be UTF-8 text strings")
            val keyEnd = offset
            val encodedKey = data.copyOfRange(keyStart, keyEnd)
            val previous = previousEncodedKey
            if (previous != null && compareUnsigned(previous, encodedKey) >= 0) {
                throw TokenizerFormatException(
                    "CBOR map keys must be unique and sorted by deterministic encoded-key bytes",
                )
            }
            previousEncodedKey = encodedKey
            if (!seen.add(key.value)) {
                throw TokenizerFormatException("Duplicate CBOR map key: ${key.value}")
            }
            entries.add(key.value to readValue(depth + 1))
        }

        return CborValue.Map(entries, start, offset)
    }

    private fun readArgument(additionalInfo: Int): Long {
        return when (additionalInfo) {
            in 0..23 -> additionalInfo.toLong()
            24 -> {
                val value = readByte().toLong()
                if (value < 24L) {
                    throw TokenizerFormatException("CBOR integer or length is not shortest-form encoded")
                }
                value
            }
            25 -> {
                val value = (readByte().toLong() shl 8) or readByte().toLong()
                if (value <= 0xffL) {
                    throw TokenizerFormatException("CBOR integer or length is not shortest-form encoded")
                }
                value
            }
            26 -> {
                var value = 0L
                repeat(4) { value = (value shl 8) or readByte().toLong() }
                if (value <= 0xffffL) {
                    throw TokenizerFormatException("CBOR integer or length is not shortest-form encoded")
                }
                value
            }
            27 -> {
                if ((peekByte() and 0x80) != 0) {
                    throw TokenizerFormatException(
                        "CBOR unsigned integer exceeds the supported signed-Long range",
                    )
                }
                var value = 0L
                repeat(8) { value = (value shl 8) or readByte().toLong() }
                if (value <= 0xffff_ffffL) {
                    throw TokenizerFormatException("CBOR integer or length is not shortest-form encoded")
                }
                value
            }
            31 -> throw TokenizerFormatException("Indefinite-length CBOR items are not permitted")
            else -> throw TokenizerFormatException("Reserved CBOR additional-information value")
        }
    }

    private fun ensureProvenanceBudget(endOffset: Int) {
        val start = provenanceBudgetStart ?: return
        if (endOffset - start > limits.maxProvenanceBytes) {
            throw TokenizerResourceLimitException(
                "Provenance exceeds maxProvenanceBytes ${limits.maxProvenanceBytes}",
            )
        }
    }

    private fun boundedLength(value: Long, location: String): Int {
        if (value > Int.MAX_VALUE.toLong()) {
            throw TokenizerFormatException("CBOR $location length exceeds Int.MAX_VALUE")
        }
        return value.toInt()
    }

    private fun readByte(): Int {
        if (offset >= data.size) {
            throw TokenizerFormatException("Unexpected end of CBOR input")
        }
        return data[offset++].toInt() and 0xff
    }

    private fun peekByte(): Int {
        if (offset >= data.size) {
            throw TokenizerFormatException("Unexpected end of CBOR input")
        }
        return data[offset].toInt() and 0xff
    }

    private fun ensureRemaining(length: Int) {
        if (length > data.size - offset) {
            throw TokenizerFormatException("CBOR item length exceeds remaining input")
        }
    }

    private fun headerLengthForArgument(argument: Long): Int {
        return when {
            argument < 24L -> 1
            argument <= 0xffL -> 2
            argument <= 0xffffL -> 3
            argument <= 0xffff_ffffL -> 5
            else -> 9
        }
    }

    private fun decodeUtf8(bytes: ByteArray, start: Int, length: Int): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, start, length)).toString()
        } catch (exception: CharacterCodingException) {
            throw TokenizerFormatException("CBOR text string contains malformed UTF-8", exception)
        }
    }

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        val commonLength = minOf(first.size, second.size)
        for (index in 0 until commonLength) {
            val a = first[index].toInt() and 0xff
            val b = second[index].toInt() and 0xff
            if (a != b) {
                return a.compareTo(b)
            }
        }
        return first.size.compareTo(second.size)
    }
}
