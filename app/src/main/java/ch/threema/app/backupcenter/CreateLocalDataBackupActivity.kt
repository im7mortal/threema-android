package ch.threema.app.backupcenter

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.TextAutoSizeDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import ch.threema.android.buildActivityIntent
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.activities.ThreemaComposeActivity
import ch.threema.app.backupcenter.CreateLocalDataBackupViewState.Step3InProgress.Status
import ch.threema.app.backupcenter.models.BackupPassword
import ch.threema.app.compose.common.BlockScreenCapture
import ch.threema.app.compose.common.ScreenContentContainer
import ch.threema.app.compose.common.appbars.AppBar
import ch.threema.app.compose.common.appbars.NavigationIcon
import ch.threema.app.compose.common.banner.InfoBanner
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.buttons.TextButtonPrimary
import ch.threema.app.compose.common.buttons.primary.ButtonPrimaryRounded
import ch.threema.app.compose.common.loading.FullScreenLoadingIndicator
import ch.threema.app.compose.common.settings.ThemedSwitch
import ch.threema.app.compose.common.spacer.SpacerRemainingVertical
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.surface.RoundedCornerSurface
import ch.threema.app.compose.common.text.SectionLabel
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.restrictions.AppRestrictions
import kotlin.getValue
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class CreateLocalDataBackupActivity : ThreemaComposeActivity() {

    private val appRestrictions: AppRestrictions by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        if (!BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED || appRestrictions.isBackupsDisabled() || appRestrictions.isDataBackupsDisabled()) {
            finish()
            return
        }

        setContent {
            val viewModel = koinViewModel<CreateLocalDataBackupViewModel>()
            EventHandler(viewModel, ::handleViewModelEvent)

            ThreemaTheme {
                CreateLocalDataBackupScreen(
                    viewModel,
                )
            }
        }
    }

    private fun handleViewModelEvent(event: CreateLocalDataBackupViewModelEvent) {
        when (event) {
            CreateLocalDataBackupViewModelEvent.Finish -> finish()
        }
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<CreateLocalDataBackupActivity>(context)
    }
}

