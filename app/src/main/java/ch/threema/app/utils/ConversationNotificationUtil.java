package ch.threema.app.utils;

import android.Manifest;
import android.graphics.Bitmap;
import android.net.Uri;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
import ch.threema.android.ResolvableString;
import ch.threema.android.ResolvedString;
import ch.threema.android.ResourceIdString;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.notification.ConversationContactReactionNotification;
import ch.threema.app.services.notification.ConversationGroupReactionNotification;
import ch.threema.app.services.notification.ConversationMessageNotification;
import ch.threema.app.services.notification.NotificationConversationHandle;
import ch.threema.app.services.notification.NotificationService;

import static ch.threema.android.PermissionExtensionsKt.checkGrantedPermission;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase;
import ch.threema.data.IdentityProvider;
import ch.threema.data.datatypes.AndroidContactLookupInfo;
import ch.threema.data.datatypes.ContactConversationId;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy;
import ch.threema.data.datatypes.ConversationCategory;
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.EmojiReactionData;
import ch.threema.data.models.EmojiReactionsModel;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.EmojiReactionsRepository;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.data.datatypes.ConversationId;
import ch.threema.data.datatypes.GroupConversationId;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.ContactMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;

// TODO(ANDR-4691): This class needs refactoring
@SuppressWarnings("LoggingSimilarMessage")
public class ConversationNotificationUtil {
    private static final Logger logger = getThreemaLogger("ConversationNotificationUtil");

    protected static final HashMap<ConversationId, NotificationConversationHandle> conversationHandleCache = new HashMap<>();
    private static final int MAX_NOTIFICATION_THUMBNAIL_SIZE_BYTES = 1024 * 1024;

