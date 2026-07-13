package ch.threema.app.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import ch.threema.android.buildActivityIntent
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity.Companion.extractConversationId
import ch.threema.app.activities.DistributionListAddActivity
import ch.threema.app.activities.GroupDetailActivity
import ch.threema.app.activities.ThreemaComposeActivity
import ch.threema.app.applock.CheckAppLockContract
import ch.threema.app.compose.common.BlockScreenCapture
import ch.threema.app.compose.common.VerificationLevelDisplay
import ch.threema.app.compose.common.appbars.AppBar
import ch.threema.app.compose.common.appbars.NavigationIcon
import ch.threema.app.compose.common.avatar.AvatarAsync
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.spacer.SpacerHorizontal
import ch.threema.app.compose.common.spacer.SpacerVertical
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.compose.theme.dimens.isTabletLayout
import ch.threema.app.compose.theme.dimens.responsive
import ch.threema.app.contactdetails.ContactDetailActivity
import ch.threema.app.conversation.wallpaper.ConversationWallpaper
import ch.threema.app.conversations.ConversationsFragment
import ch.threema.app.fragments.composemessage.ComposeMessageFragment
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.app.usecases.groups.GroupDisplayName
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.group.GroupMessageModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

private val logger = getThreemaLogger("ConversationActivity")

class ConversationActivity : ThreemaComposeActivity() {

    private val viewModel: ConversationViewModel by viewModel {
        val conversationId: ConversationId? = intent?.extractConversationId()
        if (conversationId == null) {
            logger.error("Intent is missing a conversation-id")
            finish()
        }
        val hasInitialFocus: Boolean = intent
            ?.getBooleanExtra(AppConstants.INTENT_DATA_EDITFOCUS, false)
            ?: false
        val initialText: String = intent
            ?.getStringExtra(AppConstants.INTENT_DATA_TEXT)
            ?: ""
        parametersOf(conversationId, hasInitialFocus, initialText)
    }

    private val checkAppLockLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            viewModel.onAppLockUnlocked()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val conversationId: ConversationId? = intent.extractConversationId()
        if (conversationId == null) {
            logger.error("New intent is missing a conversation-id")
            return
        }
        val hasInitialFocus: Boolean = intent.getBooleanExtra(AppConstants.INTENT_DATA_EDITFOCUS, false)
        val initialText: String = intent.getStringExtra(AppConstants.INTENT_DATA_TEXT) ?: ""

        setIntent(intent)
        viewModel.switchConversation(
            conversationId = conversationId,
            hasInitialFocus = hasInitialFocus,
            initialText = initialText,
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPause()
    }

