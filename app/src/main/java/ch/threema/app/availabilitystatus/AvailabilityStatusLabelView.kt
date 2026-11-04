package ch.threema.app.availabilitystatus

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.compose.common.extensions.get
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.EmojiSettings
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.data.datatypes.AvailabilityStatus
import kotlinx.coroutines.flow.MutableStateFlow

class AvailabilityStatusLabelView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val state = MutableStateFlow(
        AvailabilityStatusLabelState(
            availabilityStatusSet = null,
            emojiStyle = EmojiStyle.ANDROID,
        ),
    )

    fun setState(availabilityStatusSet: AvailabilityStatus.Set?, emojiStyle: EmojiStyle) {
        state.value = AvailabilityStatusLabelState(
            availabilityStatusSet = availabilityStatusSet,
            emojiStyle = emojiStyle,
        )
    }

    @Composable
    override fun Content() {
        val state by state.collectAsStateWithLifecycle()
        state.availabilityStatusSet?.let { availabilityStatusSet ->
            ThreemaTheme {
                AvailabilityStatusLabel(
                    availabilityStatusSet = availabilityStatusSet,
                    emojiStyle = state.emojiStyle,
                )
            }
        }
    }
}

private data class AvailabilityStatusLabelState(
    val availabilityStatusSet: AvailabilityStatus.Set?,
    val emojiStyle: EmojiStyle,
)

@Composable
private fun AvailabilityStatusLabel(
    availabilityStatusSet: AvailabilityStatus.Set,
    emojiStyle: EmojiStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GridUnit.x1),
        horizontalArrangement = Arrangement.spacedBy(GridUnit.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(availabilityStatusSet.iconRes()),
            tint = availabilityStatusSet.iconColor(),
            contentDescription = null,
        )
        ConversationText(
            rawInput = availabilityStatusSet.displayText().get(),
            textStyle = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            emojiSettings = EmojiSettings(
                style = emojiStyle,
            ),
            mentionFeature = MentionFeature.Off,
            markupEnabled = false,
        )
    }
}
