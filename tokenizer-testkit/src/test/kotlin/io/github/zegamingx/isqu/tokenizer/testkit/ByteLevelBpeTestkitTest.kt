package io.github.zegamingx.isqu.tokenizer.testkit

import io.github.zegamingx.isqu.tokenizer.core.ByteLevelBpeTokenizer
import io.github.zegamingx.isqu.tokenizer.core.ByteString
import io.github.zegamingx.isqu.tokenizer.core.TokenizerSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ByteLevelBpeTestkitTest {
    @Test
    fun allPublishedByteEncodingVectorsPass() {
        val tokenizer = ByteLevelBpeTokenizer(ByteLevelBpeTestVectors.snapshot)

        ByteLevelBpeTestVectors.vectors.forEach { vector ->
            TokenizerTestkit.assertVector(tokenizer, vector)
        }
    }

    @Test
    fun sameInputAndSnapshotRemainDeterministic() {
        val tokenizer = ByteLevelBpeTokenizer(ByteLevelBpeTestVectors.snapshot)

        TokenizerTestkit.assertDeterministic(
            tokenizer = tokenizer,
            input = ByteString.fromUtf8("hello hello").toByteArray(),
            repetitions = 128,
        )
    }

    @Test
    fun vocabularyExtensionPreservesOldIdsButMayChangeSegmentation() {
        val previous = TokenizerSnapshot(
            mergeablePieces = listOf(ByteString.fromUtf8("he")),
        )
        val extended = TokenizerSnapshot(
            mergeablePieces = listOf(
                ByteString.fromUtf8("he"),
                ByteString.fromUtf8("hel"),
            ),
        )

        TokenizerTestkit.assertRuntimeFingerprintChangesOnExtension(previous, extended)

        val beforeIds = ByteLevelBpeTokenizer(previous).encodeText("hel").toList()
        val afterIds = ByteLevelBpeTokenizer(extended).encodeText("hel").toList()

        assertEquals(listOf(288, 140), beforeIds)
        assertEquals(listOf(289), afterIds)
        assertEquals(ByteString.fromUtf8("he"), extended.tokenBytes(288))
        assertNotEquals(beforeIds, afterIds)
    }

    @Test
    fun rawArbitraryBytesAlwaysRoundTrip() {
        val tokenizer = ByteLevelBpeTokenizer(ByteLevelBpeTestVectors.snapshot)
        val input = byteArrayOf(
            0x00,
            0xff.toByte(),
            0xc3.toByte(),
            0x28,
            0xe2.toByte(),
            0x82.toByte(),
        )

        TokenizerTestkit.assertRoundTrip(tokenizer, input)
    }
}
