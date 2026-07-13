package ch.threema.common

import java.util.Base64 as Base64Java

/**
 * We provide our own Base64 class, which imitates the implementation from the Kotlin standard library but uses
 * Java's implementation under the hood, because it is less strict w.r.t. padding.
 */
object Base64 {
    @JvmStatic
    fun decode(source: String): ByteArray =
        try {
            Base64Java.getDecoder().decode(source)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Base64, ${e.message}: ${source.truncate(maxLength = 8)}")
        }

    @JvmStatic
    fun encodeToByteArray(source: ByteArray): ByteArray =
        Base64Java.getEncoder().encode(source)

    @JvmStatic
    fun encode(source: ByteArray): String =
        Base64Java.getEncoder().encodeToString(source)

    object UrlSafe {
        @JvmStatic
        fun decode(source: String): ByteArray =
            try {
                Base64Java.getUrlDecoder().decode(source)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid Base64, ${e.message}: ${source.truncate(maxLength = 8)}")
            }
    }
}
