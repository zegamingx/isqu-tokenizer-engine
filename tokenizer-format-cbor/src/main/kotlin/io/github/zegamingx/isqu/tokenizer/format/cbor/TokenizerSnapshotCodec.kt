package io.github.zegamingx.isqu.tokenizer.format.cbor

import io.github.zegamingx.isqu.tokenizer.core.ByteString
import io.github.zegamingx.isqu.tokenizer.core.SpecialToken
import io.github.zegamingx.isqu.tokenizer.core.TokenizerSnapshot
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Caller-selected resource bounds. There is intentionally no unbounded
 * default: callers must choose limits appropriate for their device and use.
 */
data class TokenizerLoadLimits(
    val maxArtifactBytes: Int,
    val maxMergeablePieceCount: Int,
    val maxIndividualPieceBytes: Int,
    val maxCumulativeLearnedPieceBytes: Long,
    val maxSpecialTokenRenderBytes: Int,
    val maxTextStringBytes: Int,
    val maxProvenanceBytes: Int,
) {
    init {
        require(maxArtifactBytes > 0) { "maxArtifactBytes must be positive" }
        require(maxMergeablePieceCount >= 0) { "maxMergeablePieceCount must not be negative" }
        require(maxIndividualPieceBytes > 0) { "maxIndividualPieceBytes must be positive" }
        require(maxCumulativeLearnedPieceBytes > 0L) {
            "maxCumulativeLearnedPieceBytes must be positive"
        }
        require(maxSpecialTokenRenderBytes > 0) {
            "maxSpecialTokenRenderBytes must be positive"
        }
        require(maxTextStringBytes > 0) { "maxTextStringBytes must be positive" }
        require(maxProvenanceBytes > 0) { "maxProvenanceBytes must be positive" }
    }
}

/**
 * Optional lineage and training metadata. It is excluded from the runtime
 * fingerprint because it does not alter tokenization behavior.
 */
data class TokenizerProvenance(
    val lineageRootFingerprint: ByteString,
    val snapshotSequence: Long,
    val parentRuntimeFingerprint: ByteString? = null,
    val trainerName: String? = null,
    val trainerVersion: String? = null,
    val trainerRevision: String? = null,
    val trainingConfigSha256: ByteString? = null,
    val corpusManifestSha256: ByteString? = null,
    val seed: Long? = null,
    val createdAt: String? = null,
) {
    init {
        require(lineageRootFingerprint.size == FINGERPRINT_BYTES) {
            "lineageRootFingerprint must contain exactly 32 bytes"
        }
        require(snapshotSequence >= 0L) { "snapshotSequence must not be negative" }
        require(parentRuntimeFingerprint == null || parentRuntimeFingerprint.size == FINGERPRINT_BYTES) {
            "parentRuntimeFingerprint must contain exactly 32 bytes"
        }
        require(trainingConfigSha256 == null || trainingConfigSha256.size == FINGERPRINT_BYTES) {
            "trainingConfigSha256 must contain exactly 32 bytes"
        }
        require(corpusManifestSha256 == null || corpusManifestSha256.size == FINGERPRINT_BYTES) {
            "corpusManifestSha256 must contain exactly 32 bytes"
        }
        require(seed == null || seed >= 0L) { "seed must not be negative" }
        require(trainerName == null || trainerName.isNotEmpty()) {
            "trainerName must not be empty when supplied"
        }
        require(trainerVersion == null || trainerVersion.isNotEmpty()) {
            "trainerVersion must not be empty when supplied"
        }
        require(trainerRevision == null || trainerRevision.isNotEmpty()) {
            "trainerRevision must not be empty when supplied"
        }
        require(createdAt == null || isRfc3339Timestamp(createdAt)) {
            "createdAt must be an RFC 3339 timestamp with an explicit offset"
        }
        if (snapshotSequence == 0L) {
            require(parentRuntimeFingerprint == null) {
                "A root snapshot must not have a parent fingerprint"
            }
        } else {
            require(parentRuntimeFingerprint != null) {
                "A non-root snapshot must identify its immediate parent fingerprint"
            }
        }
    }

    companion object {
        private const val FINGERPRINT_BYTES = 32

        private fun isRfc3339Timestamp(value: String): Boolean {
            return try {
                OffsetDateTime.parse(value)
                true
            } catch (_: DateTimeParseException) {
                false
            }
        }
    }
}

