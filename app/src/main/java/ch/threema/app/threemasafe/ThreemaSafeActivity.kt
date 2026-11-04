package ch.threema.app.threemasafe

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import ch.threema.android.buildActivityIntent
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.activities.ThreemaComposeActivity
import ch.threema.app.compose.common.ScreenContentContainer
import ch.threema.app.compose.common.appbars.AppBar
import ch.threema.app.compose.common.appbars.NavigationIcon
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.buttons.primary.ButtonPrimary
import ch.threema.app.compose.common.extensions.format
import ch.threema.app.compose.common.extensions.withSpacing
import ch.threema.app.compose.common.loading.FullScreenLoadingIndicator
import ch.threema.app.compose.common.spacer.SpacerRemainingVertical
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.surface.RoundedCornerSurface
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.common.time.rememberDateTimeFormatter
import ch.threema.app.compose.preview.PreviewData.INSTANT_1
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.di.isSessionScopeReady
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.kiloBytes
import ch.threema.common.megaBytes
import java.time.format.FormatStyle
import org.koin.androidx.compose.koinViewModel

private val logger = getThreemaLogger("ThreemaSafeActivity")

class ThreemaSafeActivity : ThreemaComposeActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isSessionScopeReady()) {
            finish()
            return
        }

        @Suppress("SimplifyBooleanWithConstants")
        if (!BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED) {
            finish()
            return
        }

        setContent {
            val viewModel = koinViewModel<ThreemaSafeViewModel>()
            EventHandler(viewModel) { event ->
                when (event) {
                    ThreemaSafeViewModelEvent.Finish -> finish()
                }
            }
            ThreemaTheme {
                ThreemaSafeScreen(
                    viewModel,
                    onClickBack = {
                        finish()
                    },
                    onClickChangePassword = {
                        val intent = ThreemaSafeConfigureActivity.createChangePasswordIntent(this)
                        startActivity(intent)
                    },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<ThreemaSafeActivity>(context)
    }
}

@Composable
private fun ThreemaSafeScreen(
    viewModel: ThreemaSafeViewModel,
    onClickBack: () -> Unit,
    onClickChangePassword: () -> Unit,
) {
    WithViewState(viewModel) { state ->
        ThreemaSafeScreenContent(
            state = state,
            onClickBack = onClickBack,
            onClickChangePassword = onClickChangePassword,
            onClickBackUpNow = viewModel::onClickBackUpNow,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreemaSafeScreenContent(
    state: ThreemaSafeViewState?,
    onClickBack: () -> Unit,
    onClickChangePassword: () -> Unit,
    onClickBackUpNow: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets
            .safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            AppBar(
                title = stringResource(R.string.threema_safe),
                navigationIcon = NavigationIcon.back(
                    onClick = onClickBack,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state == null) {
            FullScreenLoadingIndicator(contentPadding)
            return@Scaffold
        }

        ScreenContentContainer(
            contentPadding = contentPadding,
            modifier = Modifier.padding(vertical = GridUnit.x3),
        ) {
            val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)
            LastBackupDetails(
                modifier = horizontalPaddingModifier,
                lastBackupData = state.lastBackupData,
            )

            SpacerVertical(GridUnit.x2)

            ConfigurationDetails(
                modifier = horizontalPaddingModifier,
                configuration = state.configuration,
            )

            if (state.changePasswordButtonVisible) {
                SpacerVertical(GridUnit.x1)

                ButtonOutlined(
                    modifier = horizontalPaddingModifier.fillMaxWidth(),
                    text = stringResource(R.string.safe_change_password),
                    leadingIcon = ButtonIconInfo(
                        iconRes = R.drawable.ic_key_vertical,
                    ),
                    shape = ShapeDefaults.Medium,
                    onClick = onClickChangePassword,
                )
            }

            SpacerRemainingVertical(minHeight = GridUnit.x2)

            ButtonPrimary(
                modifier = horizontalPaddingModifier
                    .fillMaxWidth(),
                onClick = onClickBackUpNow,
                enabled = state.backUpButtonEnabled,
                text = stringResource(R.string.safe_backup_now),
            )
        }
    }
}

@Composable
private fun LastBackupDetails(
    modifier: Modifier,
    lastBackupData: ThreemaSafeViewState.LastBackupData,
) {
    RoundedCornerSurface(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(GridUnit.x2),
        ) {
            ThemedText(
                text = stringResource(R.string.android_backup_date),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            SpacerVertical(GridUnit.x1)

            Column(
                verticalArrangement = Arrangement.spacedBy(GridUnit.x1),
            ) {
                LabeledRow(
                    label = stringResource(R.string.threema_safe_last_backup_date_and_time),
                ) {
                    Text(
                        text = lastBackupData.time?.let { time ->
                            val formatter = rememberDateTimeFormatter(FormatStyle.SHORT)
                            formatter.format(time)
                        }
                            ?: "-",
                    )
                }
                LabeledRow(
                    label = stringResource(R.string.safe_size),
                ) {
                    Text(lastBackupData.size?.format() ?: "-")
                }

                LabeledRow(
                    label = stringResource(R.string.safe_result),
                ) {
                    if (lastBackupData.status == ThreemaSafeViewState.LastBackupStatus.SUCCESS) {
                        Text(
                            text = stringResource(R.string.safe_successful),
                            color = colorResource(R.color.material_green_700),
                        )
                    } else {
                        val errorMessage = when (lastBackupData.status) {
                            ThreemaSafeViewState.LastBackupStatus.CONNECTION_ERROR -> {
                                stringResource(R.string.safe_connection_error)
                            }
                            ThreemaSafeViewState.LastBackupStatus.GENERIC_ERROR -> {
                                stringResource(R.string.safe_unsuccessful)
                            }
                            ThreemaSafeViewState.LastBackupStatus.SIZE_LIMIT_EXCEEDED -> {
                                stringResource(R.string.safe_upload_size_exceeded)
                            }
                            ThreemaSafeViewState.LastBackupStatus.UPLOAD_FAILED -> {
                                stringResource(R.string.safe_upload_failed)
                            }
                        }
                        Text(
                            text = errorMessage,
                            color = colorResource(R.color.status_foreground_destructive),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween.withSpacing(GridUnit.x1),
    ) {
        val labelStyle = MaterialTheme.typography.titleMedium
        ThemedText(
            modifier = Modifier.padding(end = GridUnit.x1),
            text = label,
            style = labelStyle,
            fontWeight = FontWeight.Normal,
        )
        val contentStyle = MaterialTheme.typography.titleSmall
            .copy(
                lineHeight = labelStyle.lineHeight,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Normal,
            )
        ProvideTextStyle(contentStyle) {
            Box(modifier = Modifier.weight(1f, fill = false)) {
                content()
            }
        }
    }
}

@Composable
private fun ConfigurationDetails(
    modifier: Modifier,
    configuration: ThreemaSafeViewState.Configuration,
) {
    RoundedCornerSurface(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(GridUnit.x2),
        ) {
            ThemedText(
                text = stringResource(R.string.threema_safe_configuration_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            SpacerVertical(GridUnit.x1)

            Column(
                verticalArrangement = Arrangement.spacedBy(GridUnit.x1),
            ) {
                LabeledRow(
                    label = stringResource(R.string.safe_server_name),
                ) {
                    Text(
                        configuration.server
                            ?: stringResource(R.string.safe_use_default_server),
                    )
                }
                LabeledRow(
                    label = stringResource(R.string.safe_max_backup_size),
                ) {
                    Text(configuration.maxBackupSize.format())
                }
                LabeledRow(
                    label = stringResource(R.string.safe_retention),
                ) {
                    Text(
                        text = stringResource(R.string.number_of_days, configuration.backupRetentionDays),
                    )
                }
            }
        }
    }
}

@PreviewThreemaAll
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun ThreemaSafeScreenContent_Screen() {
    ThreemaSafeScreenContent(
        state = ThreemaSafeViewState(
            backUpButtonEnabled = true,
            lastBackupData = ThreemaSafeViewState.LastBackupData(
                time = INSTANT_1,
                size = 123.kiloBytes,
                status = ThreemaSafeViewState.LastBackupStatus.SUCCESS,
            ),
            configuration = ThreemaSafeViewState.Configuration(
                server = null,
                maxBackupSize = 3.megaBytes,
                backupRetentionDays = 180,
            ),
        ),
        onClickBack = {},
        onClickChangePassword = {},
        onClickBackUpNow = {},
    )
}
