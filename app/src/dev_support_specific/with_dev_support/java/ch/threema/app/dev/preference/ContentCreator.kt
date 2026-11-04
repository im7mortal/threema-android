package ch.threema.app.dev.preference

import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.domain.models.MessageId
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.Mention
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.group.GroupModelOld
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ContentCreator")

class ContentCreator(
    private val contactModelRepository: ContactModelRepository,
    private val groupService: GroupService,
    private val userService: UserService,
    private val contactMessageModelFactory: ContactMessageModelFactory,
    private val groupMessageModelFactory: GroupMessageModelFactory,
    private val emojiReactionsRepository: EmojiReactionsRepository,
    private val identityStore: IdentityStore,
    private val nonceFactory: NonceFactory,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun createTextMessageSpam(fragmentManager: FragmentManager) = withContext(dispatcherProvider.worker) {
        val goOn = confirm(
            fragmentManager,
            "Create $SPAM_TEXT_MESSAGES_PER_CONVERSATION messages in any contact/group whose name starts with '$SPAM_CHATS_PREFIX'?",
        )
        if (!goOn) {
            return@withContext
        }
        withGenericProgress(fragmentManager, "Creating message spam...") {
            createContactTextSpam(getSpamIdentities())
            createGroupTextSpam(getSpamGroupModels())
        }
    }

    suspend fun createReactionSpam(fragmentManager: FragmentManager) = withContext(dispatcherProvider.worker) {
        val goOn = confirm(
            fragmentManager,
            "Create loads of messages with reactions and/or ACK/DEC for any contact/group whose name starts with '$SPAM_CHATS_PREFIX'?",
        )
        if (!goOn) {
            return@withContext
        }
        withGenericProgress(fragmentManager, "Creating reaction spam...") {
            createContactReactionSpam(getSpamIdentities())

            val groups = getSpamGroupModels()
            logger.debug("Group ids for reaction spam: [{}]", groups.joinToString(", ") { "${it.id}" })
            createGroupReactionSpam(groups)
        }
    }

    private fun getSpamIdentities(): List<IdentityString> =
        contactModelRepository.getAll()
            .filter { isSpamChat(it.data?.firstName) }
            .map(ContactModel::identity)

    private fun getSpamGroupModels(): List<GroupModelOld> =
        groupService.all
            .filter { isSpamChat(it.name) }

    private fun createGroupReactionSpam(
        groups: List<GroupModelOld>,
    ) {
        val reactions = mutableListOf<DbEmojiReaction>()
        val groupService = groupService
        groups.forEach { groupModel ->
            logger.info(
                "Create messages with reaction/ack/dec in group with id={}",
                groupModel.id,
            )
            val members = groupService.getGroupMemberIdentities(groupModel).toList()
            if (members.isEmpty()) {
                logger.debug("Skip group without members")
                return@forEach
            }
            repeat(SPAM_MESSAGES_WITH_REACTIONS_PER_CONVERSATION) {
                logger.debug("Group reaction spam message #{}", it)
                reactions.addAll(
                    createGroupReactionSpam(groupModel, members),
                )
            }
        }

        emojiReactionsRepository.restoreGroupReactions { insertHandle ->
            reactions.shuffled().forEach { insertHandle.insert(it) }
        }
    }

    private fun createGroupReactionSpam(
        groupModel: GroupModelOld,
        members: List<String>,
    ): List<DbEmojiReaction> {
        val userIdentity = userService.identity!!
        val reactionIdentities = mutableListOf<IdentityString>()
        val groupMessageStates = mutableMapOf<IdentityString, String?>()

        members.forEach { memberIdentity ->
            if (Random.nextDouble() > 0.3) {
                // add reactions
                reactionIdentities.add(memberIdentity)
            } else {
                groupMessageStates[memberIdentity] = if (Random.nextBoolean()) {
                    MessageState.USERACK.toString()
                } else {
                    MessageState.USERDEC.toString()
                }
            }
        }

        val reactions = createReactions(reactionIdentities)

        val senderIdentity = members.random()
        val message = createGroupMessage(
            createGroupText(groupMessageStates, reactions),
            senderIdentity,
            userIdentity,
            groupMessageStates,
            groupModel,
        )

        groupMessageModelFactory.create(message)
        return reactions.toDbReactions(message.id)
    }

    private fun createGroupText(
        messageStates: Map<IdentityString, String?>,
        reactions: List<Pair<IdentityString, Set<String>>>,
    ): String {
        val stateTexts = messageStates
            .map { (identity, state) -> "${Mention.Identity(Identity(identity))}: $state" }
        val reactionTexts = reactions
            .map { (identity, reactions) -> "${Mention.Identity(Identity(identity))}: ${reactions.joinToString(", ")}" }
        return (stateTexts + reactionTexts).joinToString("\n")
    }

    private fun createContactTextSpam(
        contactIdentities: List<IdentityString>,
    ) {
        contactIdentities.forEach { contactIdentity ->
            logger.info("Create spam messages for contact with identity {}", contactIdentity)
            repeat(SPAM_TEXT_MESSAGES_PER_CONVERSATION) {
                logger.debug("Contact text spam message #{}", it)
                createContactTextSpamMessage(it, contactIdentity)
            }
        }
    }

    private fun createContactTextSpamMessage(
        no: Int,
        contactIdentity: IdentityString,
    ) {
        val message = createContactMessage(
            "Spam #$no",
            isOutbox = Random.nextBoolean(),
            state = null,
            identity = contactIdentity,
        )
        contactMessageModelFactory.create(message)
    }

    private fun createGroupTextSpam(
        groups: List<GroupModelOld>,
    ) {
        groups.forEach { groupModel ->
            logger.info(
                "Create text messages in group with id={}",
                groupModel.id,
            )
            val members = groupService.getGroupMemberIdentities(groupModel).toList()
            if (members.isEmpty()) {
                logger.debug("Skip empty group")
                return@forEach
            }
            repeat(SPAM_TEXT_MESSAGES_PER_CONVERSATION) {
                logger.debug("Group text spam message #{}", it)
                createGroupTextSpamMessage(it, groupModel, members)
            }
        }
    }

    private fun createGroupTextSpamMessage(
        no: Int,
        groupModel: GroupModelOld,
        members: List<String>,
    ) {
        val userIdentity = userService.identity!!
        val senderIdentity = members.random()
        val message = createGroupMessage(
            "Spam message #$no",
            senderIdentity,
            userIdentity,
            emptyMap(),
            groupModel,
        )

        groupMessageModelFactory.create(message)
    }

    private fun createContactReactionSpam(
        contactIdentities: List<IdentityString>,
    ) {
        val reactions = mutableListOf<DbEmojiReaction>()

        contactIdentities.forEach { contactIdentity ->
            logger.info("Create ack/dec messages for contact with identity {}", contactIdentity)
            repeat(SPAM_MESSAGES_WITH_REACTIONS_PER_CONVERSATION) {
                logger.debug("Contact spam message #{}", it)
                reactions.addAll(createContactReactionSpam(contactIdentity))
            }
        }

        emojiReactionsRepository.restoreContactReactions { insertHandle ->
            reactions.shuffled().forEach { insertHandle.insert(it) }
        }
    }

    private fun createContactReactionSpam(
        contactIdentity: IdentityString,
    ): List<DbEmojiReaction> {
        val userIdentity = userService.identity!!
        val hasUserReactions = Random.nextBoolean()
        val hasContactReactions = Random.nextBoolean()
        val hasAckDec = (!hasContactReactions && !hasUserReactions) ||
            ((!hasContactReactions || !hasUserReactions) && Random.nextBoolean())

        val state = if (!hasAckDec) {
            null
        } else if (Random.nextBoolean()) {
            MessageState.USERACK
        } else {
            MessageState.USERDEC
        }

        val reactionsIdentities = mutableListOf<String>()

        if (hasUserReactions) {
            reactionsIdentities.add(userIdentity)
        }
        if (hasContactReactions) {
            reactionsIdentities.add(contactIdentity)
        }

        val reactions = createReactions(reactionsIdentities)

        val message = createContactMessage(
            createContactText(state, reactions),
            isOutbox = Random.nextBoolean(),
            state = state,
            identity = contactIdentity,
        )
        contactMessageModelFactory.create(message)
        return reactions.toDbReactions(message.id)
    }

    private fun createContactText(
        state: MessageState?,
        reactions: List<Pair<IdentityString, Set<String>>>,
    ): String {
        val stateText = state?.let { "State: $it" }
        val reactionTexts = reactions
            .map { (identity, reactions) -> "${Mention.Identity(Identity(identity))}: ${reactions.joinToString(", ")}" }
        return (listOfNotNull(stateText) + reactionTexts).joinToString("\n")
    }

    private fun List<Pair<String, Set<String>>>.toDbReactions(messageId: Int): List<DbEmojiReaction> {
        return flatMap { (identity, reactions) ->
            reactions.map { reaction ->
                DbEmojiReaction(
                    messageId = messageId,
                    senderIdentity = identity,
                    emojiSequence = reaction,
                    reactedAt = Instant.now(),
                    readAt = null,
                )
            }
        }
    }

    private fun createReactions(identities: List<IdentityString>): List<Pair<IdentityString, Set<String>>> {
        val availableReactions = getReactionSequences(identities.size * 3)
        return identities.map { identity ->
            val numberOfReactions = Random.nextInt(1..3)
            identity to availableReactions.shuffled().take(numberOfReactions).toSet()
        }.filter { it.second.isNotEmpty() }
    }

    private fun isSpamChat(identifier: String?) = identifier?.startsWith(SPAM_CHATS_PREFIX) == true

    private fun createContactMessage(
        text: String,
        isOutbox: Boolean,
        state: MessageState?,
        identity: IdentityString,
    ): ContactMessageModel = ContactMessageModel().apply {
        this.identity = identity
        enrichTextMessage(text, isOutbox, state)
    }

    private fun createGroupMessage(
        text: String,
        senderIdentity: IdentityString,
        userIdentity: IdentityString,
        groupMessageStates: Map<IdentityString, String?>,
        groupModel: GroupModelOld,
    ): GroupMessageModel = GroupMessageModel().apply {
        groupId = groupModel.id
        identity = senderIdentity
        this.groupMessageStates = groupMessageStates.toMutableMap()
        enrichTextMessage(
            text,
            senderIdentity == userIdentity,
        )
    }

    private fun MessageModel.enrichTextMessage(
        text: String,
        isOutbox: Boolean,
        state: MessageState? = null,
    ) {
        val now = Instant.now()
        uid = UUID.randomUUID().toString()
        messageId = MessageId.random()
        this.isOutbox = isOutbox
        this.type = MessageType.TEXT
        body = text
        isRead = true
        this.state = state ?: if (isOutbox) {
            MessageState.DELIVERED
        } else {
            MessageState.READ
        }
        postedAt = now
        createdAt = now
        isSaved = true
    }

    private fun getReactionSequences(n: Int): List<String> = setOf(
        "👍", "👎", "🪒", "🌛", "🧲", "🇹🇹", "🧽", "🧎🏻‍♀️", "🧏🏽‍♀️", "🧝🏻‍♂️",
        "👩🏿‍🚒", "🏌️‍♂️", "👨🏻", "🤸‍♂️", "👩🏿‍🦰", "👨🏼‍🦼", "🕹️", "🍾", "🇨🇫", "🍫",
        "🧀", "🍔", "🕵🏼‍♂️", "👨🏻‍🏫", "🤷🏻‍♀️", "🧯", "🩼", "✍🏾", "🦶🏻", "🏊🏻‍♀️",
        "😔", "⌛", "👮🏿‍♂️", "☔", "🧎🏿‍➡️", "🕡", "👑", "🧖🏾", "🧑🏻‍🔬", "🐧",
        "🧑🏾‍🎤", "🧑🏻‍🦲", "⛲", "👇🏻", "⛹🏼", "🌦️", "🙋🏾", "🦸🏼‍♂️", "👩🏻‍🎤", "🏊🏿",
        "👮🏾‍♂️", "📵", "🧖🏻", "🇱🇹", "👨🏻‍❤️‍👨🏿", "👦🏼", "🚶🏽‍➡️", "🥏", "🏹", "🧑🏻‍🎨",
        "🏄🏿", "🇦🇶", "🧑🏿‍🎄", "👩🏾‍🍳", "📳", "🫱🏼‍🫲🏽", "👨‍👧‍👦", "👩🏽‍❤️‍💋‍👩🏿", "🌐", "🫃🏾",
        "💅🏿", "🤰🏻", "🧎🏽", "🏃🏿‍♂️", "👨🏼‍🚒", "🦇", "✈️", "👩🏽‍🤝‍👨🏿", "🐎", "🏒",
        "👈🏾", "🇱🇺", "🫙", "🇸🇿", "🧍🏼‍♂️", "💁🏼‍♂️", "🧑🏿‍🔧", "👨🏽‍🍳", "🦵🏽", "🧙🏿‍♂️",
        "🧙‍♀️", "💆🏾‍♀️", "↔️", "🧑🏿‍🦲", "🫴🏼", "🤚", "🫱🏼", "🏌🏾‍♂️", "🥦", "🤛🏻",
        "\uD83E\uDEC6",
    ).shuffled().take(n)

    suspend fun createNonces(fragmentManager: FragmentManager) = withContext(dispatcherProvider.worker) {
        val goOn = confirm(
            fragmentManager,
            "Generate $AMOUNT_OF_NONCES nonces for each scope ${NonceScope.CSP} and ${NonceScope.D2D}?",
        )
        if (!goOn) {
            return@withContext
        }
        withGenericProgress(fragmentManager, "Generate random nonces") {
            val myIdentity = identityStore.getIdentityString()!!
            createNonces(NonceScope.CSP, nonceFactory, myIdentity)
            createNonces(NonceScope.D2D, nonceFactory, myIdentity)
        }
    }

    @WorkerThread
    private fun createNonces(scope: NonceScope, nonceFactory: NonceFactory, identity: IdentityString) {
        logger.info("Generate random nonces for scope {}", scope)
        val nonces = (0 until AMOUNT_OF_NONCES).asSequence()
            .map { nonceFactory.next(scope) }
            .map { it.hashNonce(identity) }
            .toList()
        val success = nonceFactory.insertHashedNonces(scope, nonces)
        logger.info("Generate {} nonces success={}", nonces.size, success)
    }

    private suspend fun confirm(fragmentManager: FragmentManager, message: String): Boolean {
        val dialog = GenericAlertDialog.newInstance("Continue?", message, R.string.ok, R.string.no)
        val result = CompletableDeferred<Boolean>()
        dialog.setCallback(
            object : GenericAlertDialog.DialogClickListener {
                override fun onYes(tag: String?, data: Any?) {
                    result.complete(true)
                }

                override fun onNo(tag: String?, data: Any?) {
                    result.complete(false)
                }
            },
        )
        dialog.show(fragmentManager, "CONTENT_CREATOR_CONFIRM_DIALOG")
        return result.await()
    }

    private suspend fun withGenericProgress(
        fragmentManager: FragmentManager,
        message: String,
        block: () -> Unit,
    ) {
        val dialog = GenericProgressDialog.newInstance(null, message)
        withContext(dispatcherProvider.main) {
            dialog.show(fragmentManager, "CONTENT_CREATOR_PROGRESS_DIALOG")
        }
        try {
            block()
        } finally {
            withContext(dispatcherProvider.main) {
                dialog.dismiss()
            }
        }
    }

    companion object {
        private const val AMOUNT_OF_NONCES = 50_000
        private const val SPAM_TEXT_MESSAGES_PER_CONVERSATION = 50_000
        private const val SPAM_MESSAGES_WITH_REACTIONS_PER_CONVERSATION = 1_000

        /**
         * Chats with names that start with this prefix will be used when messages are created.
         * For groups this is the group name, for contacts the first name has to start with the prefix
         */
        private const val SPAM_CHATS_PREFIX = "\uD83D\uDC7E" // 👾
    }
}
