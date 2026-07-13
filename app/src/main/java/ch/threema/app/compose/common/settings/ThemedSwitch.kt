package ch.threema.app.compose.common.settings

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import ch.threema.app.compose.theme.ThreemaPreviewWrapper

@Composable
fun ThemedSwitch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Switch(
        modifier = modifier,
        checked = checked,
        enabled = enabled,
        colors = SwitchDefaults.colors().copy(
            uncheckedTrackColor = Color.Transparent,
            disabledUncheckedTrackColor = Color.Transparent,
        ),
        onCheckedChange = onCheckedChange,
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun ThemedSwitch_Checked_Preview() {
    ThemedSwitch(
        checked = true,
        onCheckedChange = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun ThemedSwitch_Unchecked_Preview() {
    ThemedSwitch(
        checked = false,
        onCheckedChange = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun ThemedSwitch_CheckedDisabled_Preview() {
    ThemedSwitch(
        checked = true,
        enabled = false,
        onCheckedChange = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun ThemedSwitch_UncheckedDisabled_Preview() {
    ThemedSwitch(
        checked = false,
        enabled = false,
        onCheckedChange = {},
    )
}
