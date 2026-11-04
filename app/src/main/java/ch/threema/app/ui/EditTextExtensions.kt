package ch.threema.app.ui

import android.widget.EditText

fun EditText.getTextOrEmpty(): String =
    text?.toString() ?: ""
