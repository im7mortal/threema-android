package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.GroupModel;
import ch.threema.storage.models.DistributionListModel;

/**
 * Process update/conversation requests from the browser.
 */
@WorkerThread
public class ModifyConversationHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ModifyConversationHandler");

    private static final String FIELD_IS_PINNED = "isStarred";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_INVALID_CONVERSATION,
        Protocol.ERROR_BAD_REQUEST,
    })
    private @interface ErrorCode {
    }

    @NonNull
    private final MessageDispatcher responseDispatcher;
    @NonNull
    private final DistributionListService distributionListService;

    @AnyThread
    public ModifyConversationHandler(
        @NonNull MessageDispatcher responseDispatcher,
        @NonNull ConversationService conversationService,
        @NonNull DistributionListService distributionListService
    ) {
        super(Protocol.SUB_TYPE_CONVERSATION);
        this.responseDispatcher = responseDispatcher;
        this.distributionListService = distributionListService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received update conversation message");

        // Process args
        final Map<String, Value> args = this.getArguments(message, false);
        if (!args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)
            || !args.containsKey(Protocol.ARGUMENT_RECEIVER_ID)
            || !args.containsKey(Protocol.ARGUMENT_RECEIVER_TYPE)) {
            logger.error("Invalid conversation update request, type, id or temporaryId not set");
            return;
        }
        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

        // Get conversation model
        final String id = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().toString();
        final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().toString();
        final ch.threema.app.messagereceiver.MessageReceiver receiver;
        try {
            final Utils.ModelWrapper modelWrapper = new Utils.ModelWrapper(type, id);
            receiver = modelWrapper.getReceiver();
        } catch (ConversionException e) {
            logger.error("Conversion exception in ModifyConversationHandler", e);
            this.failed(temporaryId, Protocol.ERROR_INVALID_CONVERSATION);
            return;
        }

        // Process data
        final Map<String, Value> data = this.getData(message, true);
        if (data == null) {
            this.success(temporaryId);
            return;
        }

        // Handle potential pin tag change
        if (data.containsKey(FIELD_IS_PINNED)) {
            final @Nullable Value valueIsPinned = data.get(FIELD_IS_PINNED);
            if (valueIsPinned == null || !valueIsPinned.isBooleanValue()) {
                this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
                return;
            }

            final boolean isPinned = valueIsPinned.asBooleanValue().getBoolean();

            if (receiver instanceof ContactMessageReceiver) {
                ContactModel contactModel = ((ContactMessageReceiver) receiver).getContactModel();
                if (contactModel == null) {
                    this.failed(temporaryId, Protocol.ERROR_INVALID_CONVERSATION);
                    return;
                }
                if (isPinned) {
                    contactModel.setConversationVisibilityFromLocalOrRemote(ConversationVisibility.PINNED);
                } else {
                    contactModel.setConversationVisibilityFromLocalOrRemote(ConversationVisibility.NORMAL);
                }
            } else if (receiver instanceof GroupMessageReceiver) {
                GroupModel groupModel = ((GroupMessageReceiver) receiver).getGroupModel();
                if (groupModel == null) {
                    this.failed(temporaryId, Protocol.ERROR_INVALID_CONVERSATION);
                    return;
                }
                if (isPinned) {
                    groupModel.setConversationVisibilityFromLocalOrRemote(ConversationVisibility.PINNED);
                } else {
                    groupModel.setConversationVisibilityFromLocalOrRemote(ConversationVisibility.NORMAL);
                }
            } else if (receiver instanceof DistributionListMessageReceiver) {
                DistributionListModel distributionListModel = ((DistributionListMessageReceiver) receiver).getDistributionList();
                if (isPinned) {
                    distributionListService.pin(distributionListModel);
                } else {
                    distributionListService.unpin(distributionListModel);
                }
            }
        }

        this.success(temporaryId);
    }

    /**
     * Respond with confirmAction.
     */
    private void success(String temporaryId) {
        logger.debug("Respond modify conversation success");
        this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
    }

    /**
     * Respond with an error code.
     */
    private void failed(String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond modify conversation failed ({})", errorCode);
        this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, errorCode);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
