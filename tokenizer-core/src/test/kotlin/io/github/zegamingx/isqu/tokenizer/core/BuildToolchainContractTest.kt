package io.github.zegamingx.isqu.tokenizer.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildToolchainContractTest {
    @Test
    fun `unit tests execute on the pinned modern JDK toolchain`() {
        assertTrue(
            Runtime.version().feature() >= 25,
            "The test runtime must be JDK 25 or newer",
        )
    }
}
