package ch.threema.app.ui.interop

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import ch.threema.app.R
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.TextButtonPrimary
import ch.threema.app.compose.theme.ThreemaTheme

class TextButtonPrimaryXml @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var text: String by mutableStateOf("")
    var isButtonEnabled: Boolean by mutableStateOf(true)
    var trailingIconRes: Int? by mutableStateOf(null)

    init {
        val typedArray = context.theme.obtainStyledAttributes(
            /* set = */
            attrs,
            /* attrs = */
            R.styleable.TextButtonPrimaryXml,
            /* defStyleAttr = */
            defStyleAttr,
            /* defStyleRes = */
            0,
        )
        with(typedArray) {
            getResourceId(R.styleable.TextButtonPrimaryXml_textButtonPrimary_text, NO_RES_ID).let { initialTextResId ->
                if (initialTextResId != NO_RES_ID) {
                    text = context.getString(initialTextResId)
                }
            }
            getResourceId(R.styleable.TextButtonPrimaryXml_textButtonPrimary_trailingIcon, NO_RES_ID)
                .let { initialTrailingIconResId ->
                    if (initialTrailingIconResId != NO_RES_ID) {
                        trailingIconRes = initialTrailingIconResId
                    }
                }
            recycle()
        }
    }

    @Composable
    override fun Content() {
        ThreemaTheme {
            TextButtonPrimary(
                text = text,
                maxLines = 2,
                enabled = isButtonEnabled,
                trailingIcon = trailingIconRes?.let { iconRes ->
                    ButtonIconInfo(
                        iconRes = iconRes,
                    )
                },
                onClick = {
                    super.performClick()
                },
            )
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isButtonEnabled = enabled
    }

    companion object {
        private const val NO_RES_ID = 0
    }
}
