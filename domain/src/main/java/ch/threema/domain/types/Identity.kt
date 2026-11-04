package ch.threema.domain.types

import androidx.compose.runtime.Immutable
import ch.threema.domain.protocol.csp.ProtocolDefines

/**
 * Represents an Identity a.k.a. a Threema ID.
 */
@JvmInline
@Immutable
value class Identity(val value: IdentityString) {
    init {
        require(isValidIdentity(value)) { "$value is not a valid identity" }
    }

    override fun toString() = value

    companion object {
        @JvmStatic
        fun isValidIdentity(value: String): Boolean =
            value.length == ProtocolDefines.IDENTITY_LEN &&
                value[0].run { isLatinUpperCaseLetter() || isDigit() || equals('*') } &&
                (1 until ProtocolDefines.IDENTITY_LEN).all { index ->
                    val char = value[index]
                    char.isLatinUpperCaseLetter() || char.isDigit()
                }

        private fun Char.isLatinUpperCaseLetter() =
            this in 'A'..'Z'
    }
}

fun String.toIdentityOrNull(): Identity? =
    try {
        Identity(this)
    } catch (_: IllegalArgumentException) {
        null
    }
