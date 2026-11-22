package io.github.zegamingx.isqu.tokenizer.format.cbor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

/**
 * Structural vectors for the version-1 CBOR profile (see docs/tokenizer-format.md):
 * shortest-form integers and lengths, definite lengths only, no tags, floats, or simple
 * values, text keys in encoded-key order without duplicates, strict UTF-8, bounded nesting,
 * and declared lengths that are checked before anything is read or allocated.
 */
class DeterministicCborReaderTest {
    private fun read(document: ByteArray, limits: TokenizerLoadLimits = TEST_LIMITS): CborValue {
        return DeterministicCborReader(document, limits).readDocument()
    }

    private fun assertAllRejected(vectors: List<ByteArray>) {
        for ((index, vector) in vectors.withIndex()) {
            assertThrows(
                TokenizerFormatException::class.java,
                Executable { read(vector) },
                "vector #$index must be rejected",
            )
        }
    }

    private fun nestedMaps(levels: Int): ByteArray {
        var value = TestCbor.uint(0)
        repeat(levels) { value = TestCbor.canonicalMap(mapOf("a" to value)) }
        return value
    }

    private fun mapWithEntries(count: Int): ByteArray {
        val entries = ('a'..'z').take(count).associate { letter ->
            letter.toString() to TestCbor.uint(0)
        }
        return TestCbor.canonicalMap(entries)
    }

    @Test
    fun shortestFormIntegersAreAccepted() {
        assertEquals(CborValue.UInt(0uL), read(bytesOf(0x00)))
        assertEquals(CborValue.UInt(23uL), read(bytesOf(0x17)))
        assertEquals(CborValue.UInt(24uL), read(bytesOf(0x18, 0x18)))
        assertEquals(CborValue.UInt(255uL), read(bytesOf(0x18, 0xff)))
        assertEquals(CborValue.UInt(256uL), read(bytesOf(0x19, 0x01, 0x00)))
        assertEquals(CborValue.UInt(65_535uL), read(bytesOf(0x19, 0xff, 0xff)))
        assertEquals(CborValue.UInt(65_536uL), read(bytesOf(0x1a, 0x00, 0x01, 0x00, 0x00)))
        assertEquals(CborValue.UInt(4_294_967_295uL), read(bytesOf(0x1a, 0xff, 0xff, 0xff, 0xff)))
        assertEquals(CborValue.UInt(4_294_967_296uL), read(bytesOf(0x1b, 0, 0, 0, 1, 0, 0, 0, 0)))
        assertEquals(
            CborValue.UInt(ULong.MAX_VALUE),
            read(bytesOf(0x1b, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)),
        )
    }

    @Test
    fun nonShortestIntegersAndLengthsAreRejected() {
        assertAllRejected(
            listOf(
                bytesOf(0x18, 0x00), // 0 in one-byte form
                bytesOf(0x18, 0x17), // 23 in one-byte form
                bytesOf(0x19, 0x00, 0xff), // 255 in two-byte form
                bytesOf(0x1a, 0x00, 0x00, 0xff, 0xff), // 65535 in four-byte form
                bytesOf(0x1b, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff), // 2^32 - 1 in eight-byte form
                bytesOf(0x78, 0x01, 0x61), // text-string length 1 in one-byte form
                bytesOf(0x58, 0x01, 0x00), // byte-string length 1 in one-byte form
                bytesOf(0x98, 0x00), // array length 0 in one-byte form
                bytesOf(0xb8, 0x00), // map length 0 in one-byte form
            ),
        )
    }

    @Test
    fun indefiniteLengthItemsAreRejected() {
        assertAllRejected(
            listOf(
                bytesOf(0x5f, 0x41, 0x61, 0xff), // byte string
                bytesOf(0x7f, 0x61, 0x61, 0xff), // text string
                bytesOf(0x9f, 0x00, 0xff), // array
                bytesOf(0xbf, 0x61, 0x61, 0x00, 0xff), // map
            ),
        )
    }

    @Test
    fun reservedAdditionalInformationValuesAreRejected() {
        assertAllRejected(listOf(bytesOf(0x1c), bytesOf(0x1d), bytesOf(0x1e)))
    }

    @Test
    fun negativeIntegersTagsFloatsAndSimpleValuesAreRejected() {
        assertAllRejected(
            listOf(
                bytesOf(0x20), // negative integer -1
                bytesOf(0xc1, 0x00), // tag 1
                bytesOf(0xf4), // false
                bytesOf(0xf5), // true
                bytesOf(0xf6), // null
                bytesOf(0xf7), // undefined
                bytesOf(0xf9, 0x3c, 0x00), // half-precision 1.0
                bytesOf(0xfa, 0x3f, 0x80, 0x00, 0x00), // single-precision 1.0
                bytesOf(0xfb, 0x3f, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), // double-precision 1.0
                bytesOf(0xff), // stray break
            ),
        )
    }

