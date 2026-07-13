package ch.threema.app.appstartup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.AppTypography
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.storage.DatabaseDowngradeHelperImpl
import ch.threema.storage.DatabaseUpdater
import org.koin.mp.KoinPlatform

@Composable
fun ForceDowngradeButton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpacerVertical(GridUnit.x2)
        ButtonOutlined(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                KoinPlatform.getKoin().get<DatabaseDowngradeHelperImpl>().forceEnableDowngrade()
            },
            text = "⚠\uFE0F Force-downgrade to version ${DatabaseUpdater.VERSION}",
            maxLines = 2,
        )

        SpacerVertical(GridUnit.x0_5)
        ThemedText(
            text = "Data-loss may occur, use at your own risk!",
            style = AppTypography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
