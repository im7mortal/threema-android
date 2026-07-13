package ch.threema.common

import androidx.compose.runtime.Immutable

/**
 * Represents an amount of bytes, such as the size of a file
 */
@Immutable
@JvmInline
value class ByteSize(val bytes: Long) : Comparable<ByteSize> {
    override fun toString(): String =
        "$bytes bytes"

    operator fun plus(other: ByteSize): ByteSize =
        ByteSize(bytes + other.bytes)

    operator fun minus(other: ByteSize): ByteSize =
        ByteSize(bytes - other.bytes)

    operator fun div(other: ByteSize): Double =
        bytes.toDouble() / other.bytes

    operator fun times(factor: Int): ByteSize =
        ByteSize(bytes * factor)

    override fun compareTo(other: ByteSize): Int =
        bytes.compareTo(other.bytes)
}

operator fun Int.times(byteSize: ByteSize) =
    byteSize * this

val Long.bytes
    get() = ByteSize(this)

val Int.bytes
    get() = ByteSize(this.toLong())

val Long.kiloBytes
    get() = ByteSize(this * 1000)

val Int.kiloBytes
    get() = ByteSize(this.toLong() * 1000)