    @Test
    fun mapKeysMustBeTextStrings() {
        assertAllRejected(
            listOf(
                bytesOf(0xa1, 0x01, 0x02), // unsigned-integer key
                bytesOf(0xa1, 0x41, 0x61, 0x02), // byte-string key
            ),
        )
    }

    @Test
    fun mapKeysAreOrderedByEncodedKeyBytesNotByStringOrder() {
        // "b" encodes as 61 62 and "aa" as 62 61 61, so "b" must come first even though
        // "aa" sorts before "b" as a Kotlin string.
        val encodedOrder = TestCbor.orderedMap(
            listOf(TestCbor.text("b") to TestCbor.uint(0), TestCbor.text("aa") to TestCbor.uint(1)),
        )
        val stringOrder = TestCbor.orderedMap(
            listOf(TestCbor.text("aa") to TestCbor.uint(1), TestCbor.text("b") to TestCbor.uint(0)),
        )

        val accepted = read(encodedOrder) as CborValue.Map
        assertEquals(listOf("b", "aa"), accepted.pairs.map { pair -> pair.first })
        assertAllRejected(listOf(stringOrder))
    }

    @Test
    fun duplicateAndUnsortedMapKeysAreRejected() {
        val duplicate = TestCbor.orderedMap(
            listOf(TestCbor.text("a") to TestCbor.uint(0), TestCbor.text("a") to TestCbor.uint(1)),
        )
        val sameLengthUnsorted = TestCbor.orderedMap(
            listOf(TestCbor.text("b") to TestCbor.uint(0), TestCbor.text("a") to TestCbor.uint(1)),
        )
        assertAllRejected(listOf(duplicate, sameLengthUnsorted))
    }

    @Test
    fun malformedUtf8InTextStringsIsRejected() {
        assertEquals(CborValue.Text("\u20ac"), read(bytesOf(0x63, 0xe2, 0x82, 0xac)))

        assertAllRejected(
            listOf(
                bytesOf(0x62, 0xc3, 0x28), // invalid continuation byte
                bytesOf(0x62, 0xc0, 0x80), // overlong encoding of U+0000
                bytesOf(0x63, 0xed, 0xa0, 0x80), // UTF-8 encoded surrogate U+D800
                bytesOf(0x64, 0xf4, 0x90, 0x80, 0x80), // above U+10FFFF
                bytesOf(0x61, 0xe2), // truncated multi-byte sequence
                bytesOf(0x61, 0xff), // byte that never occurs in UTF-8
            ),
        )
    }

    @Test
    fun containerNestingIsLimitedToFourLevels() {
        assertTrue(read(nestedMaps(4)) is CborValue.Map)
        assertAllRejected(listOf(nestedMaps(5), nestedMaps(6)))
    }

    @Test
    fun mapsAreLimitedToTenEntries() {
        assertTrue(read(mapWithEntries(10)) is CborValue.Map)
        assertAllRejected(listOf(mapWithEntries(11)))
    }

    @Test
    fun declaredLengthsAreCheckedBeforeContentIsReadOrAllocated() {
        val pieceKey = TestCbor.text("mergeablePieces")
        val arrayVectors = listOf(
            // 2^31 - 1 items declared, none present
            TestCbor.orderedMap(listOf(pieceKey to bytesOf(0x9a, 0x7f, 0xff, 0xff, 0xff))),
            // five items declared, one present
            TestCbor.orderedMap(listOf(pieceKey to bytesOf(0x85, 0x41, 0x61))),
            // an array under a key that may not hold one
            TestCbor.orderedMap(listOf(TestCbor.text("format") to bytesOf(0x81, 0x00))),
        )
        for ((index, vector) in arrayVectors.withIndex()) {
            assertThrows(
                TokenizerResourceLimitException::class.java,
                Executable { read(vector) },
                "array vector #$index must hit a resource limit",
            )
        }

        assertAllRejected(
            listOf(
                bytesOf(0x5b, 0, 0, 0, 1, 0, 0, 0, 0), // byte string longer than Int.MAX_VALUE
                bytesOf(0x5a, 0x7f, 0xff, 0xff, 0xff), // byte string of 2^31 - 1 bytes, none present
            ),
        )
    }

    @Test
    fun textStringLimitIsInclusive() {
        val tight = TEST_LIMITS.copy(maxTextStringBytes = 4)

        assertEquals(CborValue.Text("hell"), read(TestCbor.text("hell"), tight))
        assertThrows(
            TokenizerResourceLimitException::class.java,
            Executable { read(TestCbor.text("hello"), tight) },
            "a five-byte text string must exceed a four-byte limit",
        )
    }
}
