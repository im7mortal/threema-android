package ch.threema.app.backupcenter

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import ch.threema.android.buildActivityIntent
import ch.threema.android.showToast
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.activities.ThreemaComposeActivity
import ch.threema.app.compose.common.ScreenContentContainer
import ch.threema.app.compose.common.appbars.AppBar
import ch.threema.app.compose.common.appbars.NavigationIcon
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.buttons.primary.ButtonPrimaryRounded
import ch.threema.app.compose.common.extensions.format
import ch.threema.app.compose.common.extensions.withSpacing
import ch.threema.app.compose.common.loading.FullScreenLoadingIndicator
import ch.threema.app.compose.common.spacer.SpacerRemainingVertical
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.common.time.rememberDateTimeFormatter
import ch.threema.app.compose.preview.PreviewData.INSTANT_1
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.framework.WithViewState
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.common.kiloBytes
import java.time.format.FormatStyle
import kotlin.getValue
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class LocalDataBackupActivity : ThreemaComposeActivity() {

    private val appRestrictions: AppRestrictions by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        if (!BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED || appRestrictions.isBackupsDisabled() || appRestrictions.isDataBackupsDisabled()) {
            finish()
            return
        }

        setContent {
            val viewModel = koinViewModel<LocalDataBackupViewModel>()
            ThreemaTheme {
                LocalDataBackupScreen(
                    viewModel,
                    onClickBack = {
                        finish()
                    },
                    onClickShareBackup = {
                        // TODO(ANDR-4877): Implement
                        showNotImplementedToast()
                    },
                    onClickRecoveryKey = {
                        // TODO(ANDR-4877): Implement
                        showNotImplementedToast()
                    },
                    onClickCreateBackup = {
                        startActivity(CreateLocalDataBackupActivity.createIntent(this))
                    },
                )
            }
        }
    }

    private fun showNotImplementedToast() {
        showToast("Not yet implemented")
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<LocalDataBackupActivity>(context)
    }
}

@Composable
private fun LocalDataBackupScreen(
    viewModel: LocalDataBackupViewModel,
    onClickBack: () -> Unit,
    onClickShareBackup: () -> Unit,
    onClickRecoveryKey: () -> Unit,
    onClickCreateBackup: () -> Unit,
) {
    WithViewState(viewModel) { state ->
        LocalDataBackupScreenContent(
            state = state,
            onClickBack = onClickBack,
            onClickShareBackup = onClickShareBackup,
            onClickRecoveryKey = onClickRecoveryKey,
            onClickCreateBackup = onClickCreateBackup,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalDataBackupScreenContent(
    state: LocalDataBackupViewState?,
    onClickBack: () -> Unit,
    onClickShareBackup: () -> Unit,
    onClickRecoveryKey: () -> Unit,
    onClickCreateBackup: () -> Unit,
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
                title = stringResource(R.string.local_data_backup_title),
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
            SpacerVertical(GridUnit.x5)

            ButtonOutlined(
                modifier = horizontalPaddingModifier.fillMaxWidth(),
                text = stringResource(R.string.local_data_backup_share_backup_button),
                leadingIcon = ButtonIconInfo(
                    iconRes = R.drawable.ic_share_outline,
                ),
                onClick = onClickShareBackup,
            )

            SpacerVertical(GridUnit.x1)

            ButtonOutlined(
                modifier = horizontalPaddingModifier.fillMaxWidth(),
                text = stringResource(R.string.local_data_backup_recovery_key_button),
                leadingIcon = ButtonIconInfo(
                    iconRes = R.drawable.ic_key_vertical,
                ),
                onClick = onClickRecoveryKey,
            )

            SpacerRemainingVertical(minHeight = GridUnit.x1)

            ButtonPrimaryRounded(
                modifier = horizontalPaddingModifier
                    .fillMaxWidth(),
                onClick = onClickCreateBackup,
                text = stringResource(R.string.local_data_backup_create_backup_button),
            )
        }
    }
}

@Composable
private fun LastBackupDetails(
    modifier: Modifier,
    lastBackupData: LocalDataBackupViewState.LastBackupData,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(size = GridUnit.x1_5),
    ) {
        Column(
            modifier = Modifier.padding(GridUnit.x2),
        ) {
            ThemedText(
                text = stringResource(R.string.local_data_backup_last_backup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            SpacerVertical(GridUnit.x1)

            Column(
                verticalArrangement = Arrangement.spacedBy(GridUnit.x1),
            ) {
                LabeledRow(
                    label = stringResource(R.string.local_data_backup_date_and_time),
                ) {
                    val formatter = rememberDateTimeFormatter(FormatStyle.MEDIUM)
                    val formattedTime = formatter.format(lastBackupData.time)
                    Text(formattedTime)
                }
                LabeledRow(
                    label = stringResource(R.string.local_data_backup_size),
                ) {
                    Text(lastBackupData.size.format())
                }
                LabeledRow(
                    label = stringResource(R.string.local_data_backup_messages),
                ) {
                    Text(
                        text = stringResource(R.string.local_data_backup_included),
                        color = colorResource(R.color.material_green_700),
                    )
                }
                LabeledRow(
                    label = stringResource(R.string.local_data_backup_media),
                ) {
                    Text(
                        text = if (lastBackupData.mediaIncluded) {
                            stringResource(R.string.local_data_backup_included)
                        } else {
                            stringResource(R.string.local_data_backup_not_included)
                        },
                        color = if (lastBackupData.mediaIncluded) {
                            colorResource(R.color.material_green_700)
                        } else {
                            Color.Unspecified
                        },
                    )
                }
                LabeledRow(
                    label = stringResource(R.string.local_data_backup_location),
                ) {
                    Text(lastBackupData.location)
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

@PreviewThreemaAll
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun LocalDataBackupScreenContent_Screen() {
    LocalDataBackupScreenContent(
        state = LocalDataBackupViewState(
            lastBackupData = LocalDataBackupViewState.LastBackupData(
                time = INSTANT_1,
                size = 754.kiloBytes,
                mediaIncluded = false,
                location = "Threema Backups/BackupFile.dat",
            ),
        ),
        onClickBack = {},
        onClickShareBackup = {},
        onClickRecoveryKey = {},
        onClickCreateBackup = {},
    )
}