    private fun setContent() {
        setContent {
            EventHandler(viewModel, ::handleEvent)
            ThreemaTheme {
                WithViewState(viewModel) { state ->
                    if (state != null) {
                        BlockScreenCapture(enabled = state.secureConversationState is SecureConversationState.Private)

                        if (state.secureConversationState.showConversationContent) {
                            ConversationScreenContent(
                                onClickBack = {
                                    finish()
                                },
                                avatarBitmapProvider = viewModel::provideAvatarBitmap,
                                state = state,
                                onClickHeaderContent = {
                                    onClickConversationHeader(
                                        conversationId = state.conversationId,
                                    )
                                },
                                onTextChange = viewModel::onTextChange,
                            )
                        } else if (state.secureConversationState.showMissingAppLockContent) {
                            MissingAppLockContent(
                                onDismissRequest = {
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleEvent(event: ConversationScreenEvent) {
        when (event) {
            is ConversationScreenEvent.CheckAppLock -> checkAppLockLauncher.launch()
        }
    }

    private fun onClickConversationHeader(conversationId: ConversationId) {
        val intent = when (conversationId) {
            is ContactConversationId -> {
                logger.info("Clicked title of contact chat")
                ContactDetailActivity.createIntent(
                    /* context = */
                    this,
                    /* identity = */
                    conversationId.identity,
                    /* readOnly = */
                    true,
                )
            }
            is GroupConversationId -> {
                logger.info("Clicked title of group chat")
                GroupDetailActivity.createIntent(
                    /* context = */
                    this,
                    /* groupDatabaseId = */
                    conversationId.groupDatabaseId,
                )
            }
            is DistributionListConversationId -> {
                logger.info("Clicked title of distribution list")
                DistributionListAddActivity.createIntent(
                    context = this,
                    distributionListId = conversationId.distributionListId,
                )
            }
        }
        startActivity(intent)
    }

    companion object {

        @JvmOverloads
        @JvmStatic
        fun createIntent(
            context: Context,
            conversationId: ConversationId,
            initialText: String? = null,
            hasInitialFocus: Boolean? = null,
        ) = buildActivityIntent<ConversationActivity>(context) {
            putExtra(AppConstants.INTENT_DATA_CONVERSATION_ID, conversationId)
            if (initialText != null) {
                putExtra(AppConstants.INTENT_DATA_TEXT, initialText)
            }
            if (hasInitialFocus != null) {
                putExtra(AppConstants.INTENT_DATA_EDITFOCUS, hasInitialFocus)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun createIntentJumpToMessage(
            context: Context,
            message: AbstractMessageModel,
            overrideBackToHomeBehavior: Boolean? = null,
        ) = buildActivityIntent<ConversationActivity>(context) {
            val conversationId: ConversationId =
                when (message) {
                    is GroupMessageModel -> {
                        GroupConversationId(
                            groupDatabaseId = message.groupId.toLong(),
                        )
                    }
                    is DistributionListMessageModel -> {
                        DistributionListConversationId(
                            distributionListId = message.distributionListId,
                        )
                    }
                    else -> {
                        val identity = message.identity
                        requireNotNull(identity) { "Message is missing identity" }
                        ContactConversationId(identity)
                    }
                }
            putExtra(AppConstants.INTENT_DATA_CONVERSATION_ID, conversationId)
            putExtra(ComposeMessageFragment.EXTRA_API_MESSAGE_ID, message.apiMessageId)
            putExtra(ComposeMessageFragment.EXTRA_SEARCH_QUERY, " ")
            if (overrideBackToHomeBehavior != null) {
                putExtra(ComposeMessageFragment.EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR, overrideBackToHomeBehavior)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreenContent(
    onClickBack: () -> Unit,
    state: ConversationScreenState,
    avatarBitmapProvider: suspend (ConversationId) -> ImmutableBitmap?,
    onClickHeaderContent: () -> Unit,
    onTextChange: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppBar(
                navigationIcon = NavigationIcon.back(
                    onClick = onClickBack,
                ),
                content = {
                    Row(
                        modifier = Modifier
                            .clip(
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable(
                                onClick = onClickHeaderContent,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isTabletLayout()) {
                            SpacerHorizontal(GridUnit.x1)
                        }
                        if (state.shouldShowAvatar) {
                            AvatarAsync(
                                conversationId = state.conversationId,
                                bitmapProvider = avatarBitmapProvider,
                                contentDescription = stringResource(
                                    id = when (state.receiverState) {
                                        is ConversationReceiverState.Contact -> R.string.prefs_header_chat
                                        is ConversationReceiverState.Group -> R.string.distribution_list
                                        is ConversationReceiverState.DistributionList -> R.string.prefs_group_notifications
                                        is ConversationReceiverState.Unknown -> R.string.unknown
                                    },
                                ),
                                fallbackIcon = when (state.receiverState) {
                                    is ConversationReceiverState.Contact -> R.drawable.ic_contact
                                    is ConversationReceiverState.Group -> R.drawable.ic_group
                                    is ConversationReceiverState.DistributionList -> R.drawable.ic_distribution_list_avatar
                                    is ConversationReceiverState.Unknown -> R.drawable.ic_contact
                                },
                                showIdentityTypeBadge = when (state.receiverState) {
                                    is ConversationReceiverState.Contact -> state.receiverState.showIdentityTypeBadge
                                    is ConversationReceiverState.Group -> false
                                    is ConversationReceiverState.DistributionList -> false
                                    is ConversationReceiverState.Unknown -> false
                                },
                                availabilityStatus = when (state.receiverState) {
                                    is ConversationReceiverState.Contact -> state.receiverState.availabilityStatus
                                    is ConversationReceiverState.DistributionList -> null
                                    is ConversationReceiverState.Group -> null
                                    is ConversationReceiverState.Unknown -> null
                                },
                                avatarIteration = state.receiverState.avatarIteration,
                            )
                            SpacerHorizontal(GridUnit.x1.responsive)
                        }
                        AppBarContent(state.receiverState)
                    }
                },
                actions = {
                    if (state.receiverState is ConversationReceiverState.Unknown) {
                        return@AppBar
                    }

                    // TODO(ANDR-4609): Show and hide based on user call preference
                    if (
                        state.receiverState !is ConversationReceiverState.DistributionList &&
                        !(state.receiverState is ConversationReceiverState.Group && !state.receiverState.userIsMember)
                    ) {
                        IconButton(
                            onClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked_outline),
                                contentDescription = stringResource(R.string.threema_call),
                            )
                        }
                    }

                    // TODO(ANDR-4604): Implement different mute states
                    if (
                        state.receiverState is ConversationReceiverState.Contact ||
                        state.receiverState is ConversationReceiverState.Group
                    ) {
                        IconButton(
                            onClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_notifications_active_outline),
                                contentDescription = stringResource(R.string.notifications_settings),
                            )
                        }
                    }

                    IconButton(
                        onClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more),
                            contentDescription = stringResource(R.string.cd_menu_more),
                        )
                    }
                },
            )
        },
    ) { insetsPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = insetsPadding.calculateTopPadding(),
                ),
        ) {
            if (isTabletLayout()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AndroidFragment<ConversationsFragment>(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(dimensionResource(R.dimen.message_fragment_width)),
                        arguments = Bundle().apply {
                            putParcelable(AppConstants.INTENT_DATA_CONVERSATION_ID, state.conversationId)
                        },
                    )
                    VerticalDivider()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        ConversationScreenBodyContent(
                            state = state,
                            insetsPadding = insetsPadding,
                            onTextChange = onTextChange,
                        )
                    }
                }
            } else {
                ConversationScreenBodyContent(
                    state = state,
                    insetsPadding = insetsPadding,
                    onTextChange = onTextChange,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ConversationScreenBodyContent(
    state: ConversationScreenState,
    insetsPadding: PaddingValues,
    onTextChange: (String) -> Unit,
) {
    ConversationWallpaper(
        modifier = Modifier.fillMaxSize(),
        conversationId = state.conversationId,
    )
    if (state.receiverState !is ConversationReceiverState.Unknown) {
        MessageInput(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            insetsPadding = insetsPadding,
            hasInitialFocus = state.hasInitialFocus,
            text = state.text,
            onTextChange = onTextChange,
        )
    }
}

@Composable
private fun MessageInput(
    modifier: Modifier = Modifier,
    insetsPadding: PaddingValues,
    hasInitialFocus: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = text,
                selection = TextRange(text.length),
            ),
        )
    }

    LaunchedEffect(text) {
        if (text != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = text)
        }
    }

    LaunchedEffect(Unit) {
        if (hasInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val insetsPaddingStart =
        if (isTabletLayout()) {
            0.dp
        } else {
            insetsPadding.calculateStartPadding(layoutDirection)
        }

    val insetsPaddingEnd = insetsPadding.calculateEndPadding(layoutDirection)

    Row(
        modifier = modifier
            .imePadding()
            .padding(
                start = insetsPaddingStart,
                bottom = insetsPadding.calculateBottomPadding(),
                end = insetsPaddingEnd,
            )
            .padding(
                horizontal = GridUnit.x1,
                vertical = GridUnit.x0_5,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = stringResource(R.string.compose_message_and_enter),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send,
            ),
            value = textFieldValue,
            onValueChange = { value ->
                textFieldValue = value
                onTextChange(value.text)
            },
            colors = OutlinedTextFieldDefaults.colors().copy(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(50),
            prefix = {
                IconButton(
                    modifier = Modifier.size(32.dp),
                    onClick = {},
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_emoji),
                        contentDescription = stringResource(R.string.select_emoji),
                    )
                }
            },
            suffix = {
                Row(
                    modifier = Modifier.wrapContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GridUnit.x1),
                ) {
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = {},
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = stringResource(R.string.take_photo),
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = {},
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_attachment),
                            contentDescription = stringResource(R.string.add_attachment),
                        )
                    }
                }
            },
        )

        SpacerHorizontal(GridUnit.x1)

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(
                    shape = RoundedCornerShape(50),
                )
                .background(
                    color = MaterialTheme.colorScheme.primary,
                )
                .clickable(
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier,
                painter = painterResource(R.drawable.ic_send),
                contentDescription = stringResource(R.string.send),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun AppBarContent(receiverState: ConversationReceiverState) {
    Column(
        modifier = Modifier
            .padding(
                all = GridUnit.x0_5,
            ),
    ) {
        when (receiverState) {
            is ConversationReceiverState.Contact -> AppBarContentContactConversation(receiverState)
            is ConversationReceiverState.Group -> AppBarContentGroupConversation(receiverState)
            is ConversationReceiverState.DistributionList -> AppBarContentDistributionListConversation(receiverState)
            is ConversationReceiverState.Unknown -> AppBarContentUnknownConversation()
        }
    }
}

@Composable
private fun AppBarContentContactConversation(
    state: ConversationReceiverState.Contact,
) {
    ConversationText(
        rawInput = state.displayName,
        textStyle = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        markupEnabled = false,
    )
    SpacerVertical(GridUnit.x0_25)
    VerificationLevelDisplay(
        modifier = Modifier.sizeIn(
            maxHeight = 8.dp,
        ),
        verificationLevel = state.verificationLevel,
        workVerificationLevel = state.workVerificationLevel,
    )
    SpacerVertical(GridUnit.x0_5)
}

@Composable
private fun AppBarContentGroupConversation(
    state: ConversationReceiverState.Group,
) {
    ConversationText(
        rawInput = state.displayName.resolve(LocalContext.current),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            textDecoration = if (state.userIsMember) TextDecoration.None else TextDecoration.LineThrough,
        ),
        maxLines = 1,
        markupEnabled = false,
    )
    SpacerVertical(GridUnit.x0_25)
    ConversationText(
        rawInput = state.members,
        textStyle = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        markupEnabled = false,
    )
}

@Composable
private fun AppBarContentDistributionListConversation(
    state: ConversationReceiverState.DistributionList,
) {
    ConversationText(
        rawInput = if (state.isAdHoc) {
            stringResource(R.string.threema_message_to, "")
        } else {
            state.displayName
        },
        textStyle = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        markupEnabled = false,
    )
    SpacerVertical(GridUnit.x0_25)
    ConversationText(
        rawInput = state.members,
        textStyle = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        markupEnabled = false,
    )
}

@Composable
private fun AppBarContentUnknownConversation() {
    ThemedText(
        text = stringResource(R.string.unknown),
        style = MaterialTheme.typography.titleLarge,
        color = LocalContentColor.current,
        maxLines = 1,
    )
}

@Composable
private fun MissingAppLockContent(
    onDismissRequest: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { insetsPadding ->
        AlertDialog(
            modifier = Modifier
                .padding(insetsPadding),
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(R.string.hide_chat),
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.hide_chat_enter_message_explain),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.ok),
                    )
                }
            },
        )
    }
}

