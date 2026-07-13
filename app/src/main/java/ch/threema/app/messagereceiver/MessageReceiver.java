package ch.threema.app.messagereceiver;

import android.graphics.Bitmap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.app.services.MessageService;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.data.datatypes.ConversationId;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.poll.PollData;
import ch.threema.domain.protocol.csp.messages.poll.PollVote;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.data.MessageContentsType;

public interface MessageReceiver<M extends AbstractMessageModel> {
    int Type_CONTACT = 0;
    int Type_GROUP = 1;
    int Type_DISTRIBUTION_LIST = 2;

    // Receiver model type annotation
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Type_CONTACT, Type_GROUP, Type_DISTRIBUTION_LIST})
    @interface MessageReceiverType {
    }

    int Reactions_NONE = 0;
    int Reactions_FULL = 1;
    int Reactions_PARTIAL = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Reactions_NONE, Reactions_FULL, Reactions_PARTIAL})
    @interface EmojiReactionsSupport {
    }

    /**
     * Return all affected contact message receivers.
     * <p>
     * Note: Only used in a distribution list, other subtypes should return null.
     */
    default @Nullable List<ContactMessageReceiver> getAffectedMessageReceivers() {
        return null;
    }

    /**
     * create a local (unsaved) db model for the given message type
     */
    M createLocalModel(@Nullable MessageId messageId, MessageType type, @MessageContentsType int contentsType, Instant postedAt);

    /**
     * create a local (unsaved) db model for the given message type
     */
    default M createLocalModel(MessageType type, @MessageContentsType int contentsType, Instant postedAt) {
        return createLocalModel(null, type, contentsType, postedAt);
    }

    /**
     * create a db model for the given message type and save it
     *
     * @deprecated use createAndSaveStatusDataModel instead.
     */
    @Deprecated
    AbstractMessageModel createAndSaveStatusModel(String statusBody, Instant postedAt);

    /**
     * save a message model to the database
     */
    void saveLocalModel(M messageModel);

    /**
     * send a text message
     */
    void createAndSendTextMessage(@NonNull M messageModel);

    /**
     * send a location message
     */
    void createAndSendLocationMessage(@NonNull M messageModel);

    /**
     * send a file message
     */
    void createAndSendFileMessage(
        @Nullable byte[] thumbnailBlobId,
        @Nullable byte[] fileBlobId,
        @Nullable SymmetricEncryptionResult encryptionResult,
        @NonNull M messageModel,
        @Nullable Collection<String> recipientIdentities
    ) throws ThreemaException;

    /**
     * Send a poll (create) message. Note that the message is only sent if the trigger source is
     * local. The message id is added to the message model in any case.
     * TODO(ANDR-3518): The trigger source should not be passed until here. This is only a security
     *  measure as the poll service has many side effects. Ideally, this method would only be
     *  called if a csp message should really be sent out.
     */
    void createAndSendPollSetupMessage(
        @NonNull final PollData pollData,
        @NonNull final PollModel pollModel,
        @NonNull M abstractMessageModel,
        @Nullable Collection<String> recipientIdentities,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException;

    /**
     * Send a poll vote message. Note that the message is only sent if the trigger source is
     * local.
     * TODO(ANDR-3518): The trigger source should not be passed until here. This is only a security
     *  measure as the poll service has many side effects. Ideally, this method would only be
     *  called if a csp message should really be sent out.
     */
    void createAndSendPollVoteMessage(
        PollVote[] votes,
        PollModel pollModel,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException;

    /**
     * select and filter (if filter is set) all message models
     */
    @NonNull
    List<M> loadMessages(MessageService.MessageFilter filter);

    /**
     * Count messages for this receiver
     */
    long getMessagesCount();

    /**
     * count the unread message
     */
    long getUnreadMessagesCount();

    /**
     * get all unread messages
     *
     * @return a list of unread messages
     */
    @NonNull
    List<M> getUnreadMessages() throws SQLException;

    /**
     * compare
     */
    boolean isEqual(MessageReceiver o);

    /**
     * displaying name in gui
     */
    String getDisplayName(@NonNull ContactNameFormat contactNameFormat);

    /**
     * short displaying name in gui
     */
    String getShortName(@NonNull ContactNameFormat contactNameFormat);

    /**
     * @return the bitmap of the avatar in the notification
     */
    Bitmap getNotificationAvatar();

    /**
     * @return the bitmap of the avatar in maximally available resolution and without being cropped to a circle
     */
    Bitmap getHighResAvatar();

    @Nullable
    Bitmap getAvatar();

    /**
     * check, if the message model belongs to this receiver
     */
    boolean isMessageBelongsToMe(AbstractMessageModel message);

    /**
     * check if media should really be sent to this receiver
     * notable exceptions:
     * - distribution lists
     * - groups without members ("notes"), unless MD is active
     */
    boolean shouldSendMediaData();

    /**
     * check if we should offer the user a possibility to retry sending in the UI if the message was queued but there was an IO error in the sender thread
     */
    boolean offerRetry();

    /**
     * validate sending permission
     */
    @NonNull
    SendingPermissionValidationResult validateSendingPermission();

    /**
     * type of the receiver
     */
    @MessageReceiverType
    int getType();

    /**
     * all receiving identities
     *
     * @return array of identities
     */
    String[] getIdentities();

    /**
     * Set the `lastUpdate` field of the specified contact to the current date.
     * This *might* also save the model, and will notify the event bus.
     * <p>
     * Not that this method only has an effect if it is supported by the implementing receiver.
     */
    void bumpLastUpdate();

    /**
     * Check how this particular MessageReceiver supports emoji reactions
     *
     * @return @EmojiReactionsSupport
     */
    @EmojiReactionsSupport
    int getEmojiReactionSupport();

    /**
     * @return The current notification trigger policy override for contact- and group-receivers. Distribution lists
     * do not have this setting.
     */
    @Nullable
    NotificationTriggerPolicyOverride getNotificationTriggerPolicyOverrideOrNull();

    @NonNull
    ConversationId getConversationId();
}