@Composable
private fun CreateLocalDataBackupScreen(
    viewModel: CreateLocalDataBackupViewModel,
) {
    BackHandler(onBack = viewModel::onClickBack)
    WithViewState(viewModel) { state ->
        BlockScreenCapture(enabled = state?.step?.isSensitive == true)
        CreateLocalDataBackupScreenContent(
            state = state,
            onClickBack = viewModel::onClickBack,
            onClickCopy = viewModel::onClickCopy,
            onChangeKeyConfirmationInput = viewModel::onChangeKeyConfirmationInput,
            onChangeIncludeMedia = viewModel::onChangeIncludeMedia,
            onClickProceed = viewModel::onClickProceed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLocalDataBackupScreenContent(
    state: CreateLocalDataBackupViewState?,
    onClickBack: () -> Unit,
    onClickCopy: () -> Unit,
    onChangeKeyConfirmationInput: (String) -> Unit,
    onChangeIncludeMedia: (Boolean) -> Unit,
    onClickProceed: () -> Unit,
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
            val colors = TopAppBarDefaults.topAppBarColors()
            AppBar(
                colors = colors.copy(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                navigationIcon = NavigationIcon.back(
                    containerColor = colors.containerColor,
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
            scrollState = rememberStateSpecificScrollState(state),
            modifier = Modifier.padding(bottom = GridUnit.x2),
        ) {
            when (state.step) {
                is CreateLocalDataBackupViewState.Step1ConfirmKey,
                is CreateLocalDataBackupViewState.Step1RecoveryKey,
                -> {
                    StepHeader(
                        iconRes = R.drawable.ic_key_filled,
                        iconModifier = Modifier.rotate(112f),
                        stepNumber = 1,
                        title = stringResource(R.string.local_data_backup_confirm_your_key_title),
                    )
                }
                is CreateLocalDataBackupViewState.Step2 -> {
                    StepHeader(
                        iconRes = R.drawable.ic_settings_filled,
                        stepNumber = 2,
                        title = stringResource(R.string.local_data_backup_backup_settings_title),
                    )
                }
                is CreateLocalDataBackupViewState.Step3InProgress -> {
                    StepHeader(
                        iconRes = R.drawable.ic_download_for_offline,
                        backgroundColor = colorResource(R.color.status_foreground_info),
                        stepNumber = 3,
                        title = stringResource(R.string.local_data_backup_creating_backup_title),
                    )
                }
                is CreateLocalDataBackupViewState.Step3Done -> {
                    StepHeader(
                        iconRes = R.drawable.ic_check_circle,
                        backgroundColor = colorResource(R.color.status_foreground_success),
                        stepNumber = 3,
                        title = stringResource(R.string.local_data_backup_backup_complete_title),
                    )
                }
            }

            SpacerVertical(GridUnit.x2)

            when (val step = state.step) {
                is CreateLocalDataBackupViewState.Step1RecoveryKey -> Step1RecoveryKeyContent(
                    state = step,
                    onClickCopy = onClickCopy,
                    onClickProceed = onClickProceed,
                )
                is CreateLocalDataBackupViewState.Step1ConfirmKey -> Step1ConfirmKeyContent(
                    state = step,
                    onChangeInput = onChangeKeyConfirmationInput,
                    onClickProceed = onClickProceed,
                )
                is CreateLocalDataBackupViewState.Step2 -> Step2Content(
                    state = step,
                    onChangeIncludeMedia = onChangeIncludeMedia,
                    onClickProceed = onClickProceed,
                )
                is CreateLocalDataBackupViewState.Step3InProgress -> Step3InProgressContent(
                    state = step,
                    onClickCancel = onClickBack,
                )
                is CreateLocalDataBackupViewState.Step3Done -> Step3DoneContent(
                    state = step,
                    onClickProceed = onClickProceed,
                )
            }
        }
    }
}

@Composable
private fun rememberStateSpecificScrollState(state: CreateLocalDataBackupViewState?): ScrollState {
    val stepKey = when (state?.step) {
        is CreateLocalDataBackupViewState.Step1ConfirmKey -> 1
        is CreateLocalDataBackupViewState.Step1RecoveryKey -> 2
        is CreateLocalDataBackupViewState.Step2 -> 3
        is CreateLocalDataBackupViewState.Step3Done,
        is CreateLocalDataBackupViewState.Step3InProgress,
        -> 4
        null -> 0
    }
    return rememberSaveable(stepKey, saver = ScrollState.Saver) {
        ScrollState(initial = 0)
    }
}

@Composable
private fun ColumnScope.Step1RecoveryKeyContent(
    state: CreateLocalDataBackupViewState.Step1RecoveryKey,
    onClickCopy: () -> Unit,
    onClickProceed: () -> Unit,
) {
    val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)

    Headline(
        modifier = horizontalPaddingModifier,
        headline = stringResource(R.string.local_data_backup_password_headline),
    )

    SpacerVertical(GridUnit.x2)

    PasswordBox(
        modifier = horizontalPaddingModifier,
        password = state.password,
    )

    SpacerVertical(GridUnit.x2)

    ButtonOutlined(
        modifier = horizontalPaddingModifier.fillMaxWidth(),
        text = stringResource(R.string.copy_message_action),
        leadingIcon = ButtonIconInfo(
            iconRes = R.drawable.ic_content_copy_outline,
        ),
        onClick = onClickCopy,
    )

    SpacerRemainingVertical(minHeight = GridUnit.x2)

    PasswordNotice(
        modifier = horizontalPaddingModifier.fillMaxWidth(),
    )

    SpacerVertical(GridUnit.x3)

    ButtonPrimaryRounded(
        modifier = horizontalPaddingModifier
            .fillMaxWidth(),
        onClick = onClickProceed,
        text = stringResource(R.string.local_data_backup_confirm_key_saved),
    )
}

@Composable
private fun Headline(
    modifier: Modifier,
    headline: String,
) {
    ThemedText(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        text = headline,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepHeader(
    @DrawableRes iconRes: Int,
    iconModifier: Modifier = Modifier,
    backgroundColor: Color = colorResource(R.color.status_foreground_info),
    stepNumber: Int,
    title: String,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = GridUnit.x1)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier
                .background(color = backgroundColor, shape = CircleShape)
                .padding(12.dp)
                .size(24.dp)
                .then(iconModifier),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colorResource(R.color.on_status_foreground),
        )

        SpacerVertical(GridUnit.x1_5)

        ThemedText(
            text = stringResource(R.string.local_data_backup_step_title, stepNumber, 3).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = colorResource(R.color.brand_grey_500),
        )

        SpacerVertical(GridUnit.x1_5)

        ThemedText(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PasswordBox(
    modifier: Modifier,
    password: BackupPassword,
) {
    val windowWidth = LocalWindowInfo.current.containerDpSize.width
    RoundedCornerSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.padding(GridUnit.x2),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(GridUnit.x1),
            maxItemsInEachRow = when {
                windowWidth > 640.dp -> 8
                windowWidth > 320.dp -> 4
                else -> 2
            },
        ) {
            val modifier = Modifier
                .weight(1f)
                .padding(horizontal = GridUnit.x0_5)
            password.chunks.forEach { chunk ->
                PasswordChunk(
                    modifier = modifier,
                    chunk = chunk,
                )
            }
        }
    }
}

@Composable
private fun PasswordChunk(
    modifier: Modifier,
    chunk: String,
) {
    val brandColor = colorResource(R.color.brand_700)
    val localFontSize = LocalTextStyle.current.fontSize
    val maxFontSize = if (localFontSize >= TextAutoSizeDefaults.MinFontSize) {
        localFontSize
    } else {
        TextAutoSizeDefaults.MinFontSize
    }
    Text(
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            maxFontSize = maxFontSize,
        ),
        text = buildAnnotatedString {
            chunk.forEach { char ->
                if (char.isDigit()) {
                    withStyle(SpanStyle(color = brandColor)) {
                        append(char)
                    }
                } else {
                    append(char)
                }
            }
        },
    )
}

@Composable
private fun PasswordNotice(modifier: Modifier) {
    InfoBanner(
        modifier = modifier,
        leadingContent = {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_info_rounded),
                contentDescription = null,
            )
        },
    ) {
        // TODO(ANDR-4877): Part of this text needs to be in bold. We don't have a mechanism yet to handle formatted resource strings in Compose
        Text(
            text = stringResource(R.string.local_data_backup_password_info_banner),
        )
    }
}

