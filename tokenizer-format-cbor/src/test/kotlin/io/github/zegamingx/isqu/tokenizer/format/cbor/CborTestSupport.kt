package io.github.zegamingx.isqu.tokenizer.format.cbor

import java.io.ByteArrayOutputStream
import java.util.Arrays

/** Caller-selected limits that are generous enough for every hand-built vector. */
internal val TEST_LIMITS = TokenizerLoadLimits(
    maxArtifactBytes = 1_000_000,
    maxMergeablePieceCount = 10_000,
    maxIndividualPieceBytes = 4_096,
    maxCumulativeLearnedPieceBytes = 1_000_000L,
    maxSpecialTokenRenderBytes = 4_096,
    maxTextStringBytes = 4_096,
    maxProvenanceBytes = 16_384,
)

/** Builds a byte array from integer literals so vectors can be written as plain hex. */
internal fun bytesOf(vararg values: Int): ByteArray {
    return ByteArray(values.size) { index -> values[index].toByte() }
}

/**
 * A deliberately small CBOR writer used only by tests.
 *
 * It is written from RFC 8949 and shares no code with the production writer, so vectors built
 * with it double as an independent cross-check of the production encoder and decoder. Unlike the
 * production writer it can also emit key orders and shapes that the version-1 profile forbids.
 */
internal object TestCbor {
    fun head(majorType: Int, value: Long): ByteArray {
        require(value in 0L..0xffff_ffffL) { "The test builder supports values up to 2^32 - 1" }
        val major = majorType shl 5
        return when {
            value < 24L -> byteArrayOf((major or value.toInt()).toByte())
            value <= 0xffL -> byteArrayOf((major or 24).toByte(), value.toByte())
            value <= 0xffffL -> byteArrayOf(
                (major or 25).toByte(),
                (value shr 8).toByte(),
                value.toByte(),
            )
            else -> byteArrayOf(
                (major or 26).toByte(),
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte(),
            )
        }
    }

    fun uint(value: Long): ByteArray = head(0, value)

    fun bytes(content: ByteArray): ByteArray = head(2, content.size.toLong()) + content

    fun text(value: String): ByteArray {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        return head(3, utf8.size.toLong()) + utf8
    }

    fun array(items: List<ByteArray>): ByteArray = head(4, items.size.toLong()) + concat(items)

    /** A map with entries sorted by the bytewise order of their encoded keys (RFC 8949 4.2.1). */
    fun canonicalMap(entries: Map<String, ByteArray>): ByteArray {
        val encoded = entries
            .map { entry -> text(entry.key) to entry.value }
            .sortedWith { first, second -> Arrays.compareUnsigned(first.first, second.first) }
        return orderedMap(encoded)
    }

    /** A map with entries exactly in the given order, even when that order is not allowed. */
    fun orderedMap(entries: List<Pair<ByteArray, ByteArray>>): ByteArray {
        val parts = entries.flatMap { entry -> listOf(entry.first, entry.second) }
        return head(5, entries.size.toLong()) + concat(parts)
    }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        for (part in parts) {
            output.write(part)
        }
        return output.toByteArray()
    }
}
