package ch.threema.common

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.text.hexToByteArray
import org.jetbrains.annotations.Contract

/**
 * A collection of helper functions to allow using functions from the Kotlin standard library also in Java.
 */
object JavaCompat {
    @JvmStatic
    fun inputStreamToString(inputStream: InputStream): String =
        inputStream.readBytes().toString(charset = Charsets.UTF_8)

    @JvmStatic
    fun stringToInputStream(string: String): InputStream =
        string.byteInputStream()

    @JvmStatic
    @Throws(IOException::class)
    @JvmOverloads
    fun copyStream(inputStream: InputStream, outputStream: OutputStream, bufferSize: Int = 8 * 1024): Long =
        inputStream.copyTo(outputStream, bufferSize)

    /**
     * Reads this stream completely into a byte array.
     *
     * **Note**: It is the caller's responsibility to close this stream.
     */
    @JvmStatic
    fun readBytes(inputStream: InputStream): ByteArray =
        inputStream.readBytes()

    @JvmStatic
    fun deleteRecursively(directory: File) {
        directory.deleteRecursively()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readBytes(file: File): ByteArray =
        file.readBytes()

    @JvmStatic
    fun isNullOrEmpty(string: String?): Boolean =
        string.isNullOrEmpty()

    @JvmStatic
    fun isNullOrBlank(charSequence: CharSequence?): Boolean =
        (charSequence?.toString()).isNullOrBlank()

    @JvmStatic
    fun areEqual(a: String?, b: String?): Boolean =
        a == b

    @Contract("null -> null, !null -> !null")
    @JvmStatic
    fun toHexString(byteArray: ByteArray?): String? =
        byteArray?.toHexString()

    @JvmStatic
    fun toHexString(byte: Byte): String =
        byte.toHexString()

    @JvmStatic
    fun hexToByteArray(hexString: String): ByteArray =
        hexString.hexToByteArray()
}
