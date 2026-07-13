package ch.threema.app.ui;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

import ch.threema.android.FlowJavaCompat;
import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.R;
import ch.threema.app.eventbus.GlobalEventFlows;
import ch.threema.app.eventbus.events.PollEvent;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.poll.PollService;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.PollUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.JavaCompat.isNullOrEmpty;

import ch.threema.domain.models.MessageId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.poll.PollModel;

/**
 * A view that shows all open polls for a chat in a ChipGroup and allows users to vote or close the poll
 */
public class OpenPollNoticeView extends ConstraintLayout implements DefaultLifecycleObserver {
    private static final Logger logger = getThreemaLogger("OpenPollNoticeView");
    private static final int MAX_POLLS_SHOWN = 20;
    private static final int MAX_POLL_TITLE_LENGTH = 20;

    @NonNull
    private final GlobalEventFlows globalEventFlows = KoinJavaComponent.get(GlobalEventFlows.class);

    private ChipGroup chipGroup;
    private final List<PollChipHolder> shownPolls = new LinkedList<>();
    private PollService pollService;
    private UserService userService;
    private PreferenceService preferenceService;
    private ContactService contactService;
    private String identity;
    private MessageReceiver<?> messageReceiver;
    private int numOpenPolls;
    private OnCloseClickedListener onCloseClickedListener;

    public OpenPollNoticeView(Context context) {
        super(context);
        init(context);
    }

    public OpenPollNoticeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OpenPollNoticeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        if (!(getContext() instanceof AppCompatActivity)) {
            return;
        }

        getActivity().getLifecycle().addObserver(this);

        try {
            var serviceManager = ServiceManager.require();
            pollService = serviceManager.getPollService();
            userService = serviceManager.getUserService();
            preferenceService = serviceManager.getPreferenceService();
            contactService = serviceManager.getContactService();
        } catch (Exception e) {
            logger.error("Exception", e);
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.notice_open_polls, this);

        getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);
        getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        getLayoutTransition().enableTransitionType(LayoutTransition.DISAPPEARING);
        getLayoutTransition().enableTransitionType(LayoutTransition.APPEARING);

        findViewById(R.id.button_cancel).setOnClickListener(v -> {
            if (onCloseClickedListener != null) {
                onCloseClickedListener.onCloseClicked();
            }
        });

        identity = userService.getIdentity();
    }

    @UiThread
    public void show(boolean animated) {
        if (getVisibility() != VISIBLE && numOpenPolls > 0 && !preferenceService.getPollOverviewHidden()) {
            if (animated) {
                Transition transition = new Fade();
                transition.setDuration(250);
                transition.addTarget(this);

                TransitionManager.endTransitions((ViewGroup) getParent());
                TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
            }
            setVisibility(VISIBLE);
        }
    }

    @UiThread
    public void hide(boolean animated) {
        if (getVisibility() != GONE) {
            if (animated) {
                Transition transition = new Fade();
                transition.setDuration(250);
                transition.addTarget(this);

                TransitionManager.endTransitions((ViewGroup) getParent());
                TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
            }
            setVisibility(GONE);
        }
    }

    @UiThread
    @SuppressLint("StaticFieldLeak")
    private void updatePollDisplay() {
        if (!PollUtil.canVote(messageReceiver)) {
            return;
        }

        new LifecycleAwareAsyncTask<Void, List<PollModel>>() {
            @Override
            protected List<PollModel> doInBackground(Void params) {
                try {
                    return pollService.getPolls(new PollService.PollFilter() {
                        @Override
                        public MessageReceiver<?> getReceiver() {
                            return messageReceiver;
                        }

                        @Override
                        public PollModel.State[] getStates() {
                            return new PollModel.State[]{PollModel.State.OPEN};
                        }

                        @Override
                        public String createdOrNotVotedByIdentity() {
                            return identity;
                        }
                    });
                } catch (NotAllowedException | IllegalStateException e) {
                    logger.error("Exception", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<PollModel> pollModels) {
                // Hide this view if there are no open polls (anymore)
                if (pollModels.isEmpty()) {
                    hide(false);
                    return;
                }

                int numPollsShown = 0;
                for (int i = 0; i < pollModels.size(); i++) {
                    if (shownPolls.size() > i) {
                        // Update the available chips if possible
                        shownPolls.get(i).updatePollModel(pollModels.get(i));
                    } else {
                        // Add new chips if there are not enough chips present
                        shownPolls.add(new PollChipHolder(pollModels.get(i)));
                    }
                    // Count the shown chips. Note that chips with invalid polls are not shown,
                    // but remain in this list in case an update makes them valid.
                    if (shownPolls.get(i).isShown()) {
                        numPollsShown++;
                    }
                    // Don't add more than limit
                    if (numPollsShown >= MAX_POLLS_SHOWN) {
                        break;
                    }
                }

                // Remove the last poll models
                for (int i = shownPolls.size() - 1; i >= pollModels.size(); i--) {
                    PollChipHolder removedHolder = shownPolls.remove(i);
                    removedHolder.remove();
                }

                OpenPollNoticeView.this.numOpenPolls = numPollsShown;

                if (numPollsShown > 0) {
                    show(false);
                }
            }
        }.execute(getActivity(), null);
    }

    public void setMessageReceiver(@NonNull MessageReceiver<?> messageReceiver) {
        this.messageReceiver = messageReceiver;
        updatePollDisplay();
    }

    public void update() {
        updatePollDisplay();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        this.chipGroup = findViewById(R.id.chip_group);
        this.chipGroup.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);
        this.chipGroup.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        this.chipGroup.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        this.chipGroup.getLayoutTransition().enableTransitionType(LayoutTransition.DISAPPEARING);
        this.chipGroup.getLayoutTransition().enableTransitionType(LayoutTransition.APPEARING);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        FlowJavaCompat.collect(owner, Lifecycle.State.STARTED, globalEventFlows.getPolls(), this::handlePollEvent);
    }

    @UiThread
    private void handlePollEvent(@NonNull PollEvent event) {
        if (!pollService.belongsToMe(event.getPoll().getId(), messageReceiver)) {
            return;
        }
        if (event instanceof PollEvent.PollVoted) {
            // There is no need to update the chips if the vote has been changed. However, update
            // the view when a first vote has been received as this may change the vote counter.
            if (((PollEvent.PollVoted) event).isNewVote()) {
                updatePollDisplay();
            }
        } else {
            updatePollDisplay();
        }
    }

    @SuppressLint("RestrictedApi")
    public void onChipClick(@NonNull View v, @Nullable PollModel pollModel, boolean isVoteComplete) {
        if (pollModel != null) {
            MenuBuilder menuBuilder = new MenuBuilder(getContext());
            new MenuInflater(getContext()).inflate(R.menu.chip_open_polls, menuBuilder);

            // Set all icon colors, as MenuInflater ignores specified iconTint xml attribute
            for (int i = 0; i < menuBuilder.size(); i++) {
                final @NonNull MenuItem menuItem = menuBuilder.getItem(i);
                ConfigUtils.tintMenuIcon(getContext(), menuItem, R.attr.colorOnSurface);
            }

            if (PollUtil.canViewMatrix(pollModel)) {
                menuBuilder.findItem(R.id.menu_poll_results).setTitle(pollModel.getState() == PollModel.State.CLOSED ? R.string.ballot_result_final : R.string.ballot_result_intermediate);
            }

            final @Nullable MenuItem highlightItem;
            if (isVoteComplete) {
                highlightItem = menuBuilder.findItem(R.id.menu_poll_close);
            } else if (pollService.hasVoted(pollModel.getId(), userService.getIdentity())) {
                highlightItem = menuBuilder.findItem(R.id.menu_poll_results);
            } else {
                highlightItem = menuBuilder.findItem(R.id.menu_poll_vote);
            }
            if (highlightItem != null) {
                @ColorInt int highlightColor = ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary);
                SpannableString highlightItemSpannable = new SpannableString(highlightItem.getTitle());
                highlightItemSpannable.setSpan(new ForegroundColorSpan(highlightColor), 0, highlightItemSpannable.length(), 0);
                highlightItem.setTitle(highlightItemSpannable);
                ConfigUtils.tintMenuIcon(highlightItem, highlightColor);
            }

            menuBuilder.setCallback(new MenuBuilder.Callback() {
                @Override
                public boolean onMenuItemSelected(@NonNull MenuBuilder menu, @NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.menu_poll_vote) {
                        vote(pollModel);
                    } else if (id == R.id.menu_poll_results) {
                        PollUtil.openMatrixActivity(getContext(), pollModel);
                    } else if (id == R.id.menu_poll_close) {
                        close(pollModel);
                    } else if (id == R.id.menu_poll_delete) {
                        delete(pollModel);
                    }
                    return true;
                }

                @Override
                public void onMenuModeChange(@NonNull MenuBuilder menu) {
                    // nothing to do
                }
            });

            if (!PollUtil.canVote(pollModel, messageReceiver)) {
                menuBuilder.removeItem(R.id.menu_poll_vote);
            }

            if (!PollUtil.canViewMatrix(pollModel)) {
                menuBuilder.removeItem(R.id.menu_poll_results);
            }

            if (!PollUtil.canClose(pollModel, identity, messageReceiver)) {
                menuBuilder.removeItem(R.id.menu_poll_close);
            }

            MenuPopupHelper optionsMenu = new MenuPopupHelper(getContext(), menuBuilder, v);
            optionsMenu.setForceShowIcon(true);
            optionsMenu.show();
        }
    }

    private void vote(PollModel model) {
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();

        if (PollUtil.canVote(model, messageReceiver)) {
            PollUtil.openVoteDialog(fragmentManager, model);
        }
    }

    private void close(PollModel model) {
        if (PollUtil.canClose(model, identity, messageReceiver)) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.ballot_close)
                .setMessage(R.string.ballot_really_close)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PollUtil.closePoll(getActivity(), model, pollService, MessageId.random(), TriggerSource.LOCAL);
                    }
                });
            builder.create().show();
        }
    }

    private void delete(PollModel model) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
            .setTitle(R.string.single_ballot_really_delete)
            .setMessage(getContext().getString(R.string.single_ballot_really_delete_text))
            .setNegativeButton(R.string.no, null)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                try {
                    pollService.remove(model);
                    update();
                } catch (NotAllowedException e) {
                    logger.error("Failed to delete poll", e);
                }
            });
        builder.create().show();
    }

    private AppCompatActivity getActivity() {
        return (AppCompatActivity) getContext();
    }

    public void setOnCloseClickedListener(OnCloseClickedListener listener) {
        onCloseClickedListener = listener;
    }

    private class PollChipHolder {
        @NonNull
        private PollModel poll;
        private final Chip chip;
        private boolean isShown = true;
        private int displayedVotes = -1;
        private int displayedParticipants = -1;

        private final Animation animation = new RotateAnimation(
            -3f,
            3,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        );

        private PollChipHolder(@NonNull PollModel pollModel) {
            this.poll = pollModel;
            this.chip = createChip();

            chipGroup.addView(this.chip);

            animation.setDuration(50);
            animation.setRepeatCount(4);
            animation.setRepeatMode(Animation.REVERSE);

            show();
        }

        private void updatePollModel(@NonNull PollModel pollModel) {
            boolean isAnotherPoll = poll.getId() != pollModel.getId();
            this.poll = pollModel;
            if (isAnotherPoll) {
                show();
            } else {
                updateName();
                setColor(PollUtil.isMine(poll, userService.getIdentity()), displayedVotes, displayedParticipants);
            }
        }

        @NonNull
        private Chip createChip() {
            Chip pollChip = new Chip(getContext());

            ChipDrawable chipDrawable = ChipDrawable.createFromAttributes(getContext(),
                null,
                0,
                R.style.Threema_Chip_ChatNotice_Overview);
            pollChip.setChipDrawable(chipDrawable);
            pollChip.setTextAppearance(R.style.Threema_TextAppearance_Chip_ChatNotice);
            pollChip.setTextEndPadding(getResources().getDimensionPixelSize(R.dimen.chip_end_padding_text_only));

            return pollChip;
        }

        private void show() {
            chip.setVisibility(View.VISIBLE);

            int votes = pollService.getVotedParticipants(poll.getId()).size();
            int participants = pollService.getParticipants(poll.getId()).length;
            if (participants == 0) {
                displayedVotes = -1;
                displayedParticipants = -1;
                chip.setVisibility(View.GONE);
                isShown = false;
                return;
            }

            displayedVotes = votes;
            displayedParticipants = participants;

            chip.setOnClickListener((View v) -> OpenPollNoticeView.this.onChipClick(v, poll, votes == participants));

            boolean isMine = PollUtil.isMine(poll, userService.getIdentity());

            chip.setText(getText(isMine, votes, participants));

            setAvatar();

            setColor(isMine, votes, participants);
        }

        private void updateName() {
            int votes = pollService.getVotedParticipants(poll.getId()).size();
            int participants = pollService.getParticipants(poll.getId()).length;
            chip.setText(getText(PollUtil.isMine(poll, userService.getIdentity()), votes, participants));
            if (votes > displayedVotes && participants == displayedParticipants) {
                // Animate view when the number of votes increased
                chip.setAnimation(animation);
            }
            displayedVotes = votes;
            displayedParticipants = participants;
        }

        private void remove() {
            chipGroup.removeView(chip);
        }

        private boolean isShown() {
            return isShown;
        }

        @SuppressLint("DefaultLocale")
        @NonNull
        private String getText(boolean isMine, int votes, int participants) {
            String name = poll.getName();

            if (isNullOrEmpty(name)) {
                name = getContext().getString(R.string.ballot_placeholder);
            } else {
                if (name.length() > MAX_POLL_TITLE_LENGTH) {
                    name = name.substring(0, MAX_POLL_TITLE_LENGTH);
                    name += "…";
                }
            }
            if (isMine) {
                return String.format("%s (%d/%d)", name, votes, participants);
            } else {
                return name;
            }
        }

        private void setAvatar() {
            new LifecycleAwareAsyncTask<Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Void params) {
                    Bitmap bitmap = contactService.getAvatar(poll.getCreatorIdentity(), false);
                    if (bitmap != null) {
                        return BitmapUtil.replaceTransparency(bitmap, Color.WHITE);
                    }
                    return null;
                }

                @Deprecated
                @Override
                protected void onPostExecute(Bitmap avatar) {
                    if (avatar != null) {
                        chip.setChipIcon(AvatarConverterUtil.convertToRound(getResources(), avatar));
                    } else {
                        chip.setChipIconResource(R.drawable.ic_vote_outline);
                    }
                }
            }.execute(getActivity(), null);
        }

        private void setColor(boolean isMine, int voters, int participants) {
            ColorStateList foregroundColor, backgroundColor;

            if (isMine && voters == participants) {
                // all votes are in
                foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSecondaryContainer));
                backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorSecondaryContainer));
            } else {
                foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnPrimary));
                backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary));
            }

            chip.setTextColor(foregroundColor);
            chip.setChipBackgroundColor(backgroundColor);
        }
    }

    public interface OnCloseClickedListener {
        void onCloseClicked();
    }
}
