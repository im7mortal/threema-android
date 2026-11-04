package ch.threema.app.ui

import android.text.style.ClickableSpan
import android.view.View
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.Mention

private val logger = getThreemaLogger("MentionClickableSpan")

class MentionClickableSpan(
    @JvmField val mention: Mention,
) : ClickableSpan() {
    override fun onClick(widget: View) {
        logger.debug("onClick")
    }
}
