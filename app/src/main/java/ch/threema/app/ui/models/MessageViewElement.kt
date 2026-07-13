package ch.threema.app.ui.models

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

@Immutable
data class MessageViewElement(
    @JvmField @DrawableRes val icon: Int? = null,
    @JvmField val placeholder: String? = null,
    @JvmField val text: String? = null,
    @JvmField @ColorRes val iconTint: Int? = null,
)
