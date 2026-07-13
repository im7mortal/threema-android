package ch.threema.common

import java.io.UnsupportedEncodingException
import java.util.Locale
import kotlin.math.min

fun String.withoutLastLine(): String = dropLastWhile { it != '\n' }.dropLast(1)

fun String.lastLine(): String = takeLastWhile { it != '\n' }

fun String.replaceLast(oldValue: String, newValue: String): String {
    val prefix = substringBeforeLast(oldValue)
    if (prefix == this) {
        return this
    }
    return prefix + newValue + substringAfterLast(oldValue)
}

fun String.takeUnlessEmpty(): String? = takeUnless { it.isEmpty() }

fun String.takeUnlessBlank(): String? = takeUnless { it.isBlank() }

fun String?.isNotNullOrBlank(): Boolean = !isNullOrBlank()

fun String.capitalize(): String =
    replaceFirstChar { it.titlecase(Locale.getDefault()) }

private val lineBreakRegex = Regex("\\R+")

/**
 * Replaces each consecutive sequence of line break characters with [replaceWith].
 */
fun String.withoutLineBreaks(replaceWith: String): String =
    replace(lineBreakRegex, replaceWith)

/**
 * Truncate the string to the provided maximum byte length. This implementation avoids producing invalid
 * UTF-8 encoded strings by not truncating in the middle of an encoded multibyte character.
 */
fun String.truncateUTF8String(maxBytes: Int): String {
    require(maxBytes > 0)
    if (isEmpty()) {
        return this
    }
    return try {
        String(truncateUTF8StringToByteArray(maxBytes))
    } catch (_: UnsupportedEncodingException) {
        val byteArray = toByteArray()
        if (byteArray.size > maxBytes) {
            String(byteArray.copyOf(maxBytes))
        } else {
            this
        }
    }
}

/**
 * Start with a string that is the same length in characters as the desired maximum number of
 * encoded bytes, then keep removing characters at the end until the encoded length is less than
 * or equal to [maxBytes]. This avoids producing invalid UTF-8 encoded strings
 * which are possible if the encoded byte array is truncated, potentially in the middle of
 * an encoded multibyte character.
 */
fun String.truncateUTF8StringToByteArray(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    var currentString = substring(0, min(length, maxBytes))
    var encoded = currentString.toByteArray()
    while (encoded.size > maxBytes) {
        currentString = currentString.substring(0, currentString.length - 1)
        encoded = currentString.toByteArray()
    }
    return encoded
}

fun String.hexStringToByteArrayOrNull(): ByteArray? =
    try {
        hexToByteArray()
    } catch (_: IllegalArgumentException) {
        null
    }
