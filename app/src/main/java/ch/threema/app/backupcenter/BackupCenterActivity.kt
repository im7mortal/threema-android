package ch.threema.app.backupcenter

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import ch.threema.android.buildActivityIntent
import ch.threema.android.showToast
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.activities.BackupAdminActivity
import ch.threema.app.activities.ThreemaComposeActivity
import ch.threema.app.compose.common.ScreenContentContainer
import ch.threema.app.compose.common.appbars.AppBar
import ch.threema.app.compose.common.appbars.NavigationIcon
import ch.threema.app.compose.common.banner.InfoBanner
import ch.threema.app.compose.common.loading.FullScreenLoadingIndicator
import ch.threema.app.compose.common.settings.SettingsButton
import ch.threema.app.compose.common.settings.ThemedSwitch
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.SectionTitle
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.common.time.rememberDateTimeFormatter
import ch.threema.app.compose.preview.PreviewData.INSTANT_1
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.framework.WithViewState
import ch.threema.app.restrictions.AppRestrictions
import java.time.Instant
import java.time.format.FormatStyle
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class BackupCenterActivity : ThreemaComposeActivity() {

    private val appRestrictions: AppRestrictions by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        if (!BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED || appRestrictions.isBackupsDisabled()) {
            finish()
            return
        }

        setContent {
            val viewModel = koinViewModel<BackupCenterViewModel>()
            ThreemaTheme {
                BackupCenterScreen(
                    viewModel,
                    onClickBack = {
                        finish()
                    },
                    onClickThreemaSafe = {
                        startActivity(BackupAdminActivity.createIntent(this, true))
                    },
                    onClickDataBackup = {
                        startActivity(LocalDataBackupActivity.createIntent(this))
                    },
                    onClickLearnMoreThreemaSafe = {
                        // TODO(ANDR-4880): Implement
                        showNotImplementedToast()
                    },
                )
            }
        }
    }

    private fun showNotImplementedToast() {
        showToast("Not yet implemented")
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<BackupCenterActivity>(context)
    }
}

@Composable
private fun BackupCenterScreen(
    viewModel: BackupCenterViewModel,
    onClickBack: () -> Unit,
    onClickThreemaSafe: () -> Unit,
    onClickDataBackup: () -> Unit,
    onClickLearnMoreThreemaSafe: () -> Unit,
) {
    var localDataBackupBottomSheetVisible by rememberSaveable {
        mutableStateOf(false)
    }
    WithViewState(viewModel) { state ->
        BackupCenterScreenContent(
            state = state,
            onClickBack = onClickBack,
            onClickDismissInfoBanner = viewModel::onClickDismissInfoBanner,
            onClickThreemaSafe = onClickThreemaSafe,
            onClickDataBackup = onClickDataBackup,
            onClickLearnMoreThreemaSafe = onClickLearnMoreThreemaSafe,
            onClickLearnMoreDataBackup = {
                localDataBackupBottomSheetVisible = true
            },
        )

        if (localDataBackupBottomSheetVisible) {
            LocalDataBackupInfoBottomSheet(
                onDismiss = {
                    localDataBackupBottomSheetVisible = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupCenterScreenContent(
    state: BackupCenterViewState?,
    onClickBack: () -> Unit,
    onClickDismissInfoBanner: () -> Unit,
    onClickThreemaSafe: () -> Unit,
    onClickDataBackup: () -> Unit,
    onClickLearnMoreThreemaSafe: () -> Unit,
    onClickLearnMoreDataBackup: () -> Unit,
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
                title = stringResource(R.string.my_backups_title),
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
            modifier = Modifier.padding(vertical = GridUnit.x2),
        ) {
            val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)
            AnimatedVisibility(visible = state.infoBannerVisible) {
                InfoBanner(
                    modifier = horizontalPaddingModifier
                        .padding(bottom = GridUnit.x4),
                    onClickDismiss = onClickDismissInfoBanner,
                ) {
                    Text(
                        text = stringResource(R.string.backup_explain_text),
                    )
                }
            }

            if (state.threemaSafeVisible) {
                SectionTitle(
                    modifier = horizontalPaddingModifier,
                    title = stringResource(R.string.backup_section_title_online),
                )

                SpacerVertical(GridUnit.x1)

                ThreemaSafeButton(
                    modifier = horizontalPaddingModifier,
                    threemaSafeEnabled = state.threemaSafeEnabled,
                    onClick = onClickThreemaSafe,
                )
                SpacerVertical(GridUnit.x0_5)
                LearnMoreButton(
                    modifier = horizontalPaddingModifier,
                    onClick = onClickLearnMoreThreemaSafe,
                )

                SpacerVertical(GridUnit.x4)
            }

            SectionTitle(
                modifier = horizontalPaddingModifier,
                title = stringResource(R.string.backup_section_title_local),
            )

            SpacerVertical(GridUnit.x1)

            DataBackupButton(
                modifier = horizontalPaddingModifier,
                onClick = onClickDataBackup,
            )
            SpacerVertical(GridUnit.x0_5)
            LastBackupTime(
                modifier = horizontalPaddingModifier,
                lastBackupTime = state.lastBackupTime,
            )
            SpacerVertical(GridUnit.x0_5)
            LearnMoreButton(
                modifier = horizontalPaddingModifier,
                onClick = onClickLearnMoreDataBackup,
            )

            SpacerVertical(GridUnit.x1)
        }
    }
}

@Composable
private fun ThreemaSafeButton(
    modifier: Modifier,
    threemaSafeEnabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsButton(
        modifier = modifier,
        title = stringResource(R.string.threema_safe),
        summary = stringResource(R.string.threema_safe_summary),
        trailingContent = {
            ThemedSwitch(
                checked = threemaSafeEnabled,
                onCheckedChange = null,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun DataBackupButton(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    SettingsButton(
        modifier = modifier,
        title = stringResource(R.string.backup_data_title),
        summary = stringResource(R.string.backup_data_summary),
        onClick = onClick,
    )
}

@Composable
private fun LearnMoreButton(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = GridUnit.x1)
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GridUnit.x1),
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_info_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        ThemedText(
            text = stringResource(R.string.safe_learn_more_button),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LastBackupTime(
    modifier: Modifier,
    lastBackupTime: Instant?,
) {
    if (lastBackupTime != null) {
        val formatter = rememberDateTimeFormatter(FormatStyle.SHORT)
        val formattedTime = formatter.format(lastBackupTime)
        ThemedText(
            modifier = modifier,
            text = stringResource(R.string.last_backup_time_pattern, stringResource(R.string.android_backup_date), formattedTime),
            style = MaterialTheme.typography.bodyMedium,
            color = colorResource(R.color.status_foreground_success),
        )
    } else {
        ThemedText(
            modifier = modifier,
            text = stringResource(R.string.no_backup_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = colorResource(R.color.brand_grey_400),
        )
    }
}

@PreviewThreemaAll
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Screen() {
    BackupCenterScreenContent(
        state = BackupCenterViewState(
            infoBannerVisible = true,
            threemaSafeVisible = true,
            threemaSafeEnabled = true,
            lastBackupTime = INSTANT_1,
        ),
        onClickBack = {},
        onClickDismissInfoBanner = {},
        onClickThreemaSafe = {},
        onClickDataBackup = {},
        onClickLearnMoreThreemaSafe = {},
        onClickLearnMoreDataBackup = {},
    )
}
