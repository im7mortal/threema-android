package ch.threema.app.voip.util

import java.security.SecureRandom

class CallIdGenerator(
    private val secureRandom: SecureRandom,
) {
    /**
     * Generate a random unsigned 32 integer (packed into a non-negative long, because Java)
     */
    fun generateCallId(): Long =
        secureRandom.nextInt().toLong() and 0xffffffffL
}
