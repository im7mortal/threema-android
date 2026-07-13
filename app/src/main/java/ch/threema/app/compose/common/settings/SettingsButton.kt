package ch.threema.app.compose.common.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import ch.threema.app.compose.common.spacer.SpacerHorizontal
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    title: String,
    summary: String,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(modifier),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            ThemedText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
            )

            SpacerVertical(GridUnit.x0_5)

            ThemedText(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
        }
        trailingContent?.let {
            SpacerHorizontal(width = GridUnit.x1)
            trailingContent()
        }
    }
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun SettingsButton_Preview() {
    SettingsButton(
        title = "Some cool button",
        summary = "It has a description and everything",
        onClick = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun SettingsButton_WithTrailingContent_Preview() {
    SettingsButton(
        title = "Some cool button",
        summary = "It has a description and everything",
        trailingContent = {
            Switch(
                checked = true,
                onCheckedChange = {},
            )
        },
        onClick = {},
    )
}
