package ch.threema.app.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.isVisible
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.errorreporting.ErrorReportDetailsProvider
import ch.threema.app.errorreporting.ErrorReportingHelper
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = getThreemaLogger("ErrorReportingDialog")

class ErrorReportingDialog(
    private val errorReportingHelper: ErrorReportingHelper,
    private val preferenceService: PreferenceService,
) {
    fun showDialog(activity: HomeActivity) {
        activity.findViewById<ComposeView>(R.id.error_report_dialog_compose_view).apply {
            isVisible = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var dialogVisible by rememberSaveable {
                    mutableStateOf(true)
                }
                var rememberChoice by rememberSaveable {
                    mutableStateOf(
                        preferenceService.getErrorReportingState() != PreferenceService.ErrorReportingState.ALWAYS_ASK,
                    )
                }

                var details by rememberSaveable {
                    mutableStateOf<String?>(null)
                }
                var detailsLoading by rememberSaveable {
                    mutableStateOf(false)
                }
                val errorReportDetailsProvider: ErrorReportDetailsProvider = koinInject()
                if (details == null && detailsLoading) {
                    LaunchedEffect(Unit) {
                        try {
                            details = errorReportDetailsProvider.get()
                        } finally {
                            detailsLoading = false
                        }
                    }
                }

                if (!dialogVisible) {
                    return@setContent
                }

                val coroutineScope = rememberCoroutineScope()
                ThreemaTheme {
                    ErrorReportingDialog(
                        details = details,
                        rememberChoice = rememberChoice,
                        onClickLearnMore = {
                            goToFaqPage(activity)
                        },
                        onChangeRememberChoice = { newValue ->
                            rememberChoice = newValue
                        },
                        onRequestDetails = {
                            if (details == null) {
                                detailsLoading = true
                            }
                        },
                        onConfirm = {
                            dialogVisible = false
                            preferenceService.setErrorReportingState(
                                if (rememberChoice) {
                                    PreferenceService.ErrorReportingState.ALWAYS_SEND
                                } else {
                                    PreferenceService.ErrorReportingState.ALWAYS_ASK
                                },
                            )
                            coroutineScope.launch {
                                errorReportingHelper.confirmRecordsAndScheduleSending()
                            }
                        },
                        onDismiss = {
                            dialogVisible = false
                            preferenceService.setErrorReportingState(
                                if (rememberChoice) {
                                    PreferenceService.ErrorReportingState.NEVER_SEND
                                } else {
                                    PreferenceService.ErrorReportingState.ALWAYS_ASK
                                },
                            )
                            coroutineScope.launch {
                                errorReportingHelper.deletePendingRecords()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun goToFaqPage(context: Context) {
        try {
            val learnMoreUrl = context.getString(R.string.error_reporting_learn_more_url).toUri()
            context.startActivity(Intent(Intent.ACTION_VIEW, learnMoreUrl))
        } catch (e: ActivityNotFoundException) {
            logger.warn("No activity found to open learn more URL", e)
            context.showToast(R.string.an_error_occurred)
        }
    }
}

@Composable
private fun ErrorReportingDialog(
    details: String?,
    rememberChoice: Boolean,
    onChangeRememberChoice: (Boolean) -> Unit,
    onClickLearnMore: () -> Unit,
    onRequestDetails: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var detailsExpanded by rememberSaveable {
        mutableStateOf(false)
    }

    AlertDialog(
        properties = DialogProperties(
            dismissOnClickOutside = false,
        ),
        title = {
            Text(
                stringResource(R.string.error_detected_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.crash_detected_dialog_text),
                    style = MaterialTheme.typography.bodyMedium,
                )

                LearnMoreButton(
                    onClick = onClickLearnMore,
                )

                SpacerVertical(height = GridUnit.x1)

                RememberChoiceCheckbox(
                    rememberChoice = rememberChoice,
                    onChangeRememberChoice = onChangeRememberChoice,
                )

                SpacerVertical(height = GridUnit.x1_5)

                DetailsBox(
                    details = details,
                    expanded = detailsExpanded,
                    onClick = {
                        detailsExpanded = !detailsExpanded
                        if (detailsExpanded) {
                            onRequestDetails()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.crash_detected_dialog_send_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun RememberChoiceCheckbox(
    rememberChoice: Boolean,
    onChangeRememberChoice: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = rememberChoice,
                onValueChange = { onChangeRememberChoice(!rememberChoice) },
                role = Role.Checkbox,
            )
            .padding(vertical = GridUnit.x0_5),
        horizontalArrangement = Arrangement.spacedBy(GridUnit.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = rememberChoice,
            onCheckedChange = null,
        )
        Text(
            text = stringResource(R.string.crash_detected_dialog_remember_choice),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DetailsBox(
    expanded: Boolean,
    onClick: () -> Unit,
    details: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = GridUnit.x0_5)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GridUnit.x0_25),
        ) {
            Text(
                text = stringResource(R.string.error_reporting_details_button),
                style = MaterialTheme.typography.bodyMedium,
            )

            val rotation by animateFloatAsState(if (expanded) 0f else -90f)
            Icon(
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                painter = painterResource(R.drawable.ic_chevron_down_slightly_bigger),
                contentDescription = null,
            )
        }

        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = GridUnit.x0_5),
                contentAlignment = Alignment.Center,
            ) {
                if (details == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(GridUnit.x2),
                    )
                }
                SelectionContainer {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = details ?: "",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LearnMoreButton(
    onClick: () -> Unit,
) {
    TextButton(
        contentPadding = PaddingValues(vertical = GridUnit.x0_5),
        onClick = onClick,
    ) {
        Text(
            text = stringResource(R.string.error_reporting_learn_more_button),
        )
    }
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun ErrorReportingDialog_Preview() {
    ErrorReportingDialog(
        rememberChoice = false,
        details = null,
        onRequestDetails = {},
        onClickLearnMore = {},
        onChangeRememberChoice = {},
        onConfirm = {},
        onDismiss = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun DetailsBox_Collapsed_Preview() {
    DetailsBox(
        expanded = false,
        details = null,
        onClick = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun DetailsBox_Loading_Preview() {
    DetailsBox(
        expanded = true,
        details = null,
        onClick = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun DetailsBox_Expanded_Preview() {
    DetailsBox(
        expanded = true,
        details = "Hello World\nHere's the details",
        onClick = {},
    )
}
