package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.view.View;

import org.slf4j.Logger;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.PollUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.data.media.PollDataModel;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class PollChatAdapterDecorator extends ChatAdapterDecorator {
    private static final Logger logger = getThreemaLogger("PollChatAdapterDecorator");

    public final static int ACTION_VOTE = 0, ACTION_RESULTS = 1, ACTION_CLOSE = 2;

    public interface PollChatListener {
        void showSelectorDialog(
            ArrayList<Integer> action,
            String title,
            ArrayList<SelectorDialogItem> items,
            PollModel pollModel
        );

        void openDefaultActivity(PollModel pollModel, boolean canVote);
    }

    @NonNull
    private final PollChatListener listener;

    public PollChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper,
        @NonNull PollChatListener listener
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
        this.listener = listener;
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, int position) {
        try {
            final AbstractMessageModel messageModel = this.getMessageModel();
            String explain = "";

            PollDataModel pollData = messageModel.getPollData();

            final PollModel pollModel = this.helper.getPollService().get(pollData.getPollId());

            if (pollModel == null) {
                holder.bodyTextView.setText("");
            } else {
                switch (pollData.getType()) {
                    case POLL_CREATED:
                    case POLL_MODIFIED:
                        if (PollUtil.canVote(pollModel, helper.getMessageReceiver())) {
                            explain = context.getString(R.string.ballot_tap_to_vote);
                        }
                        break;
                    case POLL_CLOSED:
                        explain = context.getString(R.string.ballot_tap_to_view_results);
                        break;
                }

                if (this.showHide(holder.bodyTextView, true)) {
                    holder.bodyTextView.setText(pollModel.getName());
                }
            }

            if (this.showHide(holder.secondaryTextView, true)) {
                holder.secondaryTextView.setText(explain);
            }

            this.setOnClickListener(new DebouncedOnClickListener(500) {
                @Override
                public void onDebouncedClick(View v) {
                    if (messageModel.getState() != MessageState.FS_KEY_MISMATCH && messageModel.getState() != MessageState.SENDFAILED) {
                        showChooser(v.getContext(), pollModel);
                    }
                }
            }, holder.messageBlockView);

            if (holder.controller != null) {
                holder.controller.setIconResource(R.drawable.ic_outline_rule);
            }

            RuntimeUtil.runOnUiThread(() -> setupResendStatus(holder));
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    private void showChooser(Context context, final PollModel pollModel) {
        ArrayList<SelectorDialogItem> items = new ArrayList<>();
        final ArrayList<Integer> action = new ArrayList<>();
        String title = null;

        if (PollUtil.canVote(pollModel, helper.getMessageReceiver())) {
            items.add(new SelectorDialogItem(context.getString(R.string.ballot_vote), R.drawable.ic_vote_outline));
            action.add(ACTION_VOTE);
        }

        var canView = PollUtil.canViewMatrix(pollModel);
        if (canView) {
            if (pollModel.getState() == PollModel.State.CLOSED) {
                items.add(new SelectorDialogItem(context.getString(R.string.ballot_result_final), R.drawable.ic_poll_outline));
            } else {
                items.add(new SelectorDialogItem(context.getString(R.string.ballot_result_intermediate), R.drawable.ic_poll_outline));
            }
            action.add(ACTION_RESULTS);
        }

        var canClose = PollUtil.canClose(pollModel, helper.getMyIdentity(), helper.getMessageReceiver());
        if (canClose) {
            items.add(new SelectorDialogItem(context.getString(R.string.ballot_close), R.drawable.ic_check));
            action.add(ACTION_CLOSE);
        }

        if (canClose || canView) {
            title = String.format(context.getString(R.string.ballot_received_votes),
                helper.getPollService().getVotedParticipants(pollModel.getId()).size(),
                helper.getPollService().getParticipants(pollModel.getId()).length);
        }

        if (items.size() > 1) {
            listener.showSelectorDialog(action, title, items, pollModel);
        } else if (!items.isEmpty()) {
            boolean canVote = PollUtil.canVote(pollModel, helper.getMessageReceiver());
            listener.openDefaultActivity(pollModel, canVote);
        }
    }
}
