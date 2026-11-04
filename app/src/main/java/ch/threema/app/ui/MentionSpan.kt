package ch.threema.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.graphics.RectF
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.utils.NameUtil
import ch.threema.data.IdentityProvider
import ch.threema.domain.types.Mention
import kotlin.math.ceil
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class MentionSpan(
    private val appContext: Context,
    private val mention: Mention,
    @ColorInt
    backgroundColor: Int,
    @ColorInt
    invertedColor: Int,
    @ColorInt
    private val textColor: Int,
    @ColorInt
    private val invertedTextColor: Int,
    private val padding: Int,
    private val radius: Int,
    var strikeThroughText: Boolean,
) : ReplacementSpan(), KoinComponent {
    private val identityProvider: IdentityProvider by inject()
    private val contactService: ContactService by inject()
    private val userService: UserService by inject()

    private var width = 0
    private var labelText = ""

    private val backgroundPaint = Paint()
    private val invertedPaint = Paint()

    private val contactNameFormat by lazy(LazyThreadSafetyMode.NONE) {
        get<PreferenceService>().getContactNameFormat()
    }

    init {
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.setColor(backgroundColor)
        invertedPaint.style = Paint.Style.FILL
        invertedPaint.setColor(invertedColor)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: FontMetricsInt?,
    ): Int {
        if (fm != null) {
            // The span is not rendered if it spans the entire text and no height is set. Therefore, we need to apply the font metrics
            // here such that a height can be calculated.
            val fontMetrics = paint.getFontMetrics()
            fm.ascent = fontMetrics.ascent.toInt()
            fm.bottom = fontMetrics.bottom.toInt()
            fm.descent = fontMetrics.descent.toInt()
            fm.leading = fontMetrics.leading.toInt()
            fm.top = fontMetrics.top.toInt()
        }

        labelText = getMentionLabelText()
        width = paint.measureText(MENTION_INDICATOR + labelText).toInt() + (padding * 2)
        return width
    }

    private fun getMentionLabelText(): String =
        when (mention) {
            Mention.AllUsers -> appContext.getString(R.string.all)
            is Mention.Identity -> {
                var label = NameUtil.getQuoteName(mention.identityString, contactService, userService, contactNameFormat)
                if (label.length > LABEL_MAX_LENGTH) {
                    label = label.substring(0, LABEL_MAX_LENGTH).trim { it <= ' ' } + "…"
                }
                label
            }
        }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        if (width == 0) {
            return
        }
        val alpha = paint.alpha
        if (mention.includes(identityProvider.requireIdentity())) {
            canvas.drawRoundRect(
                RectF(x, top + 1f, x + width, bottom.toFloat()),
                radius.toFloat(),
                radius.toFloat(),
                invertedPaint,
            )
            paint.setColor(invertedTextColor)
            paint.setAlpha(ALPHA_INCLUDES_ME)
        } else {
            canvas.drawRoundRect(
                RectF(x, top + 1f, x + width, bottom.toFloat()),
                radius.toFloat(),
                radius.toFloat(),
                backgroundPaint,
            )
            paint.setColor(textColor)
            paint.setAlpha(ALPHA_DOES_NOT_INCLUDE_ME)
        }
        canvas.drawText(MENTION_INDICATOR, x + padding, y.toFloat(), paint)
        paint.setAlpha(ALPHA_FULL)

        canvas.drawText(
            labelText,
            x + padding + paint.measureText(MENTION_INDICATOR),
            y.toFloat(),
            paint,
        )
        paint.setAlpha(alpha)

        if (strikeThroughText) {
            setStrikeThroughText(canvas, paint, x, y.toFloat())
        }
    }

    private fun setStrikeThroughText(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        // Draw an empty string that covers the canvas with strike through flag
        val isStrikeThruText = paint.isStrikeThruText
        paint.isStrikeThruText = true

        // Create empty string that covers the whole canvas width (font dependent)
        val numSpaces = ceil((width / paint.measureText(" ")).toDouble()).toInt()
        val spaces = " ".repeat(numSpaces)
        canvas.drawText(spaces, x, y, paint)

        // Restore strike through state to prevent side effects
        paint.isStrikeThruText = isStrikeThruText
    }

    companion object {
        private const val ALPHA_FULL = 0xFF
        private const val ALPHA_INCLUDES_ME = 0x78
        private const val ALPHA_DOES_NOT_INCLUDE_ME = 0x50

        /**
         * Prefixed to a display name to indicate a mention in a human-readable manner.
         * Not to be confused with the raw mention syntax (@[...])
         *
         * @see Mention
         */
        const val MENTION_INDICATOR = "@"

        // ReplacementSpan does not support Spans that span multiple lines in a TextView!
        // Hack: Limit label length to 16 chars
        private const val LABEL_MAX_LENGTH = 16
    }
}
