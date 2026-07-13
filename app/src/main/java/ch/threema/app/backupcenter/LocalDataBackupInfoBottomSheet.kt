package ch.threema.app.backupcenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.dimens.GridUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDataBackupInfoBottomSheet(
    onDismiss: () -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
    ) {
        LocalDataBackupBottomSheetContent()
    }
}

@Composable
private fun LocalDataBackupBottomSheetContent() {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = GridUnit.x5)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpacerVertical(GridUnit.x1)

        Image(
            modifier = Modifier
                .padding(GridUnit.x4)
                .width(120.dp),
            painter = painterResource(R.drawable.illustration_backup),
            contentDescription = null,
        )

        SpacerVertical(GridUnit.x1)

        ThemedText(
            text = stringResource(R.string.local_data_backup_bottom_sheet_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        SpacerVertical(GridUnit.x2)

        ThemedText(
            text = stringResource(R.string.local_data_backup_bottom_sheet_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        SpacerVertical(GridUnit.x5)
    }
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun LocalDataBackupInfoBottomSheet_Preview() {
    LocalDataBackupBottomSheetContent()
}
