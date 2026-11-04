package ch.threema.app.ui.spamgroups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.buttons.ButtonOutlinedDense
import ch.threema.app.compose.common.buttons.primary.ButtonPrimaryDense
import ch.threema.app.compose.common.spacer.SpacerHorizontal
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun SpamGroupBanner(
    modifier: Modifier = Modifier,
    onClickNotSpam: () -> Unit,
    onClickReportSpam: () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = RoundedCornerShape(
            size = dimensionResource(R.dimen.cardview_border_radius),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GridUnit.x1_5),
        ) {
            ThemedText(
                text = stringResource(R.string.group_spam_banner_message),
                style = MaterialTheme.typography.bodyLarge,
            )
            SpacerVertical(GridUnit.x1)
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonOutlinedDense(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = ShapeDefaults.Medium,
                    onClick = onClickNotSpam,
                    text = stringResource(R.string.group_spam_banner_button_not_spam),
                )
                SpacerHorizontal(GridUnit.x2)
                ButtonPrimaryDense(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onClickReportSpam,
                    text = stringResource(R.string.group_spam_banner_button_report_spam),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SpamGroupBanner_Preview() {
    ThreemaThemePreview {
        SpamGroupBanner(
            onClickNotSpam = {},
            onClickReportSpam = {},
        )
    }
}

@PreviewLightDark
@PreviewDynamicColors
@Composable
private fun SpamGroupBanner_Preview_Dynamic() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        SpamGroupBanner(
            onClickNotSpam = {},
            onClickReportSpam = {},
        )
    }
}
