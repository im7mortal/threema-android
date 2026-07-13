package ch.threema.app.utils;

import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import ch.threema.android.ToastDuration;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.poll.PollMatrixActivity;
import ch.threema.app.dialogs.PollVoteDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.poll.PollService;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.connection.ConnectionState;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.poll.PollId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.poll.PollChoiceModel;
import ch.threema.storage.models.poll.PollModel;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

@SuppressWarnings("rawtypes")
public class PollUtil {
    private static final Logger logger = getThreemaLogger("PollUtil");

    public static boolean canVote(@Nullable PollModel model, @Nullable MessageReceiver messageReceiver) {
        return model != null
            && model.getState() == PollModel.State.OPEN
            && canVote(messageReceiver);
    }

    public static boolean canVote(@Nullable MessageReceiver messageReceiver) {
        return isMember(messageReceiver);
    }

    private static boolean isMember(@Nullable MessageReceiver messageReceiver) {
        if (messageReceiver instanceof GroupMessageReceiver) {
            var groupModel = ((GroupMessageReceiver) messageReceiver).getGroupModel();
            return groupModel != null && groupModel.isMember();
        }
        return true;
    }

    public static boolean canViewMatrix(@Nullable PollModel model) {
        return model != null
            && (model.getType() == PollModel.Type.INTERMEDIATE || model.getState() == PollModel.State.CLOSED);
    }

    public static boolean canClose(@Nullable PollModel model, @Nullable String myIdentity, @Nullable MessageReceiver messageReceiver) {
        return isMine(model, myIdentity)
            && model.getState() == PollModel.State.OPEN
            && isMember(messageReceiver);
    }

    public static boolean isMine(@Nullable PollModel model, @Nullable String myIdentity) {
        return model != null
            && myIdentity != null
            && myIdentity.equals(model.getCreatorIdentity());
    }

    public static void openDefaultActivity(@Nullable Context context, @Nullable FragmentManager fragmentManager, @Nullable PollModel pollModel, @Nullable MessageReceiver messageReceiver) {
        boolean canVote = canVote(pollModel, messageReceiver);
        openDefaultActivity(context, fragmentManager, pollModel, canVote);
    }

    public static void openDefaultActivity(
        @Nullable Context context,
        @Nullable FragmentManager fragmentManager,
        @Nullable PollModel pollModel,
        boolean canVote
    ) {
        if (canVote) {
            openVoteDialog(fragmentManager, pollModel);
        } else if (canViewMatrix(pollModel)) {
            openMatrixActivity(context, pollModel);
        }
    }

    /**
     * Must only be called if [canVote] returns true.
     */
    public static void openVoteDialog(@Nullable FragmentManager fragmentManager, @Nullable PollModel pollModel) {
        if (fragmentManager != null && pollModel != null) {
            PollVoteDialog.newInstance(pollModel.getId()).show(fragmentManager, "vote");
        }
    }

    /**
     * Must only be called if [canViewMatrix] returns true.
     */
    public static void openMatrixActivity(@Nullable Context context, @NonNull PollModel pollModel) {
        if (context != null) {
            Intent intent = new Intent(context, PollMatrixActivity.class);
            IntentDataUtil.append(pollModel, intent);
            context.startActivity(intent);
        }
    }

    @Deprecated
    @NonNull
    public static String getNotificationString(Context context, int pollId) {
        ServiceManager serviceManager = ServiceManager.get();
        if (serviceManager == null) {
            return "";
        }
        var notificationString = getNotificationString(
            context,
            serviceManager.getPollService(),
            pollId
        );
        if (notificationString == null) {
            return "";
        }
        return notificationString;
    }

     @Nullable
    public static String getNotificationString(@NonNull Context context, @NonNull PollService pollService, int pollId) {
        PollModel pollModel = pollService.get(pollId);
        if (pollModel != null) {
            if (pollModel.getState() == PollModel.State.OPEN) {
                return pollModel.getName();
            } else if (pollModel.getState() == PollModel.State.CLOSED) {
                return context.getString(R.string.ballot_message_closed);
            }
        }
        return null;
    }