    @Nullable
    public static ConversationMessageNotification convertMessageNotification(
        @NonNull MessageModel messageModel,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull ContactService contactService,
        @NonNull GroupService groupService,
        @NonNull ContactNameFormat contactNameFormat,
        @NonNull GetGroupDisplayNameUseCase getGroupDisplayNameUseCase
    ) {
        ConversationMessageNotification conversationNotification = null;
        if (messageModel instanceof ContactMessageModel) {
            conversationNotification = createContactMessageNotification(
                (ContactMessageModel) messageModel,
                contactModelRepository,
                contactService,
                contactNameFormat
            );
        } else if (messageModel instanceof GroupMessageModel) {
            conversationNotification = createGroupMessageNotification(
                (GroupMessageModel) messageModel,
                contactModelRepository,
                groupModelRepository,
                groupService,
                contactNameFormat,
                getGroupDisplayNameUseCase
            );
        }

        return conversationNotification;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static ConversationContactReactionNotification createContactReactionNotification(
        @NonNull ContactMessageModel targetMessage,
        @NonNull EmojiReactionsRepository emojiReactionsRepository,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull ContactService contactService,
        @NonNull ContactNameFormat contactNameFormat,
        @NonNull IdentityProvider identityProvider
    ) {
        final @Nullable String targetMessageUid = targetMessage.getUid();
        if (targetMessageUid == null) {
            logger.error("Missing message uid from target message with api id {}", targetMessage.getApiMessageId());
            return null;
        }
        if (!targetMessage.isOutbox()) {
            logger.error("Cannot show a reaction notification for an incoming message (uid = {})", targetMessage.getUid());
            return null;
        }
        final @Nullable String reactorIdentity = targetMessage.getIdentity();
        if (reactorIdentity == null) {
            logger.error("Missing message identity from target message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable ContactModel contactModel = contactModelRepository.getByIdentity(reactorIdentity);
        if (contactModel == null) {
            logger.warn("Missing reactor contact from target message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable ContactModelData contactModelData = contactModel.getData();
        if (contactModelData == null) {
            throw new IllegalStateException("Cannot create conversation reaction notification if contact model data is null");
        }
        final @Nullable EmojiReactionsModel emojiReactionsModel = emojiReactionsRepository.getReactionsByMessage(targetMessage);
        if (emojiReactionsModel == null) {
            logger.error("Cannot find emoji reactions model for message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable List<EmojiReactionData> reactions = (List<EmojiReactionData>) emojiReactionsModel.getData();
        if (reactions == null) {
            logger.warn("Missing emoji reactions for message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable String userIdentity = identityProvider.getIdentityString();
        if (userIdentity == null) {
            logger.error("Missing user identity");
            return null;
        }
        final @NonNull List<EmojiReactionData> reactionsFromOthers = reactions
            .stream()
            .filter((reaction) -> !reaction.senderIdentity.equals(userIdentity))
            .filter((reaction) -> reaction.readAt == null)
            .toList();

        final @NonNull ConversationId conversationId = new ContactConversationId(reactorIdentity);
        final @Nullable Instant messageCreatedAt = targetMessage.getCreatedAt();
        if (messageCreatedAt == null) {
            logger.error("Missing createdAt from message {}", targetMessage.getUid());
            return null;
        }

        synchronized (conversationHandleCache) {

            @Nullable NotificationConversationHandle notificationConversationHandle = conversationHandleCache.get(conversationId);
            final boolean isPrivateConversation = contactModelData.conversationCategory == ConversationCategory.PROTECTED;
            final @NonNull ResolvableString longName = isPrivateConversation
                ? new ResourceIdString(R.string.private_chat_subject)
                : new ResolvedString(
                    NameUtil.getContactDisplayNameOrNickname(contactModel.getIdentity(), contactService, contactNameFormat)
                );
            final @Nullable NotificationTriggerPolicyOverride<ContactNotificationTriggerPolicyOverridePolicy> notificationTriggerPolicyOverride =
                contactModelData.notificationTriggerPolicyOverride;

            if (notificationConversationHandle == null) {
                notificationConversationHandle = new NotificationConversationHandle(
                    conversationId,
                    longName,
                    notificationTriggerPolicyOverride,
                    () -> {
                        final @Nullable String identity = isPrivateConversation
                            ? null
                            : contactModel.getIdentity();
                        return contactService.getAvatar(identity, false);
                    }
                );
                conversationHandleCache.put(conversationId, notificationConversationHandle);
            } else {
                // contact name and notification-trigger-policy may change between notifications - update them
                notificationConversationHandle.name = longName;
                notificationConversationHandle.notificationTriggerPolicyOverride = notificationTriggerPolicyOverride;
            }

            final @NonNull ArrayList<String> emojiSequences = new ArrayList<>(reactionsFromOthers.size());
            for (@NonNull EmojiReactionData emojiReactionData : reactionsFromOthers) {
                emojiSequences.add(emojiReactionData.emojiSequence);
            }

            final @NonNull String reactorShortName = NameUtil.getShortName(reactorIdentity, contactService, contactNameFormat);

            return new ConversationContactReactionNotification(
                targetMessage.getId(),
                targetMessageUid,
                notificationConversationHandle,
                messageCreatedAt,
                getPerson(contactService, contactModel, reactorShortName),
                getThumbnailMimeType(targetMessage),
                getFetchThumbnail(targetMessage),
                targetMessage,
                emojiSequences
            );
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static ConversationGroupReactionNotification createGroupReactionNotification(
        @NonNull GroupMessageModel targetMessage,
        @NonNull EmojiReactionsRepository emojiReactionsRepository,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull GetGroupDisplayNameUseCase getGroupDisplayNameUseCase,
        @NonNull ContactNameFormat contactNameFormat,
        @NonNull GroupService groupService,
        @NonNull IdentityProvider identityProvider
    ) {
        final @Nullable String targetMessageUid = targetMessage.getUid();
        if (targetMessageUid == null) {
            logger.error("Missing message uid from target message with api id {}", targetMessage.getApiMessageId());
            return null;
        }
        if (!targetMessage.isOutbox()) {
            logger.error("Cannot show a reaction notification for an incoming message (uid = {})", targetMessage.getUid());
            return null;
        }
        final @Nullable GroupModel groupModel = groupModelRepository.getByGroupDatabaseId(targetMessage.getGroupId());
        final @Nullable GroupModelOld groupModelOld = groupService.getById(targetMessage.getGroupId());
        if (groupModel == null || groupModelOld == null) {
            logger.warn("Missing group from target message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable GroupModelData groupModelData = groupModel.getData();
        if (groupModelData == null) {
            throw new IllegalStateException("Cannot create conversation reaction notification if group model data is null");
        }
        final @Nullable EmojiReactionsModel emojiReactionsModel = emojiReactionsRepository.getReactionsByMessage(targetMessage);
        if (emojiReactionsModel == null) {
            logger.error("Cannot find emoji reactions model for message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable List<EmojiReactionData> reactions = (List<EmojiReactionData>) emojiReactionsModel.getData();
        if (reactions == null) {
            logger.warn("Missing emoji reactions for message {}", targetMessage.getUid());
            return null;
        }
        final @Nullable String userIdentity = identityProvider.getIdentityString();
        if (userIdentity == null) {
            logger.error("Missing user identity");
            return null;
        }
        final @NonNull List<EmojiReactionData> reactionsFromOthers = reactions
            .stream()
            .filter((reaction) -> !reaction.senderIdentity.equals(userIdentity))
            .filter((reaction) -> reaction.readAt == null)
            .toList();

        final @NonNull ConversationId conversationId = new GroupConversationId(targetMessage.getGroupId());
        final @Nullable Instant messageCreatedAt = targetMessage.getCreatedAt();
        if (messageCreatedAt == null) {
            logger.error("Missing createdAt from message {}", targetMessage.getUid());
            return null;
        }
        synchronized (conversationHandleCache) {
            @Nullable NotificationConversationHandle notificationConversationHandle = conversationHandleCache.get(conversationId);
            final boolean isPrivateConversation = groupModelData.conversationCategory == ConversationCategory.PROTECTED;
            final @NonNull ResolvableString name = isPrivateConversation
                ? new ResourceIdString(R.string.private_chat_subject)
                : getGroupDisplayNameUseCase.call(groupModel, contactNameFormat);
            final @Nullable NotificationTriggerPolicyOverride<GroupNotificationTriggerPolicyOverridePolicy> notificationTriggerPolicyOverride =
                groupModelData.notificationTriggerPolicyOverride;

            if (notificationConversationHandle == null) {
                notificationConversationHandle = new NotificationConversationHandle(
                    conversationId,
                    name,
                    notificationTriggerPolicyOverride,
                    () ->
                        groupService.getAvatar(
                            isPrivateConversation ? null : groupModelOld,
                            false
                        )
                );
                conversationHandleCache.put(conversationId, notificationConversationHandle);
            } else {
                // group name and notification-trigger-policy may change between notifications - update them
                notificationConversationHandle.name = name;
                notificationConversationHandle.notificationTriggerPolicyOverride = notificationTriggerPolicyOverride;
            }

            final @NonNull ArrayList<String> emojiSequences = new ArrayList<>(reactionsFromOthers.size());
            final @NonNull ArrayList<String> reactorIdentities = new ArrayList<>(reactionsFromOthers.size());
            for (@NonNull EmojiReactionData emojiReactionData : reactionsFromOthers) {
                emojiSequences.add(emojiReactionData.emojiSequence);
                reactorIdentities.add(emojiReactionData.senderIdentity);
            }

            return new ConversationGroupReactionNotification(
                targetMessage.getId(),
                targetMessageUid,
                notificationConversationHandle,
                messageCreatedAt,
                getThumbnailMimeType(targetMessage),
                getFetchThumbnail(targetMessage),
                targetMessage,
                emojiSequences,
                reactorIdentities
            );
        }
    }

    @NonNull
    private static MessageService.MessageString getMessageString(MessageModel messageModel) {
        try {
            return ServiceManager.require().getMessageService()
                .getMessageString(messageModel, -1, false);
        } catch (Exception e) {
            logger.error("Exception", e);
            return new MessageService.MessageString(null);
        }
    }

    @Nullable
    private static Person getSenderPerson(
        @NonNull MessageModel messageModel,
        @NonNull ContactModelRepository contactModelRepository
    ) {
        final PreferenceService preferenceService = ServiceManager.get().getPreferenceService();
        final ContactService contactService = ServiceManager.get().getContactService();
        final ContactModel contactModel = contactModelRepository.getByIdentity(messageModel.getIdentity());
        return getPerson(
            contactService,
            contactModel,
            NameUtil.getShortName(
                ThreemaApplication.getAppContext(),
                messageModel,
                contactService,
                preferenceService.getContactNameFormat()
            )
        );
    }

    @Nullable
    public static Person getPerson(
        @Nullable ContactService contactService,
        @Nullable ContactModel contactModel,
        @Nullable String name
    ) {
        if (contactService == null) {
            return null;
        }
        final @Nullable String identity = contactModel != null
            ? contactModel.getIdentity()
            : null;
        Person.Builder builder = new Person.Builder()
            .setKey(
                identity != null
                    ? new ContactConversationId(identity).getObfuscated().value
                    : ""
            )
            .setName(name);
        Bitmap avatar = contactService.getAvatar(identity, false);
        if (avatar != null) {
            IconCompat iconCompat = IconCompat.createWithBitmap(avatar);
            builder.setIcon(iconCompat);
        }
        ContactModelData contactModelData = contactModel != null ? contactModel.getData() : null;
        AndroidContactLookupInfo androidContactLookupInfo = contactModelData != null ? contactModelData.androidContactLookupInfo : null;
        if (androidContactLookupInfo != null) {
            if (checkGrantedPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS)) {
                builder.setUri(androidContactLookupInfo.getContactUri().toString());
            }
        }
        return builder.build();
    }

    private static MessageType getMessageType(@NonNull MessageModel messageModel) {
        return messageModel.getType();
    }

    @Nullable
    private static ConversationMessageNotification createContactMessageNotification(
        @NonNull final ContactMessageModel messageModel,
        @NonNull final ContactModelRepository contactModelRepository,
        @NonNull final ContactService contactService,
        @NonNull final ContactNameFormat contactNameFormat
    ) {
        final @Nullable ContactModel contactModel = contactModelRepository.getByIdentity(messageModel.getIdentity());
        if (contactModel == null) {
            logger.warn("Contact for notification not found");
            return null;
        }
        final ConversationId conversationId = new ContactConversationId(messageModel.getIdentity());
        final @Nullable ContactModelData contactModelData = contactModel.getData();
        if (contactModelData == null) {
            throw new IllegalStateException("Cannot create conversation notification if contact model data is null");
        }
        final @Nullable Instant messageCreatedAt = messageModel.getCreatedAt();
        if (messageCreatedAt == null) {
            logger.error("Missing createdAt from message {}", messageModel.getUid());
            return null;
        }
        synchronized (conversationHandleCache) {
            NotificationConversationHandle conversationHandle = conversationHandleCache.get(conversationId);
            final boolean isPrivateConversation = contactModelData.conversationCategory == ConversationCategory.PROTECTED;
            final @NonNull ResolvableString longName = isPrivateConversation
                ? new ResourceIdString(R.string.private_chat_subject)
                : new ResolvedString(
                    NameUtil.getContactDisplayNameOrNickname(contactModel.getIdentity(), contactService, contactNameFormat)
                );
            final @Nullable NotificationTriggerPolicyOverride<ContactNotificationTriggerPolicyOverridePolicy> notificationTriggerPolicyOverride =
                contactModelData.notificationTriggerPolicyOverride;

            if (conversationHandle == null) {
                conversationHandle = new NotificationConversationHandle(
                    conversationId,
                    longName,
                    notificationTriggerPolicyOverride,
                    () -> {
                        final @Nullable String identity = isPrivateConversation
                            ? null
                            : contactModel.getIdentity();
                        return contactService.getAvatar(identity, false);
                    }
                );
                conversationHandleCache.put(conversationId, conversationHandle);
            } else {
                // contact name and notification-trigger-policy may change between notifications - update them
                conversationHandle.name = longName;
                conversationHandle.notificationTriggerPolicyOverride = notificationTriggerPolicyOverride;
            }

            return new ConversationMessageNotification(
                messageModel.getId(),
                messageModel.getUid(),
                conversationHandle,
                messageCreatedAt,
                getThumbnailMimeType(messageModel),
                getFetchThumbnail(messageModel),
                getSenderPerson(messageModel, contactModelRepository),
                getMessageString(messageModel),
                getMessageType(messageModel),
                messageModel.isEdited(),
                messageModel.isDeleted()
            );
        }
    }

    @Nullable
    private static String getThumbnailMimeType(@NonNull MessageModel messageModel) {
        if (MessageType.FILE.equals(messageModel.getType()) &&
            (messageModel.getMessageContentsType() == MessageContentsType.IMAGE ||
                messageModel.getMessageContentsType() == MessageContentsType.VIDEO
            )) {
            return MimeUtil.MIME_TYPE_IMAGE_PNG.equals(messageModel.getFileData().getThumbnailMimeType()) ? MimeUtil.MIME_TYPE_IMAGE_PNG : MimeUtil.MIME_TYPE_IMAGE_JPEG;
        }
        return null;
    }

    @Nullable
    private static ConversationMessageNotification createGroupMessageNotification(
        @NonNull final GroupMessageModel messageModel,
        @NonNull final ContactModelRepository contactModelRepository,
        @NonNull final GroupModelRepository groupModelRepository,
        @NonNull final GroupService groupService,
        @NonNull final ContactNameFormat contactNameFormat,
        @NonNull final GetGroupDisplayNameUseCase getGroupDisplayNameUseCase
    ) {
        final @Nullable GroupModel groupModel = groupModelRepository.getByGroupDatabaseId(messageModel.getGroupId());
        final @Nullable GroupModelOld groupModelOld = groupService.getById(messageModel.getGroupId());
        if (groupModel == null || groupModelOld == null) {
            logger.warn("Group for notification not found");
            return null;
        }
        final ConversationId conversationId = new GroupConversationId(messageModel.getGroupId());
        final @Nullable GroupModelData groupModelData = groupModel.getData();
        if (groupModelData == null) {
            throw new IllegalStateException("Cannot create conversation notification if group model data is null");
        }
        final @Nullable Instant messageCreatedAt = messageModel.getCreatedAt();
        if (messageCreatedAt == null) {
            logger.error("Missing createdAt from message {}", messageModel.getUid());
            return null;
        }

        synchronized (conversationHandleCache) {
            @Nullable NotificationConversationHandle conversationHandle = conversationHandleCache.get(conversationId);
            final boolean isPrivateConversation = groupModelData.conversationCategory == ConversationCategory.PROTECTED;
            final @NonNull ResolvableString name = isPrivateConversation
                ? new ResourceIdString(R.string.private_chat_subject)
                : getGroupDisplayNameUseCase.call(groupModel, contactNameFormat);
            final @Nullable NotificationTriggerPolicyOverride<GroupNotificationTriggerPolicyOverridePolicy> notificationTriggerPolicyOverride =
                groupModelData.notificationTriggerPolicyOverride;

            if (conversationHandle == null) {
                conversationHandle = new NotificationConversationHandle(
                    conversationId,
                    name,
                    notificationTriggerPolicyOverride,
                    () ->
                        groupService.getAvatar(
                            isPrivateConversation
                                ? null
                                : groupModelOld,
                            false
                        )
                );
                conversationHandleCache.put(conversationId, conversationHandle);
            } else {
                // group name and notification-trigger-policy may change between notifications - update them
                conversationHandle.name = name;
                conversationHandle.notificationTriggerPolicyOverride = notificationTriggerPolicyOverride;
            }

            return new ConversationMessageNotification(
                messageModel.getId(),
                messageModel.getUid(),
                conversationHandle,
                messageCreatedAt,
                getThumbnailMimeType(messageModel),
                getFetchThumbnail(messageModel),
                getSenderPerson(messageModel, contactModelRepository),
                getMessageString(messageModel),
                getMessageType(messageModel),
                messageModel.isEdited(),
                messageModel.isDeleted()
            );
        }
    }

    @Nullable
    public static NotificationService.FetchCacheUri getFetchThumbnail(@NonNull final MessageModel messageModel) {
        if (messageModel.getMessageContentsType() == MessageContentsType.IMAGE ||
            messageModel.getMessageContentsType() == MessageContentsType.VIDEO
        ) {
            if (messageModel.getType() == MessageType.FILE && messageModel.getFileData().getRenderingType() != FileData.RENDERING_MEDIA) {
                return null;
            }

            return new NotificationService.FetchCacheUri() {
                @Override
                @Nullable
                @WorkerThread
                public Uri fetch() {
                    final FileService fileService = KoinJavaComponent.get(FileService.class);
                    return fileService.decryptThumbnailToShareableTempFile(messageModel, MAX_NOTIFICATION_THUMBNAIL_SIZE_BYTES);
                }
            };
        }
        return null;
    }
}