@Composable
@PreviewLightDark
private fun Preview_ConversationScreenContent_Contact() {
    ThreemaThemePreview {
        ConversationScreenContent(
            onClickBack = {},
            state = ConversationScreenState(
                conversationId = ContactConversationId(
                    identity = PreviewData.IDENTITY_OTHER_1.value,
                ),
                hasInitialFocus = false,
                text = "",
                secureConversationState = SecureConversationState.NotPrivate,
                receiverState = ConversationReceiverState.Contact(
                    showIdentityTypeBadge = false,
                    displayName = "Contact Name",
                    verificationLevel = VerificationLevel.FULLY_VERIFIED,
                    workVerificationLevel = WorkVerificationLevel.NONE,
                    avatarIteration = AvatarIteration.initial,
                    availabilityStatus = AvailabilityStatus.Busy(),
                ),
            ),
            avatarBitmapProvider = { null },
            onClickHeaderContent = {},
            onTextChange = {},
        )
    }
}

@Composable
@Preview
private fun Preview_ConversationScreenContent_Group() {
    ThreemaThemePreview {
        ConversationScreenContent(
            onClickBack = {},
            state = ConversationScreenState(
                conversationId = GroupConversationId(
                    groupDatabaseId = 1L,
                ),
                hasInitialFocus = false,
                text = "",
                receiverState = ConversationReceiverState.Group(
                    userIsMember = true,
                    displayName = GroupDisplayName.Defined("Group Name"),
                    members = "Member1, Member2, Member3",
                    avatarIteration = AvatarIteration.initial,
                ),
                secureConversationState = SecureConversationState.NotPrivate,
            ),
            avatarBitmapProvider = { null },
            onClickHeaderContent = {},
            onTextChange = {},
        )
    }
}

