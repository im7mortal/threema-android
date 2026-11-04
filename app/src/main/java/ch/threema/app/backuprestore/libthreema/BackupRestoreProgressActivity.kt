package ch.threema.app.backuprestore.libthreema

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.android.buildActivityIntent
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.app.activities.AppLockExempt
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.compose.common.buttons.TextButtonPrimary
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.home.HomeActivity
import kotlin.system.exitProcess
import org.koin.androidx.compose.koinViewModel

// TODO(ANDR-4878): Replace this
class BackupRestoreProgressActivity : ThreemaAppCompatActivity(), AppLockExempt {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: BackupRestoreViewModel = koinViewModel()
            ThreemaTheme {
                val backupRestoreState by viewModel.state.collectAsStateWithLifecycle()
                BackupRestoreScreen(
                    backupRestoreState = backupRestoreState,
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<BackupRestoreProgressActivity>(context)
    }
}

@Composable
fun BackupRestoreScreen(backupRestoreState: BackupRestoreState) {
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (backupRestoreState) {
                    is BackupRestoreState.Initial ->
                        RestoreInProgress(
                            stepLabel = "Initial",
                        )
                    is BackupRestoreState.InProgress ->
                        RestoreInProgress(
                            stepLabel = backupRestoreState.step.name,
                        )

                    is BackupRestoreState.Done.Failure ->
                        RestoreDone(
                            success = false,
                            message = backupRestoreState.errorMessage.get(LocalContext.current),
                        )

                    is BackupRestoreState.Done.Success ->
                        RestoreDone(
                            success = true,
                            message = "Restore complete",
                        )
                }
            }
        }
    }
}

@Composable
fun RestoreInProgress(stepLabel: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stepLabel,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun RestoreDone(
    success: Boolean,
    message: String,
) {
    val activity = LocalActivity.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(
                id = if (success) R.drawable.emoji_party_popper else R.drawable.ic_error_rounded,
            ),
            contentDescription = null,
            tint = if (success) Color.Unspecified else LocalContentColor.current,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButtonPrimary(
            text = if (success) "Continue" else "Close app",
            onClick = {
                if (success) {
                    activity?.startActivity(Intent(activity, HomeActivity::class.java))
                    activity?.finish()
                } else {
                    exitProcess(0)
                }
            },
        )
    }
}

@Composable
@Preview
fun BackupRestoreScreen_Preview_Initial() {
    ThreemaThemePreview {
        BackupRestoreScreen(
            backupRestoreState = BackupRestoreState.Initial,
        )
    }
}

@Composable
@Preview
fun BackupRestoreScreen_Preview_InProgress() {
    ThreemaThemePreview {
        BackupRestoreScreen(
            backupRestoreState = BackupRestoreState.InProgress(
                step = BackupRestoreStep.CONTACTS,
            ),
        )
    }
}

@Composable
@Preview
fun BackupRestoreScreen_Preview_Done_Success() {
    ThreemaThemePreview {
        BackupRestoreScreen(
            backupRestoreState = BackupRestoreState.Done.Success,
        )
    }
}

@Composable
@Preview
fun BackupRestoreScreen_Preview_Done_Failure() {
    ThreemaThemePreview {
        BackupRestoreScreen(
            backupRestoreState = BackupRestoreState.Done.Failure(
                errorMessage = "Preview error message.".toResolvedString(),
            ),
        )
    }
}
