package io.github.zegamingx.isqu.tokenizer.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * An immutable byte sequence. The constructor and accessors defensively copy
 * mutable arrays so snapshots cannot be changed after they are created.
 */
class ByteString private constructor(private val content: ByteArray) {
    val size: Int
        get() = content.size

    val isEmpty: Boolean
        get() = content.isEmpty()

    operator fun get(index: Int): Byte = content[index]

    fun toByteArray(): ByteArray = content.copyOf()

    fun concat(other: ByteString): ByteString {
        require(size <= Int.MAX_VALUE - other.size) { "Combined byte sequence is too large" }
        val combined = ByteArray(size + other.size)
        content.copyInto(combined, destinationOffset = 0)
        other.content.copyInto(combined, destinationOffset = size)
        return ByteString(combined)
    }

    internal fun view(startIndex: Int, endIndex: Int): ByteArrayView {
        require(startIndex >= 0 && endIndex >= startIndex && endIndex <= content.size)
        return ByteArrayView(content, startIndex, endIndex - startIndex)
    }

    internal fun copyRange(startIndex: Int, endIndex: Int): ByteArray {
        require(startIndex >= 0 && endIndex >= startIndex && endIndex <= content.size)
        return content.copyOfRange(startIndex, endIndex)
    }

    override fun equals(other: Any?): Boolean {
        return other is ByteString && content.contentEquals(other.content)
    }

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String {
        val previewLength = minOf(content.size, 16)
        val preview = buildString(previewLength * 2) {
            for (index in 0 until previewLength) {
                append(HEX[content[index].toInt() and 0xff])
            }
        }
        return if (content.size <= previewLength) {
            "ByteString($preview)"
        } else {
            "ByteString($preview…; size=${content.size})"
        }
    }

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()

        fun of(bytes: ByteArray): ByteString = ByteString(bytes.copyOf())

        fun empty(): ByteString = ByteString(ByteArray(0))

        /**
         * Converts a well-formed UTF-16 string to UTF-8 without replacement.
         * Unpaired surrogates are rejected rather than silently rewritten.
         */
        fun fromUtf8(value: String): ByteString {
            Utf8Support.validateUtf16(value)
            return ByteString(value.toByteArray(StandardCharsets.UTF_8))
        }

        internal fun takeOwnership(bytes: ByteArray): ByteString = ByteString(bytes)
    }
}

/**
 * A zero-copy immutable view used only while validating piece construction.
 */
internal class ByteArrayView(
    private val content: ByteArray,
    private val offset: Int,
    private val length: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is ByteArrayView || length != other.length) {
            return false
        }
        for (index in 0 until length) {
            if (content[offset + index] != other.content[other.offset + index]) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        for (index in 0 until length) {
            result = 31 * result + content[offset + index].toInt()
        }
        return result
    }
}

internal object Utf8Support {
    fun validateUtf16(value: String) {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                Character.isHighSurrogate(character) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                        throw MalformedUtf16InputException(index)
                    }
                    index += 2
                }
                Character.isLowSurrogate(character) -> throw MalformedUtf16InputException(index)
                else -> index++
            }
        }
    }

    fun decodeStrict(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (exception: CharacterCodingException) {
            throw InvalidUtf8TokenSequenceException(exception)
        }
    }
}

open class TokenizerException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class MalformedUtf16InputException(val index: Int) :
    TokenizerException("Input contains an unpaired UTF-16 surrogate at index $index")

class InvalidUtf8TokenSequenceException(cause: Throwable) :
    TokenizerException("Decoded token bytes are not valid UTF-8", cause)

class UnknownTokenIdException(val tokenId: Int) :
    TokenizerException("Token ID $tokenId is outside the snapshot ID space")

class UnassignedSpecialTokenIdException(val tokenId: Int) :
    TokenizerException("Special-token ID $tokenId is reserved but not assigned")

class UnknownSpecialTokenNameException(val tokenName: String) :
    TokenizerException("Special token '$tokenName' is not assigned in this snapshot")
