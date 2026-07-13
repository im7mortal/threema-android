package ch.threema.app.backupcenter.models

import androidx.compose.runtime.Immutable
import ch.threema.common.truncate
import java.security.SecureRandom
import kotlin.text.chunked

@Immutable
data class BackupPassword(val password: String) {
    init {
        require(password.length == LENGTH) {
            "Password had unexpected length of ${password.length} characters"
        }
        require(password.all(::isValidPasswordCharacter)) {
            "Password contains invalid character: ${password.first { !isValidPasswordCharacter(it) }}"
        }
    }

    val chunks: List<String>
        get() = password.chunked(4)

    val formatted: String
        get() = chunks.joinToString(separator = " ")

    override fun toString() =
        "BackupPassword[${password.truncate(maxLength = 3)}]"

    companion object {
        fun generate(secureRandom: SecureRandom) = BackupPassword(
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
