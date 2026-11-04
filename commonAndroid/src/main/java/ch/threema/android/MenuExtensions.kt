package ch.threema.android

import android.content.Context
import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.view.forEach

/**
 * Tint the icons of a complete menu to the provided color. This is necessary when using MenuInflater which does not support the iconTint attribute
 * (SupportMenuInflater / startSupportActionMode respects iconTint)
 */
fun Menu?.tintIcons(@ColorInt color: Int) {
    this?.forEach { menuItem ->
        menuItem.tintIcon(color)
        if (menuItem.hasSubMenu()) {
            menuItem.subMenu?.tintIcons(color)
        }
    }
}

fun MenuItem?.tintIcon(@ColorInt color: Int) {
    this?.icon?.let { drawable ->
        drawable.mutate()
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}

fun MenuItem?.tintIcon(context: Context, @AttrRes colorAttr: Int) {
    this?.tintIcon(context.getColorFromAttribute(colorAttr))
}
