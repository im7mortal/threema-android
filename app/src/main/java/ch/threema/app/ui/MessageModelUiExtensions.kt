package ch.threema.app.ui

import android.content.Context
import android.content.res.ColorStateList
import androidx.appcompat.content.res.AppCompatResources
import ch.threema.android.getColorFromAttribute
import ch.threema.app.R
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.VirtualMessageUtil

/**
 * @return The correct color-state-list to use when rendering contents of this message model.
 */
fun MessageModel.getUiContentColor(context: Context): ColorStateList =
    if (VirtualMessageUtil.isFirstUnreadMessage(this)) {
        ColorStateList.valueOf(
            context.getColorFromAttribute(R.attr.colorOnSecondaryContainer),
        )
    } else if (isStatusMessage) {
        AppCompatResources.getColorStateList(context, R.color.bubble_text_status_colorstatelist)
    } else {
        AppCompatResources.getColorStateList(
            context,
            if (isOutbox) R.color.bubble_send_text_colorstatelist else R.color.bubble_receive_text_colorstatelist,
        )
    }