@Composable
@Preview
private fun Preview_ConversationScreenContent_DistributionList() {
    ThreemaThemePreview {
        ConversationScreenContent(
            onClickBack = {},
            state = ConversationScreenState(
                conversationId = GroupConversationId(
                    groupDatabaseId = 1L,
                ),
                hasInitialFocus = false,
                text = "",
                receiverState = ConversationReceiverState.DistributionList(
                    displayName = "Distribution List",
                    members = "Member1, Member2, Member3",
                    isAdHoc = false,
                ),
                secureConversationState = SecureConversationState.NotPrivate,
            ),
            avatarBitmapProvider = { null },
            onClickHeaderContent = {},
            onTextChange = {},
        )
    }
}

@Composable
@Preview
private fun Preview_ConversationScreenContent_DistributionList_AdHoc() {
    ThreemaThemePreview {
        ConversationScreenContent(
            onClickBack = {},
            state = ConversationScreenState(
                conversationId = GroupConversationId(
                    groupDatabaseId = 1L,
                ),
                hasInitialFocus = false,
                text = "",
                receiverState = ConversationReceiverState.DistributionList(
                    displayName = "Ad Hoc",
                    members = "Member1, Member2, Member3",
                    isAdHoc = true,
                ),
                secureConversationState = SecureConversationState.NotPrivate,
            ),
            avatarBitmapProvider = { null },
            onClickHeaderContent = {},
            onTextChange = {},
        )
    }
}

@Composable
@Preview
private fun Preview_ConversationScreenContent_UnknownReceiver() {
    ThreemaThemePreview {
        ConversationScreenContent(
            onClickBack = {},
            state = ConversationScreenState(
                conversationId = ContactConversationId(
                    identity = "*UNKNOWN",
                ),
                hasInitialFocus = false,
                text = "",
                receiverState = ConversationReceiverState.Unknown,
                secureConversationState = SecureConversationState.NotPrivate,
            ),
            avatarBitmapProvider = { null },
            onClickHeaderContent = {},
            onTextChange = {},
        )
    }
}

@Composable
@Preview
private fun Preview_MissingAppLockContent() {
    ThreemaThemePreview {
        MissingAppLockContent(
            onDismissRequest = {},
        )
    }
}
