package ch.threema.android

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.res.use

@ColorInt
fun Context.getColorFromAttribute(@AttrRes attr: Int): Int =
    theme.obtainStyledAttributes(intArrayOf(attr))
        .use { attributes ->
            attributes.getColor(0, -1)
        }