    /**
     * Must only be called if [canClose] returns true.
     */
    public static void requestClosePoll(PollModel pollModel, Fragment targetFragment, AppCompatActivity targetActivity) {
        FragmentManager fragmentManager = targetActivity != null ? targetActivity.getSupportFragmentManager() : targetFragment.getFragmentManager();
        if (ServiceManager.get().getConnection().getConnectionState() == ConnectionState.LOGGED_IN) {
            GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.ballot_close, R.string.ballot_really_close, R.string.ok, R.string.cancel);
            dialog.setData(pollModel);
            if (targetFragment != null) {
                dialog.setTargetFragment(targetFragment, 0);
            }
            dialog.show(fragmentManager, AppConstants.CONFIRM_TAG_CLOSE_POLL);
        } else {
            SimpleStringAlertDialog dialog = SimpleStringAlertDialog.newInstance(R.string.ballot_close, R.string.ballot_not_connected);
            dialog.show(fragmentManager, "na");
        }
    }

    /**
     * Close the poll.
     *
     * @param activity      if this is not null, a progress dialog is shown
     * @param pollModel   the poll model that will be closed
     * @param pollService the poll service
     * @param messageId     the message id needs to be specified to potentially match the message id
     *                      of the reflected outgoing message. In case the trigger source of closing
     *                      the poll is not a reflected outgoing poll setup message, a randomly
     *                      generated message id must be passed.
     * @param triggerSource the trigger source of this action. If it is sync, then there won't be
     *                      any csp messages sent out
     */
    public static void closePoll(
        @Nullable AppCompatActivity activity,
        @Nullable final PollModel pollModel,
        @NonNull final PollService pollService,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) {
        if (pollModel != null && pollModel.getState() != PollModel.State.CLOSED) {
            Runnable pollCloseRunnable = () -> {
                try {
                    pollService.close(pollModel.getId(), messageId, triggerSource);
                } catch (final NotAllowedException | MessageTooLongException e) {
                    logger.error("Could not close poll", e);
                }
            };
            if (activity != null) {
                LoadingUtil.runInAlert(
                    activity.getSupportFragmentManager(),
                    R.string.ballot_close,
                    R.string.please_wait,
                    pollCloseRunnable
                );
            } else {
                pollCloseRunnable.run();
            }
        }
    }

    /**
     * Create a poll.
     *
     * @param receiver              the message receiver
     * @param description           the description of the poll (in some places also called
     *                              title)
     * @param pollType              the type of the poll (with intermediate results or not)
     * @param pollAssessment        the assessment (single vs multiple choice)
     * @param pollChoiceModelList   the choices that are available. Note that the apiChoiceId must
     *                              be unique for each item.
     * @param pollId                the poll id must be a random id, except when the poll is
     *                              created as a result of a reflected outgoing poll setup message
     * @param messageId             the message id needs to be specified to potentially match the
     *                              message id of the reflected outgoing message. In case the
     *                              trigger source of creating the poll is not a reflected
     *                              outgoing poll setup message, a randomly generated message id
     *                              must be passed.
     * @param triggerSource         the trigger source of this action. If it is sync, then there
     *                              won't be any csp messages sent out
     */
    @Nullable
    public static PollModel createPoll(
        MessageReceiver receiver,
        String description,
        PollModel.Type pollType,
        PollModel.Assessment pollAssessment,
        List<PollChoiceModel> pollChoiceModelList,
        @NonNull PollId pollId,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) {
        @NonNull
        PollModel pollModel;

        try {
            PollService pollService = ServiceManager.get().getPollService();
            PollModel.ChoiceType choiceType = PollModel.ChoiceType.TEXT;

            switch (receiver.getType()) {
                case MessageReceiver.Type_GROUP:
                    pollModel = pollService.create(
                        ((GroupMessageReceiver) receiver).getGroup(),
                        description,
                        PollModel.State.TEMPORARY,
                        pollAssessment,
                        pollType,
                        choiceType,
                        pollId
                    );
                    break;

                case MessageReceiver.Type_CONTACT:
                    pollModel = pollService.create(
                        ((ContactMessageReceiver) receiver).getContact(),
                        description,
                        PollModel.State.TEMPORARY,
                        pollAssessment,
                        pollType,
                        choiceType,
                        pollId
                    );
                    break;
                default:
                    throw new NotAllowedException("not allowed");
            }

            //add choices
            for (PollChoiceModel c : pollChoiceModelList) {
                pollService.update(pollModel, c);
            }

            try {
                pollService.modifyFinished(pollModel, messageId, triggerSource);
                if (triggerSource == TriggerSource.LOCAL) {
                    showToast(ThreemaApplication.getAppContext(), R.string.ballot_created_successfully, ToastDuration.LONG);
                }
            } catch (MessageTooLongException e) {
                pollService.remove(pollModel);
                showToast(ThreemaApplication.getAppContext(), R.string.message_too_long, ToastDuration.LONG);
                logger.error("Exception", e);
            }
            return pollModel;
        } catch (Exception e) {
            showToast(ThreemaApplication.getAppContext(), R.string.an_error_occurred, ToastDuration.LONG);
            logger.error("Exception", e);
        }

        return null;
    }
}
