package ch.threema.app.activities.poll;

import android.content.Intent;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.Lifecycle;
import ch.threema.android.FlowJavaCompat;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.adapters.poll.PollOverviewListAdapter;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.eventbus.GlobalEventFlows;
import ch.threema.app.eventbus.events.PollEvent;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.poll.PollService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.PollUtil;
import ch.threema.app.utils.IntentDataUtil;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.poll.PollModel;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PollOverviewActivity extends ThreemaToolbarActivity implements ListView.OnItemClickListener, GenericAlertDialog.DialogClickListener, SelectorDialog.SelectorDialogClickListener {
    private static final Logger logger = getThreemaLogger("PollOverviewActivity");

    private static final String DIALOG_TAG_POLL_DELETE = "bd";
    private static final String DIALOG_TAG_CHOOSE_ACTION = "ca";
    private static final int SELECTOR_ID_VOTE = 1;
    private static final int SELECTOR_ID_RESULTS = 2;
    private static final int SELECTOR_ID_CLOSE = 3;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);
    @NonNull
    private final GlobalEventFlows globalEventFlows = KoinJavaComponent.get(GlobalEventFlows.class);

    private MessageReceiver<?> messageReceiver;
    private PollOverviewListAdapter listAdapter = null;
    private List<PollModel> polls;
    private ListView listView;

    private ActionMode actionMode = null;
    private boolean pollEventHandlingEnabled = true;

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(android.R.id.list),
            InsetSides.lbr()
        );
    }

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

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.ballot_overview);

        listView = this.findViewById(android.R.id.list);
        listView.setOnItemClickListener(this);
        EmptyView emptyView = new EmptyView(this);
        emptyView.setup(getString(R.string.ballot_no_ballots_yet));
        ((ViewGroup) listView.getParent()).addView(emptyView);
        listView.setEmptyView(emptyView);

        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        appBarLayout.setLiftable(true);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                boolean isAtTop = firstVisibleItem == 0 && (view.getChildCount() == 0 || view.getChildAt(0).getTop() == 0);
                appBarLayout.setLifted(!isAtTop);
            }
        });

        Intent receivedIntent = getIntent();

        this.messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, receivedIntent);
        if (this.messageReceiver == null) {
            logger.error("cannot instantiate receiver");
            finish();
            return false;
        }

        this.setupList();
        this.updateList();

        FlowJavaCompat.collect(this, Lifecycle.State.STARTED, globalEventFlows.getPolls(), this::handlePollEvent);

        return true;
    }

    @UiThread
    private void handlePollEvent(@NonNull PollEvent event) {
        if (
            pollEventHandlingEnabled &&
                messageReceiver != null &&
                dependencies.getPollService().belongsToMe(event.getPoll().getId(), messageReceiver)
        ) {
            updateList();
        }
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_list_toolbar;
    }

    private void setupList() {
        final ListView listView = this.listView;

        if (listView != null) {
            listView.setDividerHeight(0);
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    view.setSelected(true);
                    listView.setItemChecked(position, true);
                    listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                    actionMode = startSupportActionMode(new MessageSectionAction());

                    return true;
                }
            });
        }
    }

    private void updateList() {
        try {
            this.polls = dependencies.getPollService().getPolls(new PollService.PollFilter() {
                @Override
                public MessageReceiver getReceiver() {
                    return messageReceiver;
                }

                @Override
                public PollModel.State[] getStates() {
                    return null;
                }
            });

            if (this.polls != null) {
                this.listAdapter = new PollOverviewListAdapter(
                    this,
                    this.polls,
                    messageReceiver,
                    dependencies.getPollService(),
                    dependencies.getContactService(),
                    dependencies.getUserService(),
                    dependencies.getPreferenceService(),
                    Glide.with(this)
                );

                listView.setAdapter(this.listAdapter);
            }
        } catch (NotAllowedException e) {
            logger.error("Exception", e);
            finish();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (this.listAdapter == null) {
            return;
        }

        if (actionMode == null) {
            this.deselectItem();

            PollModel pollModel = listAdapter.getItem(position);

            if (pollModel != null) {
                ArrayList<SelectorDialogItem> items = new ArrayList<>(3);
                ArrayList<Integer> values = new ArrayList<>(3);

                if (PollUtil.canVote(pollModel, messageReceiver)) {
                    items.add(new SelectorDialogItem(getString(R.string.ballot_vote), R.drawable.ic_vote_outline));
                    values.add(SELECTOR_ID_VOTE);
                }
                if (PollUtil.canViewMatrix(pollModel)) {
                    items.add(new SelectorDialogItem(getString(pollModel.getState() == PollModel.State.CLOSED ? R.string.ballot_result_final : R.string.ballot_result_intermediate), R.drawable.ic_poll_outline));
                    values.add(SELECTOR_ID_RESULTS);
                }
                if (PollUtil.canClose(pollModel, dependencies.getUserService().getIdentity(), messageReceiver)) {
                    items.add(new SelectorDialogItem(getString(R.string.ballot_close), R.drawable.ic_check));
                    values.add(SELECTOR_ID_CLOSE);
                }

                if (items.size() == 1) {
                    PollUtil.openDefaultActivity(this, getSupportFragmentManager(), pollModel, messageReceiver);
                } else if (!items.isEmpty()) {
                    SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, values, null, null);
                    selectorDialog.setData(pollModel);
                    selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_CHOOSE_ACTION);
                }
            }
        } else {
            // invalidate menu to update display => onPrepareActionMode()
            final int checked = listView.getCheckedItemCount();

            if (checked > 0) {
                actionMode.invalidate();
            } else {
                actionMode.finish();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }

        return true;
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

    private void deselectItem() {
        if (listView != null) {
            listView.clearChoices();
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            listView.requestLayout();
        }
    }

    private int getFirstCheckedPosition(ListView listView) {
        SparseBooleanArray checked = listView.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                return checked.keyAt(i);
            }
        }
        return AbsListView.INVALID_POSITION;
    }

    private void removeSelectedPolls() {
        final SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
        final int numCheckedItems = listView.getCheckedItemCount();

        GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            getResources().getQuantityString(R.plurals.ballot_really_delete, numCheckedItems, numCheckedItems),
            getResources().getQuantityString(R.plurals.ballot_really_delete_text, numCheckedItems, numCheckedItems),
            R.string.ok,
            R.string.cancel
        );
        dialog.setData(checkedItems);
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_POLL_DELETE);
    }

    private void removeSelectedPollsDo(SparseBooleanArray checkedItems) {
        synchronized (this.polls) {
            pollEventHandlingEnabled = false;
            for (int i = 0; i < checkedItems.size(); i++) {
                if (checkedItems.valueAt(i)) {

                    final int index = checkedItems.keyAt(i);
                    if (index >= 0 && index < this.polls.size()) {
                        try {
                            dependencies.getPollService().remove(this.polls.get(index));
                        } catch (NotAllowedException e) {
                            logger.error("Failed to delete poll", e);
                            showToast(this, R.string.an_error_occurred);
                            return;
                        }
                    }

                }
            }
            pollEventHandlingEnabled = true;
        }

        if (actionMode != null) {
            actionMode.finish();
        }

        this.updateList();
    }

    @Override
    public void onYes(@Nullable String tag, @Nullable Object data) {
        if (tag == null) {
            return;
        }
        if (tag.equals(DIALOG_TAG_POLL_DELETE)) {
            removeSelectedPollsDo((SparseBooleanArray) data);
        } else if (tag.equals(AppConstants.CONFIRM_TAG_CLOSE_POLL)) {
            PollUtil.closePoll(this, (PollModel) data, dependencies.getPollService(), MessageId.random(), TriggerSource.LOCAL);
        }
    }

    @Override
    public void onClick(String tag, int which, Object data) {
        final PollModel pollModel = (PollModel) data;

        switch (which) {
            case SELECTOR_ID_VOTE:
                PollUtil.openVoteDialog(this.getSupportFragmentManager(), pollModel);
                break;
            case SELECTOR_ID_RESULTS:
                PollUtil.openMatrixActivity(this, pollModel);
                break;
            case SELECTOR_ID_CLOSE:
                PollUtil.requestClosePoll(pollModel, null, this);
                break;
        }
    }

    public class MessageSectionAction implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.action_poll_overview, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final int checked = listView.getCheckedItemCount();
            final int firstCheckedItem = getFirstCheckedPosition(listView);

            if (firstCheckedItem == AbsListView.INVALID_POSITION) {
                return false;
            }

            mode.setTitle(String.format(getString(R.string.num_items_sected), Integer.toString(checked)));
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            final int firstCheckedItem = getFirstCheckedPosition(listView);

            if (firstCheckedItem == AbsListView.INVALID_POSITION) {
                return false;
            }

            if (item.getItemId() == R.id.menu_poll_remove) {
                removeSelectedPolls();
                return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            deselectItem();
        }
    }

}