data class LoadedTokenizerSnapshot(
    val snapshot: TokenizerSnapshot,
    val runtimeFingerprint: ByteString,
    val provenance: TokenizerProvenance?,
)

open class TokenizerFormatException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class UnsupportedTokenizerFormatVersionException(val version: Long) :
    TokenizerFormatException("Unsupported ISQU tokenizer format version: $version")

class TokenizerResourceLimitException(message: String) :
    TokenizerFormatException(message)

class TokenizerFingerprintMismatchException :
    TokenizerFormatException("Tokenizer runtime fingerprint does not match the snapshot runtime")

/**
 * Version-1 .isqutok reader/writer using RFC 8949 core deterministic CBOR.
 */
object TokenizerSnapshotCodec {
    const val FORMAT_IDENTIFIER: String = "isqu-tokenizer"
    const val FORMAT_VERSION: Long = 1L
    private const val FINGERPRINT_BYTES = 32

    fun save(
        snapshot: TokenizerSnapshot,
        output: OutputStream,
        provenance: TokenizerProvenance? = null,
    ) {
        output.write(encode(snapshot, provenance))
    }

    fun encode(
        snapshot: TokenizerSnapshot,
        provenance: TokenizerProvenance? = null,
    ): ByteArray {
        val runtime = runtimeValue(snapshot)
        val fingerprint = fingerprintForRuntime(runtime)
        validateProvenanceForSnapshot(provenance, fingerprint)

        val entries = mutableListOf(
            "format" to CborValue.Text(FORMAT_IDENTIFIER),
            "formatVersion" to CborValue.UInt(FORMAT_VERSION),
            "runtime" to runtime,
            "runtimeFingerprint" to CborValue.Bytes(ByteSlice.owned(fingerprint)),
        )
        if (provenance != null) {
            entries.add("provenance" to provenanceValue(provenance))
        }
        return DeterministicCborWriter.encode(CborValue.Map(entries))
    }

    fun decode(
        artifact: ByteArray,
        limits: TokenizerLoadLimits,
    ): LoadedTokenizerSnapshot {
        if (artifact.size > limits.maxArtifactBytes) {
            throw TokenizerResourceLimitException(
                "Artifact size ${artifact.size} exceeds maxArtifactBytes ${limits.maxArtifactBytes}",
            )
        }

        val root = DeterministicCborReader(artifact, limits).readDocument().asMap("top-level")
        root.requireKeys(
            required = setOf("format", "formatVersion", "runtime", "runtimeFingerprint"),
            optional = setOf("provenance"),
            location = "top-level",
        )

        val format = root.required("format").asText("format")
        if (format != FORMAT_IDENTIFIER) {
            throw TokenizerFormatException("Unsupported tokenizer format identifier: $format")
        }

        val formatVersion = root.required("formatVersion").asUInt("formatVersion")
        if (formatVersion != FORMAT_VERSION) {
            throw UnsupportedTokenizerFormatVersionException(formatVersion)
        }

        val runtime = root.required("runtime").asMap("runtime")
        val snapshot = snapshotFromRuntime(runtime, limits)
        val actualFingerprint = root.required("runtimeFingerprint")
            .asBytes("runtimeFingerprint")
            .copyToByteArray()
        if (actualFingerprint.size != FINGERPRINT_BYTES) {
            throw TokenizerFormatException("runtimeFingerprint must contain exactly 32 bytes")
        }

        val expectedFingerprint = fingerprintForRuntime(runtime)
        if (!MessageDigest.isEqual(actualFingerprint, expectedFingerprint)) {
            throw TokenizerFingerprintMismatchException()
        }

        val provenance = root.entries["provenance"]?.let { value ->
            val provenanceMap = value.asMap("provenance")
            val encodedLength = provenanceMap.encodedEnd - provenanceMap.encodedStart
            if (encodedLength > limits.maxProvenanceBytes) {
                throw TokenizerResourceLimitException(
                    "Provenance size $encodedLength exceeds maxProvenanceBytes ${limits.maxProvenanceBytes}",
                )
            }
            provenanceFromValue(provenanceMap, expectedFingerprint)
        }

        return LoadedTokenizerSnapshot(
            snapshot = snapshot,
            runtimeFingerprint = ByteString.of(expectedFingerprint),
            provenance = provenance,
        )
    }

