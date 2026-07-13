package ch.threema.app.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.lifecycle.Lifecycle;
import ch.threema.android.FlowJavaCompat;
import ch.threema.app.R;
import ch.threema.app.adapters.poll.PollVoteListAdapter;
import ch.threema.app.emojis.EmojiConversationTextView;
import ch.threema.app.eventbus.GlobalEventFlows;
import ch.threema.app.eventbus.events.PollEvent;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.services.poll.PollService;
import ch.threema.app.services.poll.PollVoteResult;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.utils.LoadingUtil;
import ch.threema.app.utils.RuntimeUtil;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.poll.PollChoiceModel;
import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.poll.PollVoteModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PollVoteDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("PollVoteDialog");

    @NonNull
    private final GlobalEventFlows globalEventFlows = KoinJavaComponent.get(GlobalEventFlows.class);

    private Activity activity;
    private AlertDialog alertDialog;
    private ListView listView;
    private PollModel pollModel;

    private PollService pollService;
    private int pollId;
    private PollVoteListAdapter listAdapter = null;
    private EmojiConversationTextView titleTextView;

    private boolean pollEventHandlingEnabled = true;

    private Thread votingThread = null;

    public static PollVoteDialog newInstance(@StringRes int pollId) {
        PollVoteDialog dialog = new PollVoteDialog();
        Bundle args = new Bundle();
        args.putInt("pollId", pollId);

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        FlowJavaCompat.collect(this, Lifecycle.State.CREATED, globalEventFlows.getPolls(), this::handlePollEvent);
    }

    @UiThread
    private void handlePollEvent(@NonNull PollEvent event) {
        if (!pollEventHandlingEnabled || pollId != event.getPoll().getId()) {
            return;
        }
        if (
            event instanceof PollEvent.PollUpdated ||
                event instanceof PollEvent.PollVoted ||
                event instanceof PollEvent.PollVoteRemoved
        ) {
            updateView();
        } else if (event instanceof PollEvent.PollRemoved) {
            showToast(requireContext(), R.string.ballot_removed);
            dismiss();
        }
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    public void onDetach() {
        this.activity = null;

        super.onDetach();
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null && alertDialog != null) {
            return alertDialog;
        }

        this.pollService = KoinJavaComponent.get(PollService.class);

        pollId = getArguments().getInt("pollId");
        pollModel = pollService.get(pollId);

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_poll_vote, null);
        this.listView = dialogView.findViewById(R.id.poll_list);
        this.titleTextView = dialogView.findViewById(R.id.title);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme());
        builder.setView(dialogView);
        builder.setPositiveButton(getString(R.string.ballot_vote), (dialog, whichButton) -> vote());
        builder.setNegativeButton(R.string.cancel, (dialog, whichButton) -> dismiss());

        alertDialog = builder.create();
        if (titleTextView != null && pollModel != null) {
            titleTextView.setText(pollModel.getName());
        }

        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (this.listView != null) {
            this.listView.setOnItemClickListener((adapterView, view, i, l) -> {
                ((CheckableRelativeLayout) view).toggle();
            });
            this.listView.setClipToPadding(false);
        }

        this.updateView();
    }

    /******/

    private void updateView() {
        try {
            if (this.pollId <= 0) {
                dismiss();
                return;
            }

            try {
                pollEventHandlingEnabled = false;
                PollModel pollModel = this.pollService.get(this.pollId);

                if (pollModel == null && activity != null) {
                    Toast.makeText(activity, R.string.ballot_not_exist, Toast.LENGTH_SHORT).show();
                    logger.error("invalid poll model");
                    dismiss();
                    return;
                }

                this.pollService.viewingPoll(pollModel, true);
            } finally {
                pollEventHandlingEnabled = true;
            }

            Map<Integer, Integer> selected;

            if (this.listAdapter != null) {
                selected = this.listAdapter.getSelectedChoices();
            } else {
                //load from db
                selected = new HashMap<>();
                for (final PollVoteModel c : this.pollService.getMyVotes(this.pollId)) {
                    selected.put(c.getPollChoiceId(), c.getChoice());
                }
            }
            List<PollChoiceModel> pollChoiceModelList = this.pollService.getChoices(this.pollId);
            boolean showVoting = this.pollModel.getType() == PollModel.Type.INTERMEDIATE || this.pollModel.getState() == PollModel.State.CLOSED;
            this.listAdapter = new PollVoteListAdapter(
                getContext(),
                pollChoiceModelList,
                selected,
                this.pollModel.getState() != PollModel.State.OPEN,
                this.pollModel.getAssessment() == PollModel.Assessment.MULTIPLE_CHOICE,
                showVoting
            );
            this.listView.setAdapter(this.listAdapter);
        } catch (NotAllowedException e) {
            logger.error("cannot reload choices", e);
        }
    }

    private void vote() {
        if (this.votingThread != null && this.votingThread.isAlive()) {
            logger.debug("voting thread alive, abort");
            return;
        }

        logger.debug("create new voting thread");
        this.votingThread = LoadingUtil.runInAlert(getFragmentManager(),
            R.string.ballot_vote,
            R.string.please_wait,
            new Runnable() {
                @Override
                public void run() {
                    try {
                        voteThread();
                        dismiss();
                    } catch (Exception x) {
                        logger.error("Exception", x);
                    }
                }
            });
    }

    private void voteThread() {
        try {
            final PollVoteResult result = this.pollService.vote(pollModel.getId(), this.listAdapter.getSelectedChoices(), TriggerSource.LOCAL);
            if (result != null) {
                RuntimeUtil.runOnUiThread(() -> {
                    if (activity != null) {
                        if (result.isSuccess()) {
                            Toast.makeText(activity, R.string.ballot_vote_posted_successfully, Toast.LENGTH_SHORT).show();
                            dismiss();
                        } else {
                            Toast.makeText(activity, R.string.ballot_vote_posted_failed, Toast.LENGTH_SHORT).show();
                            updateView();
                        }
                    }
                });
            }

        } catch (final NotAllowedException e) {
            RuntimeUtil.runOnUiThread(() -> {
                if (activity != null) {
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
