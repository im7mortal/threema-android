package ch.threema.app.activities.poll;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.Lifecycle;
import ch.threema.android.FlowJavaCompat;
import ch.threema.app.R;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.eventbus.GlobalEventFlows;
import ch.threema.app.eventbus.events.PollEvent;
import ch.threema.app.services.poll.PollMatrixData;
import ch.threema.app.services.poll.PollMatrixService;
import ch.threema.app.ui.HintedImageView;
import ch.threema.app.ui.HintedTextView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.ThreemaException;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.poll.PollVoteModel;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PollMatrixActivity extends PollDetailActivity {
    private static final Logger logger = getThreemaLogger("PollMatrixActivity");

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);
    @NonNull
    private final GlobalEventFlows globalEventFlows = KoinJavaComponent.get(GlobalEventFlows.class);

    private View scrollParent, noVotesView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (!isSessionScopeReady()) {
            finish();
        }
    }

    @Override
    protected boolean initActivity(@Nullable Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        int pollId = IntentDataUtil.getPollId(this.getIntent());

        if (pollId != 0) {
            try {
                PollModel pollModel = dependencies.getPollService().get(pollId);
                if (pollModel == null) {
                    throw new ThreemaException("invalid poll");
                }

                this.setPollModel(pollModel);
            } catch (ThreemaException e) {
                logger.error("Failed to init activity", e);
                showToast(this, R.string.an_error_occurred);
                finish();
                return false;
            }
        }

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (getPollModel().getState() == PollModel.State.CLOSED) {
                actionBar.setTitle(R.string.ballot_result_final);
            } else {
                actionBar.setTitle(R.string.ballot_result_intermediate);
            }
        }

        TextView textView = findViewById(R.id.text_view);
        if (textView != null && getPollModel().getName() != null) {
            textView.setText(this.getPollModel().getName());
        }

        noVotesView = findViewById(R.id.no_votes_yet);
        scrollParent = findViewById(R.id.scroll_parent);

        FlowJavaCompat.collect(this, Lifecycle.State.STARTED, globalEventFlows.getPolls(), this::handlePollEvent);
        this.updateView();

        return true;
    }

    @UiThread
    private void handlePollEvent(@NonNull PollEvent event) {
        if (getPollModel() == null || getPollModel().getId() != event.getPoll().getId()) {
            return;
        }
        if (event instanceof PollEvent.PollRemoved) {
            showToast(this, R.string.ballot_removed);
            finish();
        } else {
            updateView();
        }
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.scroll_parent),
            InsetSides.lbr()
        );
        ViewExtensionsKt.applyDeviceInsetsAsMargin(
            findViewById(R.id.no_votes_yet),
            InsetSides.horizontal()
        );
        ViewExtensionsKt.applyDeviceInsetsAsMargin(
            findViewById(R.id.avatar_container),
            InsetSides.horizontal(),
            SpacingValues.all(R.dimen.grid_unit_x2)
        );
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_poll_matrix;
    }

    private void updateView() {
        TableLayout dataTableLayout = findViewById(R.id.matrix_data);

        if (dataTableLayout == null) {
            logger.error("The data table layout is null");
            return;
        }

        dataTableLayout.removeAllViews();

        PollModel.DisplayType displayType = PollModel.DisplayType.LIST_MODE;

        final PollModel pollModel = dependencies.getPollService().get(this.getPollModelId());
        if (pollModel != null) {
            displayType = pollModel.getDisplayType();
        }

        final PollMatrixData matrixData = dependencies.getPollService().getMatrixData(this.getPollModelId());

        if (matrixData == null) {
            //wrong data! exit now
            Toast.makeText(this, "invalid data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<PollMatrixService.Participant> allParticipants = getAllParticipants(matrixData, displayType);
        List<PollMatrixService.Participant> votedParticipants = new ArrayList<>();
        List<PollMatrixService.Participant> notVotedParticipants = new ArrayList<>();

        for (PollMatrixService.Participant participant : allParticipants) {
            if (participant.hasVoted()) {
                votedParticipants.add(participant);
            } else {
                notVotedParticipants.add(participant);
            }
        }

        if (votedParticipants.isEmpty() && displayType == PollModel.DisplayType.LIST_MODE) {
            // no votes
            noVotesView.setVisibility(View.VISIBLE);
            scrollParent.setVisibility(View.GONE);
            return;
        }

        noVotesView.setVisibility(View.GONE);
        scrollParent.setVisibility(View.VISIBLE);

        dataTableLayout.addView(getHeaderRow(votedParticipants));

        for (PollMatrixService.Choice c : matrixData.getChoices()) {
            // create a new row for each answer
            TableRow row = new TableRow(this);

            // add answer first
            View headerCell = getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_choice_label, null);
            ((HintedTextView) headerCell.findViewById(R.id.choice_label)).setText(c.getPollChoiceModel().getName());
            row.addView(headerCell);

            // add sums
            View sumCell = getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_choice_sum, null);
            TextView sumText = sumCell.findViewById(R.id.voting_sum);

            sumText.setText(String.valueOf(c.getVoteCount()));

            if (c.isWinner()) {
                sumCell.findViewById(R.id.cell).setBackgroundResource(R.drawable.matrix_winner_cell);
                sumText.setTextColor(getResources().getColor(android.R.color.white));
            }

            row.addView(sumCell);

            for (PollMatrixService.Participant p : votedParticipants) {
                row.addView(getVotedParticipantView(matrixData, p, c));
            }

            dataTableLayout.addView(row);
        }

        TextView notVotedTextView = findViewById(R.id.not_voted);
        MaterialCardView notVotedContainer = findViewById(R.id.not_voted_container);

        if (!notVotedParticipants.isEmpty()) {
            notVotedContainer.setVisibility(View.VISIBLE);
            String userList = "";

            for (PollMatrixService.Participant p : notVotedParticipants) {
                if (!userList.isEmpty()) {
                    userList += ", ";
                }
                userList += NameUtil.getContactDisplayNameOrNickname(
                    p.getIdentity(),
                    dependencies.getContactService(),
                    dependencies.getPreferenceService().getContactNameFormat()
                );
            }
            notVotedTextView.setText(getString(R.string.not_voted_user_list, userList));
        } else {
            notVotedContainer.setVisibility(View.GONE);
        }
    }

    @NonNull
    private List<PollMatrixService.Participant> getAllParticipants(@NonNull PollMatrixData matrixData, @NonNull PollModel.DisplayType displayType) {
        List<PollMatrixService.Participant> allParticipants = matrixData.getParticipants();

        if (displayType == PollModel.DisplayType.SUMMARY_MODE) {
            for (PollMatrixService.Participant p : allParticipants) {
                if (dependencies.getUserService().isMe(p.getIdentity())) {
                    return Collections.singletonList(p);
                }
            }
        }

        return allParticipants;
    }

    @NonNull
    private TableRow getHeaderRow(@NonNull List<PollMatrixService.Participant> votedParticipants) {
        // add header row containing names/avatars of participants
        TableRow nameHeaderRow = new TableRow(this);

        getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_empty, nameHeaderRow);

        getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_empty, nameHeaderRow);

        for (PollMatrixService.Participant p : votedParticipants) {
            final ContactModel contactModel = dependencies.getContactService().getByIdentity(p.getIdentity());

            View nameCell = getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_name, null);
            String name = NameUtil.getContactDisplayNameOrNickname(contactModel, true, dependencies.getPreferenceService().getContactNameFormat());

            HintedImageView hintedImageView = nameCell.findViewById(R.id.avatar_view);
            if (hintedImageView != null) {
                hintedImageView.setContentDescription(name);

                Bitmap avatar = dependencies.getContactService().getAvatar(p.getIdentity(), false);
                hintedImageView.setImageBitmap(avatar);
            }

            nameHeaderRow.addView(nameCell);
        }

        return nameHeaderRow;
    }

    @NonNull
    private View getVotedParticipantView(@NonNull PollMatrixData matrixData, @NonNull PollMatrixService.Participant p, @NonNull PollMatrixService.Choice c) {
        View choiceVoteView;

        if (c.isWinner()) {
            choiceVoteView = getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_choice_winner, null);
        } else {
            choiceVoteView = getLayoutInflater().inflate(R.layout.row_cell_poll_matrix_choice, null);
        }

        PollVoteModel vote = matrixData.getVote(p, c);
        ViewUtil.show(
            (View) choiceVoteView.findViewById(R.id.voting_value_1),
            p.hasVoted() && vote != null && vote.getChoice() == 1);


        ViewUtil.show(
            (View) choiceVoteView.findViewById(R.id.voting_value_0),
            p.hasVoted() && (vote == null || vote.getChoice() != 1));

        ViewUtil.show(
            (View) choiceVoteView.findViewById(R.id.voting_value_none),
            !p.hasVoted());

        return choiceVoteView;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        setResult(RESULT_OK);
        finish();
    }
}
