package io.github.zegamingx.isqu.tokenizer.format.cbor

import io.github.zegamingx.isqu.tokenizer.core.ByteString
import io.github.zegamingx.isqu.tokenizer.core.SpecialToken
import io.github.zegamingx.isqu.tokenizer.core.TokenizerSnapshot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.InputStream

/**
 * TokenizerSnapshotCodec.load promises to read no more than maxArtifactBytes from the caller's
 * stream. In practice that means the artifact plus at most one sentinel byte whose presence
 * proves the stream is longer than the limit; it must never drain a whole read buffer past the
 * limit, because the stream belongs to the caller and may carry more data after the artifact.
 */
class TokenizerSnapshotCodecStreamTest {
    private val snapshot = TokenizerSnapshot(
        specialTokens = listOf(
            SpecialToken(0, "bos", ByteString.of("<BOS>".toByteArray(Charsets.UTF_8))),
        ),
        mergeablePieces = listOf(
            ByteString.of("he".toByteArray(Charsets.UTF_8)),
            ByteString.of("hel".toByteArray(Charsets.UTF_8)),
        ),
    )
    private val encoded = TokenizerSnapshotCodec.encode(snapshot)
    private val limits = TEST_LIMITS.copy(maxArtifactBytes = encoded.size)

    @Test
    fun anExactSizeStreamIsConsumedExactly() {
        val stream = CountingInputStream(encoded, chunkSize = 8_192)

        val loaded = TokenizerSnapshotCodec.load(stream, limits)

        assertArrayEquals(
            TokenizerSnapshotCodec.fingerprint(snapshot).toByteArray(),
            loaded.runtimeFingerprint.toByteArray(),
        )
        assertEquals(encoded.size, stream.consumed)
    }

    @Test
    fun anOverlongStreamIsConsumedOnlyOneByteBeyondTheLimit() {
        val overlong = encoded + ByteArray(5_000)

        for (chunkSize in intArrayOf(1, 7, 8_192)) {
            val stream = CountingInputStream(overlong, chunkSize)

            assertThrows(
                TokenizerResourceLimitException::class.java,
                Executable { TokenizerSnapshotCodec.load(stream, limits) },
                "chunk size $chunkSize must be rejected",
            )
            assertEquals(encoded.size + 1, stream.consumed, "chunk size $chunkSize")
        }
    }

    @Test
    fun aTinyLimitDoesNotDrainTheReadBuffer() {
        val stream = CountingInputStream(ByteArray(100_000), chunkSize = 8_192)
        val tiny = TEST_LIMITS.copy(maxArtifactBytes = 100)

        assertThrows(
            TokenizerResourceLimitException::class.java,
            Executable { TokenizerSnapshotCodec.load(stream, tiny) },
            "a stream far longer than the limit must be rejected",
        )
        assertEquals(101, stream.consumed)
    }
}

/** Hands out at most [chunkSize] bytes per read and counts every byte it has handed out. */
private class CountingInputStream(
    private val data: ByteArray,
    private val chunkSize: Int,
) : InputStream() {
    private var position = 0

    val consumed: Int
        get() = position

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
