package io.github.zegamingx.isqu.tokenizer.core

import java.util.Collections

/**
 * A special token is an explicitly assigned control token. The tokenizer does
 * not infer special tokens from matching text in ordinary input.
 */
data class SpecialToken(
    val id: Int,
    val name: String,
    val renderBytes: ByteString,
) {
    init {
        require(id in SPECIAL_TOKEN_BASE until SPECIAL_TOKEN_LIMIT) {
            "Special-token ID must be in [0, 32)"
        }
        require(SPECIAL_NAME.matches(name)) {
            "Special-token name must match [A-Za-z][A-Za-z0-9._-]{0,63}"
        }
        require(!renderBytes.isEmpty) { "Special-token render bytes must not be empty" }
        Utf8Support.decodeStrict(renderBytes.toByteArray())
    }

    companion object {
        const val SPECIAL_TOKEN_BASE: Int = 0
        const val SPECIAL_TOKEN_LIMIT: Int = 32

        private val SPECIAL_NAME = Regex("[A-Za-z][A-Za-z0-9._-]{0,63}")
    }
}

/**
 * Immutable runtime state for the initial isqu-byte-bpe-v1 profile.
 *
 * Learned pieces are ordered by increasing merge rank. The token ID of
 * mergeablePieces[index] is 288 + index.
 */
class TokenizerSnapshot(
    specialTokens: List<SpecialToken> = emptyList(),
    mergeablePieces: List<ByteString> = emptyList(),
) {
    val specialTokens: List<SpecialToken>
    val mergeablePieces: List<ByteString>

    val mergeablePieceCount: Int
        get() = mergeablePieces.size

    val idSpaceSize: Int

    val activeTokenCount: Int
        get() = BYTE_TOKEN_COUNT + specialTokens.size + mergeablePieces.size

    private val specialTokensById: Map<Int, SpecialToken>
    private val specialTokensByName: Map<String, SpecialToken>

    init {
        require(mergeablePieces.size <= Int.MAX_VALUE - BASE_ID_SPACE) {
            "Learned-piece count exceeds the non-negative Int token-ID space"
        }

        val sortedSpecialTokens = specialTokens.sortedBy(SpecialToken::id)
        require(sortedSpecialTokens.size <= RESERVED_SPECIAL_TOKEN_CAPACITY) {
            "At most 32 special tokens can be assigned in isqu-byte-bpe-v1"
        }
        require(sortedSpecialTokens.map(SpecialToken::id).toSet().size == sortedSpecialTokens.size) {
            "Special-token IDs must be unique"
        }
        require(sortedSpecialTokens.map(SpecialToken::name).toSet().size == sortedSpecialTokens.size) {
            "Special-token names must be unique"
        }

        this.specialTokens = Collections.unmodifiableList(sortedSpecialTokens.toList())
        this.specialTokensById = Collections.unmodifiableMap(
            sortedSpecialTokens.associateBy(SpecialToken::id),
        )
        this.specialTokensByName = Collections.unmodifiableMap(
            sortedSpecialTokens.associateBy(SpecialToken::name),
        )

        val copiedPieces = mergeablePieces.toList()
        validateLearnedPieces(copiedPieces)
        this.mergeablePieces = Collections.unmodifiableList(copiedPieces)
        this.idSpaceSize = BASE_ID_SPACE + copiedPieces.size
    }

    fun specialTokenById(tokenId: Int): SpecialToken? = specialTokensById[tokenId]

    fun specialTokenByName(tokenName: String): SpecialToken? = specialTokensByName[tokenName]

    fun tokenBytes(tokenId: Int): ByteString {
        if (tokenId < 0 || tokenId >= idSpaceSize) {
            throw UnknownTokenIdException(tokenId)
        }
        if (tokenId < RESERVED_SPECIAL_TOKEN_CAPACITY) {
            return specialTokensById[tokenId]?.renderBytes
                ?: throw UnassignedSpecialTokenIdException(tokenId)
        }
        if (tokenId < BASE_ID_SPACE) {
            return ByteString.of(byteArrayOf((tokenId - BYTE_TOKEN_BASE).toByte()))
        }
        return mergeablePieces[tokenId - MERGE_TOKEN_BASE]
    }

    private fun validateLearnedPieces(pieces: List<ByteString>) {
        val definedPieces = HashSet<ByteArrayView>()
        for (byteValue in 0..255) {
            val baseByte = byteArrayOf(byteValue.toByte())
            val base = ByteString.of(baseByte)
            definedPieces.add(base.view(0, base.size))
        }

        for ((pieceIndex, piece) in pieces.withIndex()) {
            require(piece.size >= 2) {
                "Learned piece at rank $pieceIndex must contain at least two bytes"
            }

            val completeView = piece.view(0, piece.size)
            require(!definedPieces.contains(completeView)) {
                "Learned piece at rank $pieceIndex duplicates a base byte or earlier piece"
            }

            var constructible = false
            for (splitIndex in 1 until piece.size) {
                if (
                    definedPieces.contains(piece.view(0, splitIndex)) &&
                    definedPieces.contains(piece.view(splitIndex, piece.size))
                ) {
                    constructible = true
                    break
                }
            }
            require(constructible) {
                "Learned piece at rank $pieceIndex cannot be constructed from lower-rank pieces"
            }
            definedPieces.add(completeView)
        }
    }

    companion object {
        const val RESERVED_SPECIAL_TOKEN_CAPACITY: Int = 32
        const val BYTE_TOKEN_BASE: Int = 32
        const val BYTE_TOKEN_COUNT: Int = 256
        const val MERGE_TOKEN_BASE: Int = 288
        const val BASE_ID_SPACE: Int = 288
        const val ID_LAYOUT_SCHEME: String = "isqu-byte-bpe-v1"
    }
}