@Composable
private fun ColumnScope.Step1ConfirmKeyContent(
    state: CreateLocalDataBackupViewState.Step1ConfirmKey,
    onChangeInput: (String) -> Unit,
    onClickProceed: () -> Unit,
) {
    val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)

    Headline(
        modifier = horizontalPaddingModifier,
        headline = stringResource(R.string.local_data_backup_confirm_password_headline),
    )

    SpacerVertical(GridUnit.x2)

    KeyConfirmationBox(
        modifier = horizontalPaddingModifier,
        text = state.input,
        isMatch = state.isMatch,
        onSubmit = {
            if (state.isMatch) {
                onClickProceed()
            }
        },
        onChangeText = onChangeInput,
    )

    SpacerRemainingVertical(minHeight = GridUnit.x3)

    ButtonPrimaryRounded(
        modifier = horizontalPaddingModifier
            .fillMaxWidth(),
        onClick = onClickProceed,
        enabled = state.isMatch,
        text = stringResource(R.string.label_continue),
    )
}

@Composable
private fun KeyConfirmationBox(
    modifier: Modifier,
    text: String,
    isMatch: Boolean,
    onSubmit: () -> Unit,
    onChangeText: (String) -> Unit,
) {
    RoundedCornerSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(GridUnit.x2),
        ) {
            ThemedText(
                text = stringResource(R.string.local_data_backup_recovery_key_confirm_label),
                style = MaterialTheme.typography.bodyLarge,
                color = colorResource(R.color.brand_grey_500),
            )

            SpacerVertical(GridUnit.x1_5)

            Box {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.local_data_backup_confirm_password_placeholder),
                        style = TextStyle.Default.copy(
                            color = colorResource(R.color.brand_grey_400),
                        ),
                    )
                }
                BasicTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = { value ->
                        value.uppercase()
                            .filter(BackupPassword::isValidPasswordCharacter)
                            .let(onChangeText)
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onSubmit() },
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    textStyle = TextStyle.Default.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }

            AnimatedVisibility(isMatch) {
                Column {
                    SpacerVertical(GridUnit.x1)
                    KeyMatchBadge()
                }
            }
        }
    }
}

