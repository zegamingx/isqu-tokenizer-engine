package io.github.zegamingx.isqu.tokenizer.format.cbor

import io.github.zegamingx.isqu.tokenizer.core.ByteString
import io.github.zegamingx.isqu.tokenizer.core.SpecialToken
import io.github.zegamingx.isqu.tokenizer.core.TokenizerSnapshot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.InputStream
import java.security.MessageDigest

/**
 * Schema, fingerprint, provenance, and limit vectors for TokenizerSnapshotCodec.decode.
 *
 * Every artifact is assembled byte by byte with TestCbor (see CborTestSupport.kt) and carries a
 * correct runtime fingerprint for whatever runtime it contains. Each negative vector therefore
 * deviates from a known-good baseline in exactly one way, so a rejection can only come from the
 * rule under test and never from a stale fingerprint. The one exception is the stale-fingerprint
 * test, which is the point of that test.
 */
class TokenizerSnapshotCodecSchemaTest {
    private val limits = TEST_LIMITS

    // ---- building blocks -------------------------------------------------------------------

    private fun utf8(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

    private fun specialTokenEntry(id: Long, name: String, renderBytes: ByteArray): ByteArray {
        return TestCbor.canonicalMap(
            mapOf(
                "id" to TestCbor.uint(id),
                "name" to TestCbor.text(name),
                "renderBytes" to TestCbor.bytes(renderBytes),
            ),
        )
    }

    private fun specialTokenList(vararg entries: ByteArray): ByteArray {
        return TestCbor.array(entries.toList())
    }

    private fun pieceModel(pieces: List<ByteArray>): ByteArray {
        return TestCbor.canonicalMap(
            mapOf("mergeablePieces" to TestCbor.array(pieces.map { piece -> TestCbor.bytes(piece) })),
        )
    }

    private fun typeMap(type: String): ByteArray {
        return TestCbor.canonicalMap(mapOf("type" to TestCbor.text(type)))
    }

    private fun algorithmEntries(): MutableMap<String, ByteArray> = linkedMapOf(
        "name" to TestCbor.text("byte-level-bpe-rank"),
        "version" to TestCbor.uint(1),
    )

    private fun idLayoutEntries(): MutableMap<String, ByteArray> = linkedMapOf(
        "scheme" to TestCbor.text("isqu-byte-bpe-v1"),
        "tokenIdType" to TestCbor.text("non-negative-int32"),
        "specialTokenBase" to TestCbor.uint(0),
        "specialTokenCapacity" to TestCbor.uint(32),
        "byteTokenBase" to TestCbor.uint(32),
        "byteTokenCount" to TestCbor.uint(256),
        "mergeTokenBase" to TestCbor.uint(288),
    )

    private fun decoderEntries(): MutableMap<String, ByteArray> = linkedMapOf(
        "type" to TestCbor.text("byte-concatenation"),
        "textEncoding" to TestCbor.text("utf-8"),
        "invalidUtf8" to TestCbor.text("error"),
        "specialTokenPolicy" to TestCbor.text("render-bytes"),
    )

    private fun withEntry(
        base: Map<String, ByteArray>,
        key: String,
        value: ByteArray,
    ): MutableMap<String, ByteArray> {
        val copy = LinkedHashMap<String, ByteArray>(base)
        copy[key] = value
        return copy
    }

    private fun without(base: Map<String, ByteArray>, key: String): MutableMap<String, ByteArray> {
        val copy = LinkedHashMap<String, ByteArray>(base)
        copy.remove(key)
        return copy
    }

    private fun changedMap(base: Map<String, ByteArray>, key: String, value: ByteArray): ByteArray {
        return TestCbor.canonicalMap(withEntry(base, key, value))
    }

    private fun removedKey(base: Map<String, ByteArray>, key: String): ByteArray {
        return TestCbor.canonicalMap(without(base, key))
    }

    private fun baselineTokens(): ByteArray = specialTokenList(
        specialTokenEntry(0, "bos", utf8("<BOS>")),
        specialTokenEntry(1, "eos", utf8("<EOS>")),
    )

    private fun baselinePieces(): List<ByteArray> = listOf(utf8("he"), utf8("hel"))

    private fun baselineRuntime(): MutableMap<String, ByteArray> = linkedMapOf(
        "algorithm" to TestCbor.canonicalMap(algorithmEntries()),
        "idLayout" to TestCbor.canonicalMap(idLayoutEntries()),
        "specialTokens" to baselineTokens(),
        "normalizer" to typeMap("none"),
        "preTokenizer" to typeMap("none"),
        "decoder" to TestCbor.canonicalMap(decoderEntries()),
        "model" to pieceModel(baselinePieces()),
    )

    private fun runtimeWith(
        tokens: ByteArray = baselineTokens(),
        pieces: List<ByteArray> = baselinePieces(),
    ): MutableMap<String, ByteArray> {
        val runtime = baselineRuntime()
        runtime["specialTokens"] = tokens
        runtime["model"] = pieceModel(pieces)
        return runtime
    }

    private fun fingerprintOf(runtime: Map<String, ByteArray>): ByteArray {
        val input = TestCbor.canonicalMap(
            mapOf(
                "format" to TestCbor.text("isqu-tokenizer"),
                "formatVersion" to TestCbor.uint(1),
                "runtime" to TestCbor.canonicalMap(runtime),
            ),
        )
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun buildArtifact(
        runtime: Map<String, ByteArray>,
        provenance: Map<String, ByteArray>? = null,
        adjustTopLevel: (MutableMap<String, ByteArray>) -> Unit = {},
    ): ByteArray {
        val topLevel = linkedMapOf(
            "format" to TestCbor.text("isqu-tokenizer"),
            "formatVersion" to TestCbor.uint(1),
            "runtime" to TestCbor.canonicalMap(runtime),
            "runtimeFingerprint" to TestCbor.bytes(fingerprintOf(runtime)),
        )
        if (provenance != null) {
            topLevel["provenance"] = TestCbor.canonicalMap(provenance)
        }
        adjustTopLevel(topLevel)
        return TestCbor.canonicalMap(topLevel)
    }

    private fun topLevelVariant(change: (MutableMap<String, ByteArray>) -> Unit): ByteArray {
        return buildArtifact(baselineRuntime(), adjustTopLevel = change)
    }

    private fun runtimeVariant(change: (MutableMap<String, ByteArray>) -> Unit): ByteArray {
        val runtime = baselineRuntime()
        change(runtime)
        return buildArtifact(runtime)
    }

    private fun replaced(key: String, value: ByteArray): ByteArray {
        return runtimeVariant { runtime -> runtime[key] = value }
    }

    private fun tokenVariant(vararg entries: ByteArray): ByteArray {
        return buildArtifact(runtimeWith(tokens = specialTokenList(*entries)))
    }

    private fun pieceVariant(vararg pieces: ByteArray): ByteArray {
        return buildArtifact(runtimeWith(pieces = pieces.toList()))
    }

    private fun withProvenance(provenance: Map<String, ByteArray>): ByteArray {
        return buildArtifact(baselineRuntime(), provenance)
    }

    private fun rootProvenance(fingerprint: ByteArray): MutableMap<String, ByteArray> = linkedMapOf(
        "lineageRootFingerprint" to TestCbor.bytes(fingerprint),
        "snapshotSequence" to TestCbor.uint(0),
    )

    private fun runtimeVariants(): List<Map<String, ByteArray>> {
        val bos = specialTokenEntry(0, "bos", utf8("<BOS>"))
        val eos = specialTokenEntry(1, "eos", utf8("<EOS>"))
        return listOf(
            baselineRuntime(),
            runtimeWith(tokens = specialTokenList(bos, specialTokenEntry(1, "EOS", utf8("<EOS>")))),
            runtimeWith(tokens = specialTokenList(bos, specialTokenEntry(1, "eos", utf8("</s>")))),
            runtimeWith(tokens = specialTokenList(bos, eos, specialTokenEntry(2, "pad", utf8("<PAD>")))),
            runtimeWith(tokens = specialTokenList()),
            runtimeWith(pieces = listOf(utf8("he"), utf8("el"))),
            runtimeWith(pieces = listOf(utf8("he"), utf8("hel"), utf8("hell"))),
            runtimeWith(pieces = listOf(utf8("he"))),
            runtimeWith(pieces = emptyList()),
        )
    }

    private fun assertRejected(name: String, candidate: ByteArray) {
        val failure = assertThrows(
            TokenizerFormatException::class.java,
            Executable { TokenizerSnapshotCodec.decode(candidate, limits) },
            "$name must be rejected",
        )
        assertFalse(
            failure is TokenizerFingerprintMismatchException,
            "$name was stopped only by the fingerprint check, not by the rule under test",
        )
    }

    private fun assertAllRejected(vectors: List<Pair<String, ByteArray>>) {
        for ((name, candidate) in vectors) {
            assertRejected(name, candidate)
        }
    }

    // ---- control: the hand-built baseline is the real format -------------------------------

    @Test
    fun handBuiltBaselineMatchesTheProductionEncoder() {
        val snapshot = TokenizerSnapshot(
            specialTokens = listOf(
                SpecialToken(0, "bos", ByteString.of(utf8("<BOS>"))),
                SpecialToken(1, "eos", ByteString.of(utf8("<EOS>"))),
            ),
            mergeablePieces = listOf(ByteString.of(utf8("he")), ByteString.of(utf8("hel"))),
        )
        val handBuilt = buildArtifact(baselineRuntime())

        assertArrayEquals(TokenizerSnapshotCodec.encode(snapshot), handBuilt)

        val loaded = TokenizerSnapshotCodec.decode(handBuilt, limits)
        assertArrayEquals(fingerprintOf(baselineRuntime()), loaded.runtimeFingerprint.toByteArray())
        assertArrayEquals(
            TokenizerSnapshotCodec.fingerprint(snapshot).toByteArray(),
            loaded.runtimeFingerprint.toByteArray(),
        )
    }

    // ---- schema ------------------------------------------------------------------------------

    @Test
    fun topLevelSchemaViolationsAreRejected() {
        assertAllRejected(
            listOf(
                "unknown top-level key" to topLevelVariant { top -> top["extra"] = TestCbor.uint(0) },
                "missing format" to topLevelVariant { top -> top.remove("format") },
                "missing runtimeFingerprint" to topLevelVariant { top -> top.remove("runtimeFingerprint") },
                "foreign format identifier" to
                    topLevelVariant { top -> top["format"] = TestCbor.text("isqu-tokenizer-v2") },
                "format identifier is not text" to
                    topLevelVariant { top -> top["format"] = TestCbor.uint(1) },
                "formatVersion is text" to
                    topLevelVariant { top -> top["formatVersion"] = TestCbor.text("1") },
                "runtime is not a map" to
                    topLevelVariant { top -> top["runtime"] = TestCbor.array(emptyList()) },
                "runtimeFingerprint is text" to
                    topLevelVariant { top -> top["runtimeFingerprint"] = TestCbor.text("not-bytes") },
                "runtimeFingerprint is 31 bytes" to
                    topLevelVariant { top -> top["runtimeFingerprint"] = TestCbor.bytes(ByteArray(31)) },
                "runtimeFingerprint is 33 bytes" to
                    topLevelVariant { top -> top["runtimeFingerprint"] = TestCbor.bytes(ByteArray(33)) },
            ),
        )
    }

    @Test
    fun runtimeSchemaViolationsAreRejected() {
        assertAllRejected(
            listOf(
                "unknown runtime key" to runtimeVariant { runtime -> runtime["extra"] = TestCbor.uint(0) },
                "missing model" to runtimeVariant { runtime -> runtime.remove("model") },
                "missing idLayout" to runtimeVariant { runtime -> runtime.remove("idLayout") },
                "algorithm is not a map" to replaced("algorithm", TestCbor.text("byte-level-bpe-rank")),
                "unknown algorithm" to
                    replaced("algorithm", changedMap(algorithmEntries(), "name", TestCbor.text("unigram"))),
                "algorithm version 2" to
                    replaced("algorithm", changedMap(algorithmEntries(), "version", TestCbor.uint(2))),
                "algorithm has an extra key" to
                    replaced("algorithm", changedMap(algorithmEntries(), "extra", TestCbor.uint(0))),
                "normalizer is not none" to replaced("normalizer", typeMap("nfc")),
                "pre-tokenizer is not none" to replaced("preTokenizer", typeMap("whitespace")),
                "normalizer has an extra key" to replaced(
                    "normalizer",
                    TestCbor.canonicalMap(mapOf("type" to TestCbor.text("none"), "extra" to TestCbor.uint(0))),
                ),
                "decoder replaces invalid UTF-8" to
                    replaced("decoder", changedMap(decoderEntries(), "invalidUtf8", TestCbor.text("replace"))),
                "decoder skips special tokens" to
                    replaced("decoder", changedMap(decoderEntries(), "specialTokenPolicy", TestCbor.text("skip"))),
                "decoder uses UTF-16" to
                    replaced("decoder", changedMap(decoderEntries(), "textEncoding", TestCbor.text("utf-16"))),
                "decoder lacks textEncoding" to
                    replaced("decoder", removedKey(decoderEntries(), "textEncoding")),
                "idLayout mergeTokenBase is 289" to
                    replaced("idLayout", changedMap(idLayoutEntries(), "mergeTokenBase", TestCbor.uint(289))),
                "idLayout scheme is v2" to
                    replaced("idLayout", changedMap(idLayoutEntries(), "scheme", TestCbor.text("isqu-byte-bpe-v2"))),
                "idLayout capacity is 64" to
                    replaced("idLayout", changedMap(idLayoutEntries(), "specialTokenCapacity", TestCbor.uint(64))),
                "idLayout token type is int64" to
                    replaced("idLayout", changedMap(idLayoutEntries(), "tokenIdType", TestCbor.text("int64"))),
                "idLayout has an extra key" to
                    replaced("idLayout", changedMap(idLayoutEntries(), "extra", TestCbor.uint(0))),
                "idLayout lacks byteTokenCount" to
                    replaced("idLayout", removedKey(idLayoutEntries(), "byteTokenCount")),
            ),
        )
    }

    @Test
    fun specialTokenViolationsAreRejected() {
        val bos = specialTokenEntry(0, "bos", utf8("<BOS>"))
        val eos = specialTokenEntry(1, "eos", utf8("<EOS>"))
        val invalidNames = listOf("1bos", "bo s", "bos!", "", "a".repeat(65))

        assertAllRejected(
            listOf(
                "ids out of order" to tokenVariant(eos, bos),
                "duplicate id" to tokenVariant(bos, specialTokenEntry(0, "eos", utf8("<EOS>"))),
                "id outside the reserved range" to tokenVariant(specialTokenEntry(32, "bos", utf8("<BOS>"))),
                "slot 0 left unassigned" to tokenVariant(eos),
                "duplicate name" to tokenVariant(bos, specialTokenEntry(1, "bos", utf8("<EOS>"))),
                "empty render bytes" to tokenVariant(specialTokenEntry(0, "bos", ByteArray(0))),
                "render bytes are not UTF-8" to
                    tokenVariant(specialTokenEntry(0, "bos", byteArrayOf(0xff.toByte()))),
                "entry has an extra key" to tokenVariant(
                    TestCbor.canonicalMap(
                        mapOf(
                            "id" to TestCbor.uint(0),
                            "name" to TestCbor.text("bos"),
                            "renderBytes" to TestCbor.bytes(utf8("<BOS>")),
                            "extra" to TestCbor.uint(0),
                        ),
                    ),
                ),
                "entry lacks name" to tokenVariant(
                    TestCbor.canonicalMap(
                        mapOf(
                            "id" to TestCbor.uint(0),
                            "renderBytes" to TestCbor.bytes(utf8("<BOS>")),
                        ),
                    ),
                ),
                "entry id is text" to tokenVariant(
                    TestCbor.canonicalMap(
                        mapOf(
                            "id" to TestCbor.text("0"),
                            "name" to TestCbor.text("bos"),
                            "renderBytes" to TestCbor.bytes(utf8("<BOS>")),
                        ),
                    ),
                ),
                "specialTokens is a map" to replaced("specialTokens", TestCbor.canonicalMap(emptyMap())),
            ) + invalidNames.map { name ->
                "invalid name '$name'" to tokenVariant(specialTokenEntry(0, name, utf8("<BOS>")))
            },
        )
    }

    @Test
    fun specialTokenCountAndNameLengthBoundariesAreInclusive() {
        val thirtyTwo = (0 until 32).map { id -> specialTokenEntry(id.toLong(), "t$id", utf8("<t$id>")) }
        val full = TokenizerSnapshotCodec.decode(
            buildArtifact(runtimeWith(tokens = TestCbor.array(thirtyTwo))),
            limits,
        )
        assertEquals(32, full.snapshot.specialTokens.size)
        assertEquals(full.snapshot.idSpaceSize, full.snapshot.activeTokenCount)

        val thirtyThree = (0 until 33).map { id -> specialTokenEntry(id.toLong(), "t$id", utf8("<t$id>")) }
        assertThrows(
            TokenizerResourceLimitException::class.java,
            Executable {
                TokenizerSnapshotCodec.decode(
                    buildArtifact(runtimeWith(tokens = TestCbor.array(thirtyThree))),
                    limits,
                )
            },
            "a 33rd special token must exceed the reserved capacity",
        )

        val longestName = "a" + "b".repeat(63)
        val loaded = TokenizerSnapshotCodec.decode(
            buildArtifact(
                runtimeWith(tokens = specialTokenList(specialTokenEntry(0, longestName, utf8("<x>")))),
            ),
            limits,
        )
        assertEquals(longestName, loaded.snapshot.specialTokens[0].name)
    }

    @Test
    fun learnedPieceViolationsAreRejected() {
        assertAllRejected(
            listOf(
                "single-byte piece" to pieceVariant(utf8("a")),
                "empty piece" to pieceVariant(ByteArray(0)),
                "duplicate piece" to pieceVariant(utf8("he"), utf8("he")),
                "piece that cannot be built from lower ranks" to pieceVariant(utf8("xyz")),
                "piece listed before its parts" to pieceVariant(utf8("hel"), utf8("he")),
                "piece is a text string" to replaced(
                    "model",
                    TestCbor.canonicalMap(
                        mapOf("mergeablePieces" to TestCbor.array(listOf(TestCbor.text("he")))),
                    ),
                ),
                "mergeablePieces is a map" to replaced(
                    "model",
                    TestCbor.canonicalMap(mapOf("mergeablePieces" to TestCbor.canonicalMap(emptyMap()))),
                ),
                "model has an extra key" to replaced(
                    "model",
                    TestCbor.canonicalMap(
                        mapOf(
                            "mergeablePieces" to TestCbor.array(emptyList()),
                            "extra" to TestCbor.uint(0),
                        ),
                    ),
                ),
                "model lacks mergeablePieces" to replaced("model", TestCbor.canonicalMap(emptyMap())),
            ),
        )
    }

    // ---- provenance --------------------------------------------------------------------------

    @Test
    fun provenanceSchemaViolationsAreRejected() {
        val fingerprint = fingerprintOf(baselineRuntime())
        val root = rootProvenance(fingerprint)

        assertAllRejected(
            listOf(
                "unknown provenance key" to withProvenance(withEntry(root, "extra", TestCbor.uint(0))),
                "missing snapshotSequence" to withProvenance(without(root, "snapshotSequence")),
                "missing lineageRootFingerprint" to withProvenance(without(root, "lineageRootFingerprint")),
                "root lineage differs from the runtime fingerprint" to
                    withProvenance(rootProvenance(ByteArray(32))),
                "root snapshot names a parent" to
                    withProvenance(withEntry(root, "parentRuntimeFingerprint", TestCbor.bytes(fingerprint))),
                "non-root snapshot has no parent" to
                    withProvenance(withEntry(root, "snapshotSequence", TestCbor.uint(1))),
                "lineage root of 31 bytes" to withProvenance(rootProvenance(ByteArray(31))),
                "empty trainerName" to withProvenance(withEntry(root, "trainerName", TestCbor.text(""))),
                "createdAt is not a timestamp" to
                    withProvenance(withEntry(root, "createdAt", TestCbor.text("yesterday"))),
                "seed is text" to withProvenance(withEntry(root, "seed", TestCbor.text("7"))),
                "provenance is not a map" to
                    topLevelVariant { top -> top["provenance"] = TestCbor.array(emptyList()) },
            ),
        )
    }

    @Test
    fun wellFormedProvenanceIsAccepted() {
        val fingerprint = fingerprintOf(baselineRuntime())

        val root = TokenizerSnapshotCodec.decode(withProvenance(rootProvenance(fingerprint)), limits)
        assertEquals(0uL, root.provenance?.snapshotSequence)

        val childEntries = rootProvenance(ByteArray(32) { 1 })
        childEntries["snapshotSequence"] = TestCbor.uint(1)
        childEntries["parentRuntimeFingerprint"] = TestCbor.bytes(ByteArray(32) { 2 })
        childEntries["createdAt"] = TestCbor.text("2026-11-13T12:00:00Z")
        val child = TokenizerSnapshotCodec.decode(withProvenance(childEntries), limits)
        assertEquals(1uL, child.provenance?.snapshotSequence)
        assertEquals("2026-11-13T12:00:00Z", child.provenance?.createdAt)
    }

    // ---- fingerprint -------------------------------------------------------------------------

    @Test
    fun everyRuntimeChangeProducesADifferentFingerprint() {
        val seen = HashSet<List<Byte>>()
        for ((index, variant) in runtimeVariants().withIndex()) {
            val loaded = TokenizerSnapshotCodec.decode(buildArtifact(variant), limits)
            val actual = loaded.runtimeFingerprint.toByteArray()

            assertArrayEquals(fingerprintOf(variant), actual, "variant #$index")
            assertArrayEquals(
                TokenizerSnapshotFingerprint.compute(loaded.snapshot).toByteArray(),
                actual,
                "variant #$index",
            )
            seen.add(actual.toList())
        }
        assertEquals(runtimeVariants().size, seen.size)
    }

    @Test
    fun aStaleFingerprintIsRejectedForEveryRuntimeChange() {
        val staleFingerprint = fingerprintOf(baselineRuntime())
        for ((index, variant) in runtimeVariants().withIndex().drop(1)) {
            val stale = buildArtifact(variant) { top ->
                top["runtimeFingerprint"] = TestCbor.bytes(staleFingerprint)
            }
            assertThrows(
                TokenizerFingerprintMismatchException::class.java,
                Executable { TokenizerSnapshotCodec.decode(stale, limits) },
                "variant #$index must not verify against the baseline fingerprint",
            )
        }
    }

    // ---- limits ------------------------------------------------------------------------------

    @Test
    fun everyLoadLimitIsInclusive() {
        val provenance = rootProvenance(fingerprintOf(baselineRuntime()))
        val candidate = buildArtifact(baselineRuntime(), provenance)
        val exact = TokenizerLoadLimits(
            maxArtifactBytes = candidate.size,
            maxMergeablePieceCount = 2,
            maxIndividualPieceBytes = 3,
            maxCumulativeLearnedPieceBytes = 5L,
            maxSpecialTokenRenderBytes = 5,
            maxTextStringBytes = 64,
            maxProvenanceBytes = TestCbor.canonicalMap(provenance).size,
        )

        TokenizerSnapshotCodec.decode(candidate, exact)

        val oneBelow = listOf(
            "artifact bytes" to exact.copy(maxArtifactBytes = exact.maxArtifactBytes - 1),
            "piece count" to exact.copy(maxMergeablePieceCount = 1),
            "individual piece bytes" to exact.copy(maxIndividualPieceBytes = 2),
            "cumulative piece bytes" to exact.copy(maxCumulativeLearnedPieceBytes = 4L),
            "special-token render bytes" to exact.copy(maxSpecialTokenRenderBytes = 4),
            "provenance bytes" to exact.copy(maxProvenanceBytes = exact.maxProvenanceBytes - 1),
        )
        for ((name, tight) in oneBelow) {
            assertThrows(
                TokenizerResourceLimitException::class.java,
                Executable { TokenizerSnapshotCodec.decode(candidate, tight) },
                "the $name limit must be enforced",
            )
        }
    }

    @Test
    fun zeroLearnedPiecesAreValidWhenTheLimitIsZero() {
        val loaded = TokenizerSnapshotCodec.decode(
            buildArtifact(runtimeWith(pieces = emptyList())),
            limits.copy(maxMergeablePieceCount = 0),
        )

        assertEquals(0, loaded.snapshot.mergeablePieces.size)
        assertEquals(288, loaded.snapshot.idSpaceSize)
    }

    @Test
    fun streamLoaderEnforcesTheArtifactLimitAcrossShortReads() {
        val encoded = buildArtifact(baselineRuntime())
        val exact = limits.copy(maxArtifactBytes = encoded.size)

        val loaded = TokenizerSnapshotCodec.load(TrickleInputStream(encoded, 7), exact)
        assertArrayEquals(fingerprintOf(baselineRuntime()), loaded.runtimeFingerprint.toByteArray())

        assertThrows(
            TokenizerResourceLimitException::class.java,
            Executable {
                TokenizerSnapshotCodec.load(
                    TrickleInputStream(encoded, 7),
                    exact.copy(maxArtifactBytes = encoded.size - 1),
                )
            },
            "a stream one byte over the limit must be rejected",
        )
        assertThrows(
            TokenizerResourceLimitException::class.java,
            Executable {
                TokenizerSnapshotCodec.load(TrickleInputStream(encoded + byteArrayOf(0), 7), exact)
            },
            "a stream with one extra trailing byte must be rejected",
        )
    }

    @Test
    fun loadLimitsRejectNonPositiveBounds() {
        assertThrows(IllegalArgumentException::class.java) { limits.copy(maxArtifactBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { limits.copy(maxMergeablePieceCount = -1) }
        assertThrows(IllegalArgumentException::class.java) { limits.copy(maxIndividualPieceBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            limits.copy(maxCumulativeLearnedPieceBytes = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) { limits.copy(maxSpecialTokenRenderBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { limits.copy(maxTextStringBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { limits.copy(maxProvenanceBytes = 0) }
    }
}

/** Delivers at most [chunkSize] bytes per read so the loader's chunked accounting is exercised. */
private class TrickleInputStream(
    private val data: ByteArray,
    private val chunkSize: Int,
) : InputStream() {
    private var position = 0

    override fun read(): Int {
        if (position >= data.size) {
            return -1
        }
        return data[position++].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) {
            return 0
        }
        if (position >= data.size) {
            return -1
        }
        val count = minOf(chunkSize, len, data.size - position)
        System.arraycopy(data, position, b, off, count)
        position += count
        return count
    }
}
