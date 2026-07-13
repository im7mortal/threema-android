package ch.threema.android.textwatchers

import android.text.Editable

class Base32InputSanitizer : SimpleTextWatcher() {
    private val invalidCharacterRegex = "[^A-Za-z0-9]+".toRegex()

    override fun afterTextChanged(editable: Editable) {
        val original = editable.toString()
        val chunks = original.replace(invalidCharacterRegex, "")
            .chunked(4)
        var transformed = chunks
            .joinToString(separator = "-")
        if (original.endsWith("-") && chunks.lastOrNull()?.length == 4) {
            transformed += "-"
        }
        if (transformed != original) {
            editable.replace(0, editable.length, transformed)
        }
    }
}
