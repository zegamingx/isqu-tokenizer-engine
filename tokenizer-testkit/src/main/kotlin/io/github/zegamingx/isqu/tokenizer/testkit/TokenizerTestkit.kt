package io.github.zegamingx.isqu.tokenizer.testkit

import io.github.zegamingx.isqu.tokenizer.core.ByteLevelBpeTokenizer
import io.github.zegamingx.isqu.tokenizer.core.ByteString
import io.github.zegamingx.isqu.tokenizer.core.SpecialToken
import io.github.zegamingx.isqu.tokenizer.core.TokenizerSnapshot
import io.github.zegamingx.isqu.tokenizer.format.cbor.TokenizerSnapshotFingerprint

/**
 * A portable golden vector. Input and expected output are bytes so vectors
 * can cover arbitrary byte sequences, not only valid Unicode strings.
 */
data class ByteEncodingVector(
    val name: String,
    val input: ByteString,
    val expectedTokenIds: List<Int>,
    val expectedDecodedBytes: ByteString = input,
)

/**
 * Small, deliberately inspectable vectors for the isqu-byte-bpe-v1 runtime.
 * These are conformance fixtures, not a trained production vocabulary.
 */
object ByteLevelBpeTestVectors {
    val snapshot: TokenizerSnapshot by lazy {
        TokenizerSnapshot(
            mergeablePieces = listOf(
                ByteString.fromUtf8("he"),
                ByteString.fromUtf8("hel"),
                ByteString.fromUtf8("hell"),
                ByteString.fromUtf8("hello"),
                ByteString.fromUtf8("  "),
            ),
        )
    }

    val vectors: List<ByteEncodingVector> by lazy {
        listOf(
            ByteEncodingVector(
                name = "empty byte stream",
                input = ByteString.empty(),
                expectedTokenIds = emptyList(),
            ),
            ByteEncodingVector(
                name = "ranked hello merge",
                input = ByteString.fromUtf8("hello"),
                expectedTokenIds = listOf(291),
            ),
            ByteEncodingVector(
                name = "whitespace is preserved",
                input = ByteString.fromUtf8("hello hello"),
                expectedTokenIds = listOf(291, 64, 291),
            ),
            ByteEncodingVector(
                name = "embedded NUL byte",
                input = ByteString.of(byteArrayOf(0)),
                expectedTokenIds = listOf(32),
            ),
            ByteEncodingVector(
                name = "arbitrary invalid UTF-8 byte",
                input = ByteString.of(byteArrayOf(0xff.toByte())),
                expectedTokenIds = listOf(287),
            ),
            ByteEncodingVector(
                name = "multibyte Unicode remains byte exact",
                input = ByteString.fromUtf8("é"),
                expectedTokenIds = listOf(227, 201),
            ),
        )
    }
}

/**
 * Assertions reusable by downstream tokenizer implementations and consumers.
 * This module is intended for test/runtime verification dependencies only.
 */
object TokenizerTestkit {
    fun assertVector(
        tokenizer: ByteLevelBpeTokenizer,
        vector: ByteEncodingVector,
    ) {
        val actual = tokenizer.encodeBytes(vector.input.toByteArray())
        check(actual.toList() == vector.expectedTokenIds) {
            "Vector '${vector.name}' expected ${vector.expectedTokenIds}, got ${actual.toList()}"
        }
        val decoded = tokenizer.decodeToBytes(actual)
        check(decoded.contentEquals(vector.expectedDecodedBytes.toByteArray())) {
            "Vector '${vector.name}' did not round-trip to its expected bytes"
        }
    }

    fun assertRoundTrip(
        tokenizer: ByteLevelBpeTokenizer,
        input: ByteArray,
    ) {
        val decoded = tokenizer.decodeToBytes(tokenizer.encodeBytes(input))
        check(decoded.contentEquals(input)) {
            "Byte-level BPE round-trip changed the input byte sequence"
        }
    }

    fun assertDeterministic(
        tokenizer: ByteLevelBpeTokenizer,
        input: ByteArray,
        repetitions: Int = 32,
    ) {
        require(repetitions > 0) { "repetitions must be positive" }
        val expected = tokenizer.encodeBytes(input)
        repeat(repetitions - 1) {
            val actual = tokenizer.encodeBytes(input)
            check(actual.contentEquals(expected)) {
                "Encoding changed between repeated runs for the same snapshot and input"
            }
        }
    }

    /**
     * Proves append-only identity, not segmentation stability. A larger BPE
     * vocabulary may legitimately choose new merges for previously seen text.
     */
    fun assertExistingTokenIdsPreserved(
        previous: TokenizerSnapshot,
        extended: TokenizerSnapshot,
    ) {
        check(extended.idSpaceSize >= previous.idSpaceSize) {
            "An extended snapshot cannot shrink the ID space"
        }
        check(extended.mergeablePieces.size >= previous.mergeablePieces.size) {
            "An extended snapshot cannot remove learned pieces"
        }
        for (index in previous.mergeablePieces.indices) {
            check(extended.mergeablePieces[index] == previous.mergeablePieces[index]) {
                "Learned token ID ${TokenizerSnapshot.MERGE_TOKEN_BASE + index} was reassigned"
            }
        }
        for (special in previous.specialTokens) {
            val extendedSpecial = extended.specialTokenById(special.id)
            check(extendedSpecial == special) {
                "Special-token ID ${special.id} was removed or rebound"
            }
        }
    }

    fun assertRuntimeFingerprintChangesOnExtension(
        previous: TokenizerSnapshot,
        extended: TokenizerSnapshot,
    ) {
        assertExistingTokenIdsPreserved(previous, extended)
        check(
            TokenizerSnapshotFingerprint.compute(previous) !=
                TokenizerSnapshotFingerprint.compute(extended),
        ) {
            "A runtime-changing vocabulary extension must change the fingerprint"
        }
    }

    fun assertStableSpecialTokenMapping(
        previous: List<SpecialToken>,
        extended: List<SpecialToken>,
    ) {
        val byId = extended.associateBy(SpecialToken::id)
        for (oldToken in previous) {
            check(byId[oldToken.id] == oldToken) {
                "Special-token ID ${oldToken.id} changed its name or render bytes"
            }
        }
    }
}
