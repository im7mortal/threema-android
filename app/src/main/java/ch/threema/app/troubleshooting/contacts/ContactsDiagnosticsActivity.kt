package ch.threema.app.troubleshooting.contacts

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.threema.android.buildActivityIntent
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.compose.common.appbars.AppBar
import ch.threema.app.compose.common.appbars.NavigationIcon
import ch.threema.app.compose.common.buttons.primary.ButtonPrimary
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.framework.WithViewState
import org.koin.androidx.compose.koinViewModel

class ContactsDiagnosticsActivity : ThreemaAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel = koinViewModel<ContactsDiagnosticsViewModel>()
            ThreemaTheme {
                ContactsDiagnosticsScreen(
                    viewModel,
                    onClickBack = {
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<ContactsDiagnosticsActivity>(context)
    }
}

@Composable
private fun ContactsDiagnosticsScreen(
    viewModel: ContactsDiagnosticsViewModel,
    onClickBack: () -> Unit,
) {
    var fixProblemsWarningVisible by remember {
        mutableStateOf(false)
    }

    if (fixProblemsWarningVisible) {
        ConfirmDialog(
            onConfirm = {
                fixProblemsWarningVisible = false
                viewModel.onClickFixProblems()
            },
            onDismiss = onClickBack,
        )
    }

    WithViewState(viewModel) { state ->
        ContactsDiagnosticsScreenContent(
            state = state,
            onClickBack = onClickBack,
            onClickFixProblems = {
                fixProblemsWarningVisible = true
            },
        )
    }
}

@Composable
private fun ConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                "This will attempt to delete all invalid contacts listed on this screen. No messages will be deleted. " +
                    "Nonetheless, we advise that you create a backup of your data first before proceeding.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text("Proceed")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        onDismissRequest = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsDiagnosticsScreenContent(
    state: ContactsDiagnosticsViewState?,
    onClickBack: () -> Unit,
    onClickFixProblems: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppBar(
                title = "Contacts Diagnostics",
                navigationIcon = NavigationIcon.back(
                    onClick = onClickBack,
                ),
            )
        },
    ) { contentPadding ->
        if (state == null || state.fixInProgress) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(GridUnit.x10),
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(state.contactsWithProblems) { contact ->
                    ProblematicContact(contact)
                }

                if (state.contactsWithProblems.isEmpty()) {
                    item {
                        Text(
                            text = "No problems found",
                            modifier = Modifier.padding(GridUnit.x2),
                        )
                    }
                }
            }
            if (!state.contactsWithProblems.isEmpty()) {
                Row(
                    modifier = Modifier.padding(GridUnit.x2),
                ) {
                    ButtonPrimary(
                        onClick = onClickFixProblems,
                        text = "Fix problems",
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblematicContact(
    contact: ContactsDiagnosticsViewState.ContactUiModel,
) {
    ListItem(
        headlineContent = {
            Text(contact.displayName)
        },
        supportingContent = {
            Text(contact.problem)
        },
    )
}

@PreviewThreemaAll
@Composable
private fun ContactsDiagnosticsScreenContent_Preview() {
    ThreemaThemePreview {
        ContactsDiagnosticsScreenContent(
            state = ContactsDiagnosticsViewState(
                contactsWithProblems = listOf(
                    ContactsDiagnosticsViewState.ContactUiModel(
                        identity = PreviewData.IDENTITY_OTHER_1,
                        name = "Alice",
                        problem = "Contact has invalid public key",
                    ),
                    ContactsDiagnosticsViewState.ContactUiModel(
                        identity = PreviewData.IDENTITY_OTHER_2,
                        name = "Bob",
                        problem = "Contact is bad at math",
                    ),
                ),
            ),
            onClickBack = {},
            onClickFixProblems = {},
        )
    }
}
