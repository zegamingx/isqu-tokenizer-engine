package io.github.zegamingx.isqu.tokenizer.format.cbor

import io.github.zegamingx.isqu.tokenizer.core.ByteLevelBpeTokenizer
import io.github.zegamingx.isqu.tokenizer.core.ByteString
import io.github.zegamingx.isqu.tokenizer.core.SpecialToken
import io.github.zegamingx.isqu.tokenizer.core.TokenizerSnapshot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class TokenizerSnapshotCodecTest {
    private val limits = TokenizerLoadLimits(
        maxArtifactBytes = 1_000_000,
        maxMergeablePieceCount = 10_000,
        maxIndividualPieceBytes = 4_096,
        maxCumulativeLearnedPieceBytes = 1_000_000L,
        maxSpecialTokenRenderBytes = 4_096,
        maxTextStringBytes = 4_096,
        maxProvenanceBytes = 16_384,
    )

    private fun snapshot(): TokenizerSnapshot {
        return TokenizerSnapshot(
            specialTokens = listOf(
                SpecialToken(0, "bos", ByteString.fromUtf8("<BOS>")),
                SpecialToken(1, "eos", ByteString.fromUtf8("<EOS>")),
            ),
            mergeablePieces = listOf(
                ByteString.fromUtf8("he"),
                ByteString.fromUtf8("hel"),
            ),
        )
    }

    @Test
    fun snapshotSaveAndLoadPreserveRuntimeAndFingerprint() {
        val snapshot = snapshot()
        val encoded = TokenizerSnapshotCodec.encode(snapshot)
        val loaded = TokenizerSnapshotCodec.decode(encoded, limits)

        assertEquals(snapshot.specialTokens, loaded.snapshot.specialTokens)
        assertEquals(snapshot.mergeablePieces, loaded.snapshot.mergeablePieces)
        assertEquals(TokenizerSnapshotFingerprint.compute(snapshot), loaded.runtimeFingerprint)
        assertArrayEquals(encoded, TokenizerSnapshotCodec.encode(loaded.snapshot))
        assertArrayEquals(
            ByteLevelBpeTokenizer(snapshot).encodeText("hello"),
            ByteLevelBpeTokenizer(loaded.snapshot).encodeText("hello"),
        )
    }

    @Test
    fun saveWritesTheSameBytesAsEncode() {
        val snapshot = snapshot()
        val output = java.io.ByteArrayOutputStream()

        TokenizerSnapshotCodec.save(snapshot, output)

        assertArrayEquals(TokenizerSnapshotCodec.encode(snapshot), output.toByteArray())
    }

    @Test
    fun deterministicEncodingIsByteForByteRepeatable() {
        val snapshot = snapshot()
        val first = TokenizerSnapshotCodec.encode(snapshot)

        repeat(20) {
            assertArrayEquals(first, TokenizerSnapshotCodec.encode(snapshot))
        }
    }

    @Test
    fun provenanceChangesDoNotChangeRuntimeFingerprint() {
        val snapshot = snapshot()
        val fingerprint = TokenizerSnapshotFingerprint.compute(snapshot)
        val firstProvenance = TokenizerProvenance(
            lineageRootFingerprint = fingerprint,
            snapshotSequence = 0uL,
            trainerName = "isqu-test-trainer",
            trainerVersion = "0.1",
        )
        val secondProvenance = firstProvenance.copy(trainerVersion = "0.2")

        val firstBytes = TokenizerSnapshotCodec.encode(snapshot, firstProvenance)
        val secondBytes = TokenizerSnapshotCodec.encode(snapshot, secondProvenance)
        val firstLoaded = TokenizerSnapshotCodec.decode(firstBytes, limits)
        val secondLoaded = TokenizerSnapshotCodec.decode(secondBytes, limits)

        assertNotEquals(firstBytes.toList(), secondBytes.toList())
        assertEquals(firstLoaded.runtimeFingerprint, secondLoaded.runtimeFingerprint)
        assertEquals("0.1", firstLoaded.provenance?.trainerVersion)
        assertEquals("0.2", secondLoaded.provenance?.trainerVersion)
    }

    @Test
    fun fingerprintMismatchIsRejected() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 0x01).toByte()

        assertThrows(TokenizerFingerprintMismatchException::class.java) {
            TokenizerSnapshotCodec.decode(encoded, limits)
        }
    }

    @Test
    fun loaderRequiresCallerSuppliedBoundsAndEnforcesArtifactSize() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())
        val smallLimits = limits.copy(maxArtifactBytes = encoded.size - 1)

        assertThrows(TokenizerResourceLimitException::class.java) {
            TokenizerSnapshotCodec.decode(encoded, smallLimits)
        }
    }

    @Test
    fun loaderEnforcesIndividualAndCumulativePieceByteLimits() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())

        assertThrows(TokenizerResourceLimitException::class.java) {
            TokenizerSnapshotCodec.decode(
                encoded,
                limits.copy(maxIndividualPieceBytes = 2),
            )
        }
        assertThrows(TokenizerResourceLimitException::class.java) {
            TokenizerSnapshotCodec.decode(
                encoded,
                limits.copy(maxCumulativeLearnedPieceBytes = 4L),
            )
        }
    }

    @Test
    fun loaderEnforcesSpecialTokenRenderByteLimit() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())

        assertThrows(TokenizerResourceLimitException::class.java) {
            TokenizerSnapshotCodec.decode(
                encoded,
                limits.copy(maxSpecialTokenRenderBytes = 4),
            )
        }
    }

    @Test
    fun loaderEnforcesProvenanceByteLimitDuringParsing() {
        val snapshot = snapshot()
        val provenance = TokenizerProvenance(
            lineageRootFingerprint = TokenizerSnapshotFingerprint.compute(snapshot),
            snapshotSequence = 0uL,
            trainerName = "isqu-test-trainer".repeat(8),
        )
        val encoded = TokenizerSnapshotCodec.encode(snapshot, provenance)

        assertThrows(TokenizerResourceLimitException::class.java) {
            TokenizerSnapshotCodec.decode(
                encoded,
                limits.copy(maxProvenanceBytes = 32),
            )
        }
    }

    @Test
    fun loaderEnforcesMergeablePieceCount() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())
        val strictLimits = limits.copy(maxMergeablePieceCount = 1)

        assertThrows(TokenizerResourceLimitException::class.java) {
            TokenizerSnapshotCodec.decode(encoded, strictLimits)
        }
    }

    @Test
    fun streamLoaderDoesNotTakeStreamOwnership() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())
        val input = ByteArrayInputStream(encoded)

        val loaded = TokenizerSnapshotCodec.load(input, limits)

        assertEquals(TokenizerSnapshotFingerprint.compute(snapshot()), loaded.runtimeFingerprint)
        assertEquals(-1, input.read())
    }

    @Test
    fun unsupportedFormatVersionIsRejectedExplicitly() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())
        val versionKey = "formatVersion".toByteArray(Charsets.UTF_8)
        val valueOffset = encoded.indexOfSequence(versionKey) + 1 + versionKey.size
        check(valueOffset > versionKey.size)
        check(encoded[valueOffset].toInt() == 1)
        encoded[valueOffset] = 2

        val exception = assertThrows(UnsupportedTokenizerFormatVersionException::class.java) {
            TokenizerSnapshotCodec.decode(encoded, limits)
        }
        assertEquals(2uL, exception.version)
    }

    @Test
    fun truncatedAndTrailingInputAreRejected() {
        val encoded = TokenizerSnapshotCodec.encode(snapshot())

        assertThrows(TokenizerFormatException::class.java) {
            TokenizerSnapshotCodec.decode(encoded.copyOf(encoded.size - 1), limits)
        }
        assertThrows(TokenizerFormatException::class.java) {
            TokenizerSnapshotCodec.decode(encoded + byteArrayOf(0), limits)
        }
    }

    @Test
    fun unsignedProvenanceRetainsTheFullCborUInt64Range() {
        val snapshot = snapshot()
        val fingerprint = TokenizerSnapshotFingerprint.compute(snapshot)
        val provenance = TokenizerProvenance(
            lineageRootFingerprint = fingerprint,
            snapshotSequence = ULong.MAX_VALUE,
            parentRuntimeFingerprint = fingerprint,
            seed = ULong.MAX_VALUE,
        )

        val loaded = TokenizerSnapshotCodec.decode(
            TokenizerSnapshotCodec.encode(snapshot, provenance),
            limits,
        )

        assertEquals(ULong.MAX_VALUE, loaded.provenance?.snapshotSequence)
        assertEquals(ULong.MAX_VALUE, loaded.provenance?.seed)
    }

    @Test
    fun invalidRootProvenanceIsRejected() {
        val invalid = TokenizerProvenance(
            lineageRootFingerprint = ByteString.of(ByteArray(32)),
            snapshotSequence = 0uL,
        )

        assertThrows(TokenizerFormatException::class.java) {
            TokenizerSnapshotCodec.encode(snapshot(), invalid)
        }
    }
}


private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
    if (sequence.isEmpty() || sequence.size > size) {
        return -1
    }
    for (start in 0..(size - sequence.size)) {
        var matches = true
        for (index in sequence.indices) {
            if (this[start + index] != sequence[index]) {
                matches = false
                break
            }
        }
        if (matches) {
            return start
        }
    }
    return -1
}
