package ch.threema.android

import android.content.Context
import android.text.format.Formatter
import ch.threema.common.ByteSize

fun ByteSize.format(context: Context): String =
    Formatter.formatFileSize(context, bytes)
