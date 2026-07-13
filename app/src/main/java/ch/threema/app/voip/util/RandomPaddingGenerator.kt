package ch.threema.app.voip.util

import ch.threema.common.generateRandomBytes
import ch.threema.common.secureRandom

object RandomPaddingGenerator {
    /**
     * Generate between `minBytes` (inclusive) and `maxBytes` (inclusive) random bytes.
     */
    @JvmStatic
    fun generateRandomPadding(minBytes: Int, maxBytes: Int): ByteArray {
        val count = secureRandom().nextInt(maxBytes + 1 - minBytes) + minBytes
        return secureRandom().generateRandomBytes(count)
    }
}