@Composable
private fun KeyMatchBadge() {
    Row(
        modifier = Modifier
            .background(
                color = colorResource(R.color.status_foreground_success),
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = GridUnit.x1, vertical = GridUnit.x0_5),
        horizontalArrangement = Arrangement.spacedBy(GridUnit.x0_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = colorResource(R.color.on_status_foreground),
        )
        Text(
            text = stringResource(R.string.local_data_backup_confirm_password_matched),
            color = colorResource(R.color.on_status_foreground),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ColumnScope.Step2Content(
    state: CreateLocalDataBackupViewState.Step2,
    onChangeIncludeMedia: (Boolean) -> Unit,
    onClickProceed: () -> Unit,
) {
    val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)

    Headline(
        modifier = horizontalPaddingModifier,
        headline = stringResource(R.string.local_data_backup_backup_settings_headline),
    )

    SpacerVertical(GridUnit.x4)

    SectionLabel(
        modifier = horizontalPaddingModifier,
        label = stringResource(R.string.local_data_backup_always_included_label),
    )

    SpacerVertical(GridUnit.x2)

    RoundedCornerSurface(
        modifier = horizontalPaddingModifier.fillMaxWidth(),
    ) {
        DataTypeRow(
            modifier = Modifier.padding(
                horizontal = GridUnit.x2,
                vertical = GridUnit.x1,
            ),
            iconRes = R.drawable.ic_chat_bubble_filled,
            title = stringResource(R.string.local_data_backup_data_type_messages_title),
            subtitle = stringResource(R.string.local_data_backup_data_type_messages_subtitle),
        )
    }

    SpacerVertical(GridUnit.x4)

    SectionLabel(
        modifier = horizontalPaddingModifier,
        label = stringResource(R.string.local_data_backup_media_label),
    )

    SpacerVertical(GridUnit.x2)

    RoundedCornerSurface(
        modifier = horizontalPaddingModifier.fillMaxWidth(),
    ) {
        val innerPaddingModifier = Modifier.padding(horizontal = GridUnit.x2)
        Column {
            LabeledSwitch(
                modifier = innerPaddingModifier
                    .padding(vertical = GridUnit.x1),
                title = stringResource(R.string.local_data_backup_include_media_switch_title),
                subtitle = stringResource(R.string.local_data_backup_include_media_switch_subtitle),
                checked = state.includeMedia,
                onChangeChecked = onChangeIncludeMedia,
            )

            HorizontalDivider(
                modifier = innerPaddingModifier.alpha(0.1f),
                color = colorResource(R.color.brand_grey_500),
            )

            SpacerVertical(GridUnit.x0_5)

            DataTypeRow(
                modifier = innerPaddingModifier,
                iconRes = R.drawable.ic_photo_library,
                title = stringResource(R.string.local_data_backup_data_type_photos_title),
                subtitle = stringResource(R.string.local_data_backup_data_type_photos_subtitle),
                enabled = state.includeMedia,
            )
            DataTypeRow(
                modifier = innerPaddingModifier,
                iconRes = R.drawable.ic_videocam,
                title = stringResource(R.string.local_data_backup_data_type_videos_title),
                subtitle = stringResource(R.string.local_data_backup_data_type_videos_subtitle),
                enabled = state.includeMedia,
            )
            DataTypeRow(
                modifier = innerPaddingModifier,
                iconRes = R.drawable.ic_file_filled,
                title = stringResource(R.string.local_data_backup_data_type_documents_title),
                subtitle = stringResource(R.string.local_data_backup_data_type_documents_subtitle),
                enabled = state.includeMedia,
            )
            DataTypeRow(
                modifier = innerPaddingModifier,
                iconRes = R.drawable.ic_microphone,
                title = stringResource(R.string.local_data_backup_data_type_voice_messages_title),
                subtitle = stringResource(R.string.local_data_backup_data_type_voice_messages_subtitle),
                enabled = state.includeMedia,
            )

            SpacerVertical(GridUnit.x1)
        }
    }

    SpacerVertical(GridUnit.x1)

    ThemedText(
        modifier = horizontalPaddingModifier,
        text = stringResource(R.string.local_data_backup_media_increase_size_info),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SpacerRemainingVertical(minHeight = GridUnit.x3)

    ButtonPrimaryRounded(
        modifier = horizontalPaddingModifier
            .fillMaxWidth(),
        onClick = onClickProceed,
        text = stringResource(R.string.local_data_backup_start_backup_button),
    )
}

@Composable
private fun LabeledSwitch(
    modifier: Modifier,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChangeChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .toggleable(
                value = checked,
                onValueChange = onChangeChecked,
                role = Role.Switch,
            )
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            ThemedText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            ThemedText(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.brand_grey_500),
            )
        }
        ThemedSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun DataTypeRow(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
) {
    val iconSize = 20.dp
    val alpha by animateFloatAsState(if (enabled) 1f else 0.6f)
    Column(
        modifier = Modifier
            .padding(vertical = GridUnit.x1)
            .alpha(alpha)
            .then(modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GridUnit.x1),
        ) {
            Icon(
                modifier = Modifier.size(iconSize),
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            ThemedText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        ThemedText(
            Modifier.padding(start = iconSize + GridUnit.x1),
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colorResource(R.color.brand_grey_500),
        )
    }
}

@Composable
private fun ColumnScope.Step3InProgressContent(
    state: CreateLocalDataBackupViewState.Step3InProgress,
    onClickCancel: () -> Unit,
) {
    val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)

    Headline(
        modifier = horizontalPaddingModifier,
        headline = stringResource(R.string.local_data_backup_creating_backup_headline),
    )

    SpacerVertical(GridUnit.x2)

    ProgressBox(
        modifier = horizontalPaddingModifier,
        messagesStatus = state.messagesStatus,
        mediaFilesStatus = state.mediaFilesStatus,
        encryptionStatus = state.encryptionStatus,
        finalizingStatus = state.finalizingStatus,
    )

    SpacerVertical(GridUnit.x1)

    ThemedText(
        modifier = horizontalPaddingModifier,
        text = stringResource(R.string.local_data_backup_do_not_close_app_info),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SpacerRemainingVertical(minHeight = GridUnit.x2)

    TextButtonPrimary(
        modifier = horizontalPaddingModifier
            .fillMaxWidth(),
        onClick = onClickCancel,
        text = stringResource(R.string.cancel),
    )
}

@Composable
private fun ProgressBox(
    modifier: Modifier,
    messagesStatus: Status,
    mediaFilesStatus: Status?,
    encryptionStatus: Status,
    finalizingStatus: Status,
) {
    RoundedCornerSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(GridUnit.x2),
            verticalArrangement = Arrangement.spacedBy(GridUnit.x1),
        ) {
            ProgressRow(
                title = stringResource(R.string.local_data_backup_exporting_messages_status),
                status = messagesStatus,
            )
            if (mediaFilesStatus != null) {
                ProgressRow(
                    title = stringResource(R.string.local_data_backup_preparing_media_files_status),
                    status = mediaFilesStatus,
                )
            }
            ProgressRow(
                title = stringResource(R.string.local_data_backup_encrypting_files_status),
                status = encryptionStatus,
            )
            ProgressRow(
                title = stringResource(R.string.local_data_backup_finalizing_backup_status),
                status = finalizingStatus,
            )
        }
    }
}

@Composable
private fun ProgressRow(
    title: String,
    status: Status,
) {
    val backgroundColor = when (status) {
        Status.WAITING -> colorResource(R.color.brand_grey_300)
        Status.IN_PROGRESS -> colorResource(R.color.status_foreground_info)
        Status.DONE -> colorResource(R.color.status_foreground_success)
        Status.FAILED -> colorResource(R.color.status_foreground_destructive)
    }
    val statusText = when (status) {
        Status.WAITING -> stringResource(R.string.local_data_backup_status_waiting)
        Status.IN_PROGRESS -> stringResource(R.string.local_data_backup_status_in_progress)
        Status.DONE -> stringResource(R.string.local_data_backup_status_done)
        Status.FAILED -> stringResource(R.string.local_data_backup_status_failed)
    }
    val statusTextColor = when (status) {
        Status.WAITING,
        Status.IN_PROGRESS,
        -> colorResource(R.color.brand_grey_400)
        Status.DONE -> MaterialTheme.colorScheme.onSurface
        Status.FAILED -> colorResource(R.color.status_foreground_destructive)
    }
    val iconRes = when (status) {
        Status.WAITING,
        Status.IN_PROGRESS,
        -> null
        Status.DONE -> R.drawable.ic_check
        Status.FAILED -> R.drawable.ic_close
    }
    val iconModifier = Modifier
        .background(color = backgroundColor, shape = CircleShape)
        .padding(6.dp)
        .size(22.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(GridUnit.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                modifier = iconModifier,
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colorResource(R.color.on_status_foreground),
            )
        } else {
            CircularProgressIndicator(
                modifier = iconModifier,
                strokeWidth = 2.dp,
                color = colorResource(R.color.on_status_foreground),
            )
        }
        Column {
            ThemedText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThemedText(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusTextColor,
            )
        }
    }
}

