package ch.threema.app.compose.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.dimens.GridUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressDialog(
    title: String? = null,
    text: String? = null,
    onDismissRequest: () -> Unit = {},
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Row(
                modifier = Modifier.padding(GridUnit.x3),
                horizontalArrangement = Arrangement.spacedBy(GridUnit.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(GridUnit.x5),
                )
                if (title != null || text != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridUnit.x1),
                    ) {
                        if (title != null) {
                            ThemedText(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (text != null) {
                            ThemedText(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
