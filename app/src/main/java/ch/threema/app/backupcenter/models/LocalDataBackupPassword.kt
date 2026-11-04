package ch.threema.app.backupcenter.models

import androidx.compose.runtime.Immutable
import java.security.SecureRandom
import kotlin.text.chunked

@Immutable
data class LocalDataBackupPassword(val passwordString: String) {
    init {
        require(passwordString.length == LENGTH) {
            "Password had unexpected length of ${passwordString.length} characters"
        }
        require(passwordString.all(::isValidPasswordCharacter)) {
            "Password contains invalid character: ${passwordString.first { !isValidPasswordCharacter(it) }}"
        }
    }

    val chunks: List<String>
        get() = passwordString.chunked(4)

    val formatted: String
        get() = chunks.joinToString(separator = " ")

    val snippet: String
        get() = passwordString.take(4) + "…" + passwordString.takeLast(4)

    override fun toString() =
        "LocalDataBackupPassword[$snippet]"

    companion object {
        fun generate(secureRandom: SecureRandom) = LocalDataBackupPassword(
            buildString(LENGTH) {
                repeat(LENGTH) {
                    append(generateChar(secureRandom))
                }
            },
        )

        private const val LENGTH = 64

        fun isValidPasswordCharacter(character: Char) =
            character.isDigit() || character in 'A'..'Z'

        private fun generateChar(secureRandom: SecureRandom): Char {
            val charIndex = secureRandom.nextInt(36)
            return when {
                charIndex < 10 -> '0' + charIndex
                else -> 'A' + (charIndex - 10)
            }
        }
    }
}