    /**
     * Reads no more than maxArtifactBytes from the caller-owned stream.
     * The stream is not closed by this method.
     */
    fun load(
        input: InputStream,
        limits: TokenizerLoadLimits,
    ): LoadedTokenizerSnapshot {
        val output = ByteArrayOutputStream(minOf(limits.maxArtifactBytes, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (read == 0) {
                val single = input.read()
                if (single < 0) {
                    break
                }
                if (total == limits.maxArtifactBytes) {
                    throw TokenizerResourceLimitException(
                        "Input stream exceeds maxArtifactBytes ${limits.maxArtifactBytes}",
                    )
                }
                output.write(single)
                total++
                continue
            }
            if (read > limits.maxArtifactBytes - total) {
                throw TokenizerResourceLimitException(
                    "Input stream exceeds maxArtifactBytes ${limits.maxArtifactBytes}",
                )
            }
            output.write(buffer, 0, read)
            total += read
        }
        return decode(output.toByteArray(), limits)
    }

    fun fingerprint(snapshot: TokenizerSnapshot): ByteString {
        return ByteString.of(fingerprintForRuntime(runtimeValue(snapshot)))
    }

    private fun runtimeValue(snapshot: TokenizerSnapshot): CborValue.Map {
        val specialTokens = snapshot.specialTokens.map { special ->
            CborValue.Map(
                listOf(
                    "id" to CborValue.UInt(special.id.toLong()),
                    "name" to CborValue.Text(special.name),
                    "renderBytes" to CborValue.Bytes(
                        ByteSlice.owned(special.renderBytes.toByteArray()),
                    ),
                ),
            )
        }
        val pieces = snapshot.mergeablePieces.map { piece ->
            CborValue.Bytes(ByteSlice.owned(piece.toByteArray()))
        }

        return CborValue.Map(
            listOf(
                "algorithm" to CborValue.Map(
                    listOf(
                        "name" to CborValue.Text("byte-level-bpe-rank"),
                        "version" to CborValue.UInt(1L),
                    ),
                ),
                "idLayout" to CborValue.Map(
                    listOf(
                        "scheme" to CborValue.Text(TokenizerSnapshot.ID_LAYOUT_SCHEME),
                        "tokenIdType" to CborValue.Text("non-negative-int32"),
                        "specialTokenBase" to CborValue.UInt(0L),
                        "specialTokenCapacity" to CborValue.UInt(32L),
                        "byteTokenBase" to CborValue.UInt(32L),
                        "byteTokenCount" to CborValue.UInt(256L),
                        "mergeTokenBase" to CborValue.UInt(288L),
                    ),
                ),
                "specialTokens" to CborValue.Array(specialTokens),
                "normalizer" to CborValue.Map(
                    listOf("type" to CborValue.Text("none")),
                ),
                "preTokenizer" to CborValue.Map(
                    listOf("type" to CborValue.Text("none")),
                ),
                "decoder" to CborValue.Map(
                    listOf(
                        "type" to CborValue.Text("byte-concatenation"),
                        "textEncoding" to CborValue.Text("utf-8"),
                        "invalidUtf8" to CborValue.Text("error"),
                        "specialTokenPolicy" to CborValue.Text("render-bytes"),
                    ),
                ),
                "model" to CborValue.Map(
                    listOf("mergeablePieces" to CborValue.Array(pieces)),
                ),
            ),
        )
    }

    private fun snapshotFromRuntime(
        runtime: CborValue.Map,
        limits: TokenizerLoadLimits,
    ): TokenizerSnapshot {
        runtime.requireKeys(
            required = setOf(
                "algorithm",
                "idLayout",
                "specialTokens",
                "normalizer",
                "preTokenizer",
                "decoder",
                "model",
            ),
            location = "runtime",
        )

        val algorithm = runtime.required("algorithm").asMap("runtime.algorithm")
        algorithm.requireKeys(
            required = setOf("name", "version"),
            location = "runtime.algorithm",
        )
        if (algorithm.required("name").asText("algorithm.name") != "byte-level-bpe-rank") {
            throw TokenizerFormatException("Unsupported tokenizer algorithm")
        }
        if (algorithm.required("version").asUInt("algorithm.version") != 1L) {
            throw TokenizerFormatException("Unsupported byte-level BPE algorithm version")
        }

        validateIdLayout(runtime.required("idLayout").asMap("runtime.idLayout"))
        validateSingleTypeMap(runtime.required("normalizer"), "normalizer", "none")
        validateSingleTypeMap(runtime.required("preTokenizer"), "preTokenizer", "none")
        validateDecoder(runtime.required("decoder").asMap("runtime.decoder"))

        val specialTokenValues = runtime.required("specialTokens")
            .asArray("runtime.specialTokens")
        if (specialTokenValues.items.size > TokenizerSnapshot.RESERVED_SPECIAL_TOKEN_CAPACITY) {
            throw TokenizerResourceLimitException("Snapshot declares more than 32 special tokens")
        }

        val specialTokens = ArrayList<SpecialToken>(specialTokenValues.items.size)
        var previousSpecialId = -1
        for (value in specialTokenValues.items) {
            val specialMap = value.asMap("special-token entry")
            specialMap.requireKeys(
                required = setOf("id", "name", "renderBytes"),
                location = "special-token entry",
            )
            val idLong = specialMap.required("id").asUInt("special-token id")
            if (idLong > Int.MAX_VALUE.toLong()) {
                throw TokenizerFormatException("Special-token ID exceeds Int.MAX_VALUE")
            }
            val id = idLong.toInt()
            if (id <= previousSpecialId) {
                throw TokenizerFormatException("Special-token entries must be sorted by increasing ID")
            }
            previousSpecialId = id

            val renderSlice = specialMap.required("renderBytes").asBytes("special-token renderBytes")
            if (renderSlice.length > limits.maxSpecialTokenRenderBytes) {
                throw TokenizerResourceLimitException(
                    "Special-token render bytes exceed maxSpecialTokenRenderBytes ${limits.maxSpecialTokenRenderBytes}",
                )
            }
            val renderBytes = ByteString.of(renderSlice.copyToByteArray())
            val name = specialMap.required("name").asText("special-token name")
            try {
                specialTokens.add(SpecialToken(id, name, renderBytes))
            } catch (exception: IllegalArgumentException) {
                throw TokenizerFormatException("Invalid special-token entry", exception)
            }
        }

        val model = runtime.required("model").asMap("runtime.model")
        model.requireKeys(
            required = setOf("mergeablePieces"),
            location = "runtime.model",
        )
        val pieceValues = model.required("mergeablePieces").asArray("model.mergeablePieces")
        if (pieceValues.items.size > limits.maxMergeablePieceCount) {
            throw TokenizerResourceLimitException(
                "Mergeable-piece count ${pieceValues.items.size} exceeds maxMergeablePieceCount ${limits.maxMergeablePieceCount}",
            )
        }

        val pieces = ArrayList<ByteString>(pieceValues.items.size)
        var cumulativePieceBytes = 0L
        for ((index, value) in pieceValues.items.withIndex()) {
            val slice = value.asBytes("mergeablePieces[$index]")
            if (slice.length > limits.maxIndividualPieceBytes) {
                throw TokenizerResourceLimitException(
                    "mergeablePieces[$index] exceeds maxIndividualPieceBytes ${limits.maxIndividualPieceBytes}",
                )
            }
            cumulativePieceBytes += slice.length.toLong()
            if (cumulativePieceBytes > limits.maxCumulativeLearnedPieceBytes) {
                throw TokenizerResourceLimitException(
                    "Cumulative learned-piece bytes exceed maxCumulativeLearnedPieceBytes ${limits.maxCumulativeLearnedPieceBytes}",
                )
            }
            pieces.add(ByteString.of(slice.copyToByteArray()))
        }

        return try {
            TokenizerSnapshot(
                specialTokens = specialTokens,
                mergeablePieces = pieces,
            )
        } catch (exception: IllegalArgumentException) {
            throw TokenizerFormatException("Invalid tokenizer runtime data", exception)
        }
    }

    private fun validateIdLayout(layout: CborValue.Map) {
        layout.requireKeys(
            required = setOf(
                "scheme",
                "tokenIdType",
                "specialTokenBase",
                "specialTokenCapacity",
                "byteTokenBase",
                "byteTokenCount",
                "mergeTokenBase",
            ),
            location = "runtime.idLayout",
        )
        val expected = mapOf(
            "scheme" to CborValue.Text(TokenizerSnapshot.ID_LAYOUT_SCHEME),
            "tokenIdType" to CborValue.Text("non-negative-int32"),
            "specialTokenBase" to CborValue.UInt(0L),
            "specialTokenCapacity" to CborValue.UInt(32L),
            "byteTokenBase" to CborValue.UInt(32L),
            "byteTokenCount" to CborValue.UInt(256L),
            "mergeTokenBase" to CborValue.UInt(288L),
        )
        for ((key, expectedValue) in expected) {
            if (layout.required(key) != expectedValue) {
                throw TokenizerFormatException("Unsupported ID-layout value for '$key'")
            }
        }
    }

    private fun validateSingleTypeMap(
        value: CborValue,
        location: String,
        expectedType: String,
    ) {
        val map = value.asMap("runtime.$location")
        map.requireKeys(required = setOf("type"), location = "runtime.$location")
        if (map.required("type").asText("$location.type") != expectedType) {
            throw TokenizerFormatException("Unsupported $location profile")
        }
    }

    private fun validateDecoder(decoder: CborValue.Map) {
        decoder.requireKeys(
            required = setOf("type", "textEncoding", "invalidUtf8", "specialTokenPolicy"),
            location = "runtime.decoder",
        )
        val expected = mapOf(
            "type" to "byte-concatenation",
            "textEncoding" to "utf-8",
            "invalidUtf8" to "error",
            "specialTokenPolicy" to "render-bytes",
        )
        for ((key, value) in expected) {
            if (decoder.required(key).asText("decoder.$key") != value) {
                throw TokenizerFormatException("Unsupported decoder setting '$key'")
            }
        }
    }

    private fun provenanceValue(provenance: TokenizerProvenance): CborValue.Map {
        val entries = mutableListOf<Pair<String, CborValue>>(
            "lineageRootFingerprint" to CborValue.Bytes(
                ByteSlice.owned(provenance.lineageRootFingerprint.toByteArray()),
            ),
            "snapshotSequence" to CborValue.UInt(provenance.snapshotSequence),
        )
        provenance.parentRuntimeFingerprint?.let {
            entries.add("parentRuntimeFingerprint" to CborValue.Bytes(ByteSlice.owned(it.toByteArray())))
        }
        provenance.trainerName?.let { entries.add("trainerName" to CborValue.Text(it)) }
        provenance.trainerVersion?.let { entries.add("trainerVersion" to CborValue.Text(it)) }
        provenance.trainerRevision?.let { entries.add("trainerRevision" to CborValue.Text(it)) }
        provenance.trainingConfigSha256?.let {
            entries.add("trainingConfigSha256" to CborValue.Bytes(ByteSlice.owned(it.toByteArray())))
        }
        provenance.corpusManifestSha256?.let {
            entries.add("corpusManifestSha256" to CborValue.Bytes(ByteSlice.owned(it.toByteArray())))
        }
        provenance.seed?.let { entries.add("seed" to CborValue.UInt(it)) }
        provenance.createdAt?.let { entries.add("createdAt" to CborValue.Text(it)) }
        return CborValue.Map(entries)
    }

    private fun provenanceFromValue(
        map: CborValue.Map,
        runtimeFingerprint: ByteArray,
    ): TokenizerProvenance {
        map.requireKeys(
            required = setOf("lineageRootFingerprint", "snapshotSequence"),
            optional = setOf(
                "parentRuntimeFingerprint",
                "trainerName",
                "trainerVersion",
                "trainerRevision",
                "trainingConfigSha256",
                "corpusManifestSha256",
                "seed",
                "createdAt",
            ),
            location = "provenance",
        )

        val rootFingerprint = map.required("lineageRootFingerprint")
            .asBytes("provenance.lineageRootFingerprint")
            .copyToByteArray()
        val sequence = map.required("snapshotSequence").asUInt("provenance.snapshotSequence")
        val parentFingerprint = map.entries["parentRuntimeFingerprint"]
            ?.asBytes("provenance.parentRuntimeFingerprint")
            ?.copyToByteArray()
        val provenance = try {
            TokenizerProvenance(
                lineageRootFingerprint = ByteString.of(rootFingerprint),
                snapshotSequence = sequence,
                parentRuntimeFingerprint = parentFingerprint?.let { ByteString.of(it) },
                trainerName = map.optionalText("trainerName"),
                trainerVersion = map.optionalText("trainerVersion"),
                trainerRevision = map.optionalText("trainerRevision"),
                trainingConfigSha256 = map.optionalBytes("trainingConfigSha256"),
                corpusManifestSha256 = map.optionalBytes("corpusManifestSha256"),
                seed = map.entries["seed"]?.asUInt("provenance.seed"),
                createdAt = map.optionalText("createdAt"),
            )
        } catch (exception: IllegalArgumentException) {
            throw TokenizerFormatException("Invalid snapshot provenance", exception)
        }

        if (sequence == 0L && !MessageDigest.isEqual(rootFingerprint, runtimeFingerprint)) {
            throw TokenizerFormatException(
                "Root provenance lineageRootFingerprint must equal the runtime fingerprint",
            )
        }
        return provenance
    }

    private fun validateProvenanceForSnapshot(
        provenance: TokenizerProvenance?,
        runtimeFingerprint: ByteArray,
    ) {
        if (provenance == null) {
            return
        }
        if (
            provenance.snapshotSequence == 0L &&
            !MessageDigest.isEqual(
                provenance.lineageRootFingerprint.toByteArray(),
                runtimeFingerprint,
            )
        ) {
            throw TokenizerFormatException(
                "Root provenance lineageRootFingerprint must equal the runtime fingerprint",
            )
        }
    }

    private fun fingerprintForRuntime(runtime: CborValue.Map): ByteArray {
        val input = CborValue.Map(
            listOf(
                "format" to CborValue.Text(FORMAT_IDENTIFIER),
                "formatVersion" to CborValue.UInt(FORMAT_VERSION),
                "runtime" to runtime,
            ),
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(DeterministicCborWriter.encode(input))
    }

    private fun CborValue.asText(location: String): String {
        return (this as? CborValue.Text)?.value
            ?: throw TokenizerFormatException("$location must be a CBOR text string")
    }

    private fun CborValue.asUInt(location: String): Long {
        return (this as? CborValue.UInt)?.value
            ?: throw TokenizerFormatException("$location must be a CBOR unsigned integer")
    }

    private fun CborValue.asBytes(location: String): ByteSlice {
        return (this as? CborValue.Bytes)?.value
            ?: throw TokenizerFormatException("$location must be a CBOR byte string")
    }

    private fun CborValue.asArray(location: String): CborValue.Array {
        return (this as? CborValue.Array)
            ?: throw TokenizerFormatException("$location must be a CBOR array")
    }

    private fun CborValue.asMap(location: String): CborValue.Map {
        return (this as? CborValue.Map)
            ?: throw TokenizerFormatException("$location must be a CBOR map")
    }

    private fun CborValue.Map.required(key: String): CborValue {
        return entries[key] ?: throw TokenizerFormatException("Missing required key '$key'")
    }

    private fun CborValue.Map.requireKeys(
        required: Set<String>,
        optional: Set<String> = emptySet(),
        location: String,
    ) {
        val actual = entries.keys
        val missing = required - actual
        val unknown = actual - required - optional
        if (missing.isNotEmpty()) {
            throw TokenizerFormatException("$location is missing required keys: ${missing.sorted()}")
        }
        if (unknown.isNotEmpty()) {
            throw TokenizerFormatException("$location contains unknown keys: ${unknown.sorted()}")
        }
    }

    private fun CborValue.Map.optionalText(key: String): String? {
        return entries[key]?.asText("provenance.$key")
    }

    private fun CborValue.Map.optionalBytes(key: String): ByteString? {
        return entries[key]?.asBytes("provenance.$key")?.let {
            ByteString.of(it.copyToByteArray())
        }
    }
}

/** Fingerprint helper kept in the serialization adapter, not tokenizer-core. */
object TokenizerSnapshotFingerprint {
    fun compute(snapshot: TokenizerSnapshot): ByteString = TokenizerSnapshotCodec.fingerprint(snapshot)
}
