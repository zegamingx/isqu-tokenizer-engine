package io.github.zegamingx.isqu.tokenizer.core

import java.io.ByteArrayOutputStream
import java.util.PriorityQueue

/**
 * Immutable, thread-safe byte-level BPE runtime for isqu-byte-bpe-v1.
 *
 * For every encode operation, the merge with the lowest resulting token ID is
 * selected first. Equal-rank candidates are resolved from left to right.
 */
class ByteLevelBpeTokenizer(
    val snapshot: TokenizerSnapshot,
) {
    private val mergeablePieceIds: Map<ByteString, Int> = buildMap {
        snapshot.mergeablePieces.forEachIndexed { index, piece ->
            put(piece, TokenizerSnapshot.MERGE_TOKEN_BASE + index)
        }
    }

    fun encodeText(text: String): IntArray {
        Utf8Support.validateUtf16(text)
        return encodeBytes(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encodes arbitrary bytes. The input is not required to be valid UTF-8.
     */
    fun encodeBytes(bytes: ByteArray): IntArray {
        if (bytes.isEmpty()) {
            return IntArray(0)
        }

        val nodes = ArrayList<Node>(bytes.size)
        for (index in bytes.indices) {
            val byteValue = bytes[index].toInt() and 0xff
            val node = Node(
                tokenId = TokenizerSnapshot.BYTE_TOKEN_BASE + byteValue,
                bytes = ByteString.of(byteArrayOf(bytes[index])),
                startOffset = index,
            )
            if (nodes.isNotEmpty()) {
                val previous = nodes[nodes.lastIndex]
                previous.next = node
                node.previous = previous
            }
            nodes.add(node)
        }

        val candidates = PriorityQueue(
            compareBy<MergeCandidate>({ it.rank }, { it.left.startOffset }),
        )

        for (index in 0 until nodes.size - 1) {
            addCandidate(nodes[index], nodes[index + 1], candidates)
        }

        while (candidates.isNotEmpty()) {
            val candidate = candidates.remove()
            val left = candidate.left
            val right = candidate.right

            if (
                !left.active ||
                !right.active ||
                left.next !== right ||
                left.version != candidate.leftVersion ||
                right.version != candidate.rightVersion
            ) {
                continue
            }

            val mergedBytes = snapshot.mergeablePieces[
                candidate.rank - TokenizerSnapshot.MERGE_TOKEN_BASE
            ]
            left.tokenId = candidate.rank
            left.bytes = mergedBytes
            left.version++

            val afterRight = right.next
            left.next = afterRight
            if (afterRight != null) {
                afterRight.previous = left
            }
            right.active = false
            right.previous = null
            right.next = null
            right.version++

            val beforeLeft = left.previous
            if (beforeLeft != null) {
                addCandidate(beforeLeft, left, candidates)
            }
            if (afterRight != null) {
                addCandidate(left, afterRight, candidates)
            }
        }

        var current: Node? = nodes[0]
        val output = ArrayList<Int>(bytes.size)
        while (current != null) {
            if (current.active) {
                output.add(current.tokenId)
            }
            current = current.next
        }
        return output.toIntArray()
    }

    /**
     * Inserts special-token IDs only through typed segments. Plain encodeText
     * and encodeBytes always treat special-token spellings as ordinary data.
     */
    fun encodeSegments(segments: List<TokenizerSegment>): IntArray {
        val output = ArrayList<Int>()
        for (segment in segments) {
            when (segment) {
                is TokenizerSegment.Text -> {
                    encodeText(segment.value).forEach(output::add)
                }
                is TokenizerSegment.Special -> {
                    if (snapshot.specialTokenById(segment.id) == null) {
                        if (segment.id in 0 until TokenizerSnapshot.RESERVED_SPECIAL_TOKEN_CAPACITY) {
                            throw UnassignedSpecialTokenIdException(segment.id)
                        }
                        throw UnknownTokenIdException(segment.id)
                    }
                    output.add(segment.id)
                }
            }
        }
        return output.toIntArray()
    }

    /**
     * Concatenates token byte fragments before any UTF-8 validation.
     */
    fun decodeToBytes(
        tokenIds: IntArray,
        skipSpecialTokens: Boolean = false,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        for (tokenId in tokenIds) {
            if (tokenId < 0 || tokenId >= snapshot.idSpaceSize) {
                throw UnknownTokenIdException(tokenId)
            }

            if (tokenId < TokenizerSnapshot.RESERVED_SPECIAL_TOKEN_CAPACITY) {
                val special = snapshot.specialTokenById(tokenId)
                    ?: throw UnassignedSpecialTokenIdException(tokenId)
                if (!skipSpecialTokens) {
                    output.write(special.renderBytes.toByteArray())
                }
            } else if (tokenId < TokenizerSnapshot.MERGE_TOKEN_BASE) {
                output.write(tokenId - TokenizerSnapshot.BYTE_TOKEN_BASE)
            } else {
                val piece = snapshot.mergeablePieces[
                    tokenId - TokenizerSnapshot.MERGE_TOKEN_BASE
                ]
                output.write(piece.toByteArray())
            }
        }
        return output.toByteArray()
    }

    fun decodeText(
        tokenIds: IntArray,
        skipSpecialTokens: Boolean = false,
    ): String {
        return Utf8Support.decodeStrict(decodeToBytes(tokenIds, skipSpecialTokens))
    }

    private fun addCandidate(
        left: Node,
        right: Node,
        queue: PriorityQueue<MergeCandidate>,
    ) {
        if (!left.active || !right.active || left.next !== right) {
            return
        }
        val combined = left.bytes.concat(right.bytes)
        val rank = mergeablePieceIds[combined] ?: return
        queue.add(
            MergeCandidate(
                left = left,
                right = right,
                leftVersion = left.version,
                rightVersion = right.version,
                rank = rank,
            ),
        )
    }

    private class Node(
        var tokenId: Int,
        var bytes: ByteString,
        val startOffset: Int,
    ) {
        var previous: Node? = null
        var next: Node? = null
        var active: Boolean = true
        var version: Int = 0
    }

    private data class MergeCandidate(
        val left: Node,
        val right: Node,
        val leftVersion: Int,
        val rightVersion: Int,
        val rank: Int,
    )
}

sealed class TokenizerSegment {
    data class Text(val value: String) : TokenizerSegment()
    data class Special(val id: Int) : TokenizerSegment()
}
