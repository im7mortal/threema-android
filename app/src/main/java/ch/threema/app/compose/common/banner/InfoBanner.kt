package ch.threema.app.compose.common.banner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun InfoBanner(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    onClickDismiss: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(GridUnit.x1_5),
        color = colorResource(R.color.status_background_info),
        contentColor = colorResource(R.color.on_status_background),
    ) {
        Row(
            modifier = Modifier.padding(GridUnit.x1_5),
            horizontalArrangement = Arrangement.spacedBy(GridUnit.x1),
        ) {
            leadingContent?.let {
                Box {
                    leadingContent()
                }
            }
            Box(
                modifier = Modifier.weight(1f),
            ) {
                CompositionLocalProvider(LocalTextStyle provides textStyle) {
                    content()
                }
            }
            onClickDismiss?.let {
                IconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = onClickDismiss,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_cancel),
                        contentDescription = stringResource(R.string.accessibility_dismiss_hint),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun InfoBanner_Simple_Preview() {
    InfoBanner(
        modifier = Modifier.padding(8.dp),
    ) {
        Text(
            text = "This is an informative info banner of information to inform the user, so that they are informed.",
        )
    }
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun InfoBanner_WithLeadingIcon_Preview() {
    InfoBanner(
        modifier = Modifier.padding(8.dp),
        leadingContent = {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_info_rounded),
                contentDescription = null,
            )
        },
    ) {
        Text(
            text = "This is an informative info banner of information to inform the user, so that they are informed.",
        )
    }
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun InfoBanner_WithDismiss_Preview() {
    InfoBanner(
        modifier = Modifier.padding(8.dp),
        onClickDismiss = {},
    ) {
        Text(
            text = "This is a dismissable info banner of information to inform the user, so that they are informed.",
        )
    }
}
