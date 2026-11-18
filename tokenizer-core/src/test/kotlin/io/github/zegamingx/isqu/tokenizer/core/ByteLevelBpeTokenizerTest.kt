package io.github.zegamingx.isqu.tokenizer.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ByteLevelBpeTokenizerTest {
    @Test
    fun rankedBpeSelectsLowestRank() {
        val snapshot = TokenizerSnapshot(
            mergeablePieces = listOf(
                ByteString.fromUtf8("ab"),
                ByteString.fromUtf8("bc"),
            ),
        )
        val tokenizer = ByteLevelBpeTokenizer(snapshot)

        assertEquals(listOf(288, 131), tokenizer.encodeText("abc").toList())
    }

    @Test
    fun equalRankCandidatesAreResolvedFromLeft() {
        val tokenizer = ByteLevelBpeTokenizer(
            TokenizerSnapshot(
                mergeablePieces = listOf(ByteString.fromUtf8("aa")),
            ),
        )

        assertEquals(listOf(288, 288), tokenizer.encodeText("aaaa").toList())
    }

    @Test
    fun rankedMultiStepMergeProducesExpectedTokenId() {
        val tokenizer = ByteLevelBpeTokenizer(
            TokenizerSnapshot(
                mergeablePieces = listOf(
                    ByteString.fromUtf8("he"),
                    ByteString.fromUtf8("hel"),
                    ByteString.fromUtf8("hell"),
                    ByteString.fromUtf8("hello"),
                    ByteString.fromUtf8("  "),
                ),
            ),
        )

        assertEquals(listOf(291), tokenizer.encodeText("hello").toList())
        assertEquals(listOf(291, 64, 291), tokenizer.encodeText("hello hello").toList())
    }

    @Test
    fun allPossibleByteValuesRoundTripWithoutLoss() {
        val tokenizer = ByteLevelBpeTokenizer(TokenizerSnapshot())
        val input = ByteArray(256) { it.toByte() }

        assertArrayEquals(input, tokenizer.decodeToBytes(tokenizer.encodeBytes(input)))
    }

    @Test
    fun byteStringDebugRepresentationFormatsEveryByteAsTwoHexDigits() {
        val value = ByteString.of(
            byteArrayOf(0x00, 0x0f, 0x10, 0xff.toByte()),
        )

        assertEquals("ByteString(000f10ff)", value.toString())
    }

    @Test
    fun rawByteRoundTripPreservesArbitraryNonUtf8Data() {
        val tokenizer = ByteLevelBpeTokenizer(TokenizerSnapshot())
        val input = byteArrayOf(0, 0xff.toByte(), 0xc3.toByte(), 0x28, 0x0a)

        val decoded = tokenizer.decodeToBytes(tokenizer.encodeBytes(input))

        assertArrayEquals(input, decoded)
    }

    @Test
    fun utf8CodePointMayCrossTokenBoundaries() {
        val firstTwoEuroBytes = ByteString.of(
            byteArrayOf(0xe2.toByte(), 0x82.toByte()),
        )
        val tokenizer = ByteLevelBpeTokenizer(
            TokenizerSnapshot(mergeablePieces = listOf(firstTwoEuroBytes)),
        )

        val ids = tokenizer.encodeText("€")
        assertEquals(listOf(288, 204), ids.toList())
        assertEquals("€", tokenizer.decodeText(ids))
    }

    @Test
    fun plainTextDoesNotInjectSpecialTokens() {
        val special = SpecialToken(
            id = 0,
            name = "bos",
            renderBytes = ByteString.fromUtf8("<BOS>"),
        )
        val tokenizer = ByteLevelBpeTokenizer(
            TokenizerSnapshot(specialTokens = listOf(special)),
        )

        val plainIds = tokenizer.encodeText("<BOS>")
        val typedIds = tokenizer.encodeSegments(
            listOf(
                TokenizerSegment.Text("a"),
                TokenizerSegment.Special(0),
                TokenizerSegment.Text("b"),
            ),
        )

        assertNotEquals(listOf(0), plainIds.toList())
        assertEquals("a<BOS>b", tokenizer.decodeText(typedIds))
        assertEquals(
            listOf(0),
            tokenizer.encodeSegments(listOf(TokenizerSegment.SpecialByName("bos"))).toList(),
        )
        assertEquals("ab", tokenizer.decodeText(typedIds, skipSpecialTokens = true))
    }

    @Test
    fun unassignedSpecialIdsAreRejected() {
        val tokenizer = ByteLevelBpeTokenizer(TokenizerSnapshot())

        assertThrows(UnassignedSpecialTokenIdException::class.java) {
            tokenizer.decodeToBytes(intArrayOf(0))
        }
    }

    @Test
    fun invalidUtf16IsRejectedBeforeUtf8Conversion() {
        val tokenizer = ByteLevelBpeTokenizer(TokenizerSnapshot())
        val malformed = String(charArrayOf(0xd800.toChar()))

        assertThrows(MalformedUtf16InputException::class.java) {
            tokenizer.encodeText(malformed)
        }
    }

    @Test
    fun invalidUtf8IsRejectedAfterConcatenatingTokenBytes() {
        val tokenizer = ByteLevelBpeTokenizer(TokenizerSnapshot())

        assertThrows(InvalidUtf8TokenSequenceException::class.java) {
            tokenizer.decodeText(intArrayOf(287))
        }
    }

    @Test
    fun snapshotValidatesRankedPieceConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            TokenizerSnapshot(
                mergeablePieces = listOf(ByteString.fromUtf8("xyz")),
            )
        }
    }

    @Test
    fun specialTokenIdsCannotSkipTheLowestUnassignedSlot() {
        assertThrows(IllegalArgumentException::class.java) {
            TokenizerSnapshot(
                specialTokens = listOf(
                    SpecialToken(1, "eos", ByteString.fromUtf8("<EOS>")),
                ),
            )
        }
    }

    @Test
    fun tokenIdSpaceAndActiveTokenCountRemainDistinct() {
        val snapshot = TokenizerSnapshot(
            specialTokens = listOf(
                SpecialToken(0, "eos", ByteString.fromUtf8("<EOS>")),
            ),
            mergeablePieces = listOf(ByteString.fromUtf8("ab")),
        )

        assertEquals(289, snapshot.idSpaceSize)
        assertEquals(258, snapshot.activeTokenCount)
    }
}
