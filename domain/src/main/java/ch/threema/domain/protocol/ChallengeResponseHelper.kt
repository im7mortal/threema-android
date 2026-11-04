package ch.threema.domain.protocol

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.emptyDirectByteBuffer
import ch.threema.common.toDirectByteBuffer
import ch.threema.libthreema.CryptoException
import ch.threema.libthreema.blake2bMac256

private val logger = getThreemaLogger("ChallengeResponseHelper")

class ChallengeResponseHelper {
    /**
     * @throws IllegalArgumentException if the response can not be computed due to invalid input
     */
    fun calculateResponse(sharedSecret: ByteArray, challenge: ByteArray): ByteArray {
        try {
            val responseKey = blake2bMac256(
                key = sharedSecret,
                personal = "3ma-csp".toDirectByteBuffer(),
                salt = "dir".toDirectByteBuffer(),
                data = emptyDirectByteBuffer(),
            )
            return blake2bMac256(
                key = responseKey,
                personal = emptyDirectByteBuffer(),
                salt = emptyDirectByteBuffer(),
                data = challenge.toDirectByteBuffer(),
            )
        } catch (e: CryptoException) {
            logger.error("Failed to compute blake2b hash", e)
            throw IllegalArgumentException("Failed to compute blake2b hash", e)
        }
    }
}