@Composable
private fun ColumnScope.Step3DoneContent(
    state: CreateLocalDataBackupViewState.Step3Done,
    onClickProceed: () -> Unit,
) {
    val horizontalPaddingModifier = Modifier.padding(horizontal = GridUnit.x3)

    Headline(
        modifier = horizontalPaddingModifier,
        headline = if (state.includeMedia) {
            stringResource(R.string.local_data_backup_backup_complete_including_media_headline)
        } else {
            stringResource(R.string.local_data_backup_backup_complete_headline)
        },
    )

    SpacerVertical(GridUnit.x2)

    ProgressBox(
        modifier = horizontalPaddingModifier,
        messagesStatus = Status.DONE,
        mediaFilesStatus = if (state.includeMedia) Status.DONE else null,
        encryptionStatus = Status.DONE,
        finalizingStatus = Status.DONE,
    )

    SpacerRemainingVertical(minHeight = GridUnit.x1)

    ButtonPrimaryRounded(
        modifier = horizontalPaddingModifier
            .fillMaxWidth(),
        onClick = onClickProceed,
        text = stringResource(R.string.menu_done),
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Step1RecoveryKey_Screen() {
    CreateLocalDataBackupScreenContent(
        state = CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step1RecoveryKey(
                password = BackupPassword("ABC1DEF2G4H5I67JKLMN890OPQRSTUVW".repeat(2)),
            ),
        ),
        onClickBack = {},
        onClickCopy = {},
        onChangeKeyConfirmationInput = {},
        onChangeIncludeMedia = {},
        onClickProceed = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Step1ConfirmKey_Screen() {
    CreateLocalDataBackupScreenContent(
        state = CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step1ConfirmKey(
                expectedInput = "A1B2",
            ),
        ),
        onClickBack = {},
        onClickCopy = {},
        onChangeKeyConfirmationInput = {},
        onChangeIncludeMedia = {},
        onClickProceed = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Step2IncludeMediaOn_Screen() {
    CreateLocalDataBackupScreenContent(
        state = CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step2(
                includeMedia = true,
            ),
        ),
        onClickBack = {},
        onClickCopy = {},
        onChangeKeyConfirmationInput = {},
        onChangeIncludeMedia = {},
        onClickProceed = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Step2IncludeMediaOff_Screen() {
    CreateLocalDataBackupScreenContent(
        state = CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step2(
                includeMedia = false,
            ),
        ),
        onClickBack = {},
        onClickCopy = {},
        onChangeKeyConfirmationInput = {},
        onChangeIncludeMedia = {},
        onClickProceed = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Step3InProgress_Screen() {
    CreateLocalDataBackupScreenContent(
        state = CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step3InProgress(
                messagesStatus = Status.DONE,
                mediaFilesStatus = Status.IN_PROGRESS,
                encryptionStatus = Status.WAITING,
                finalizingStatus = Status.FAILED,
            ),
        ),
        onClickBack = {},
        onClickCopy = {},
        onChangeKeyConfirmationInput = {},
        onChangeIncludeMedia = {},
        onClickProceed = {},
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun BackupCenterScreenContent_Step3Done_Screen() {
    CreateLocalDataBackupScreenContent(
        state = CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step3Done(
                includeMedia = true,
            ),
        ),
        onClickBack = {},
        onClickCopy = {},
        onChangeKeyConfirmationInput = {},
        onChangeIncludeMedia = {},
        onClickProceed = {},
    )
}
