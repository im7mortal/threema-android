package ch.threema.app.activities.poll;

import android.content.Intent;
import android.os.Bundle;
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

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.List;

import androidx.lifecycle.Lifecycle;
import ch.threema.android.FlowJavaCompat;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.adapters.poll.PollOverviewListAdapter;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.eventbus.GlobalEventFlows;
import ch.threema.app.eventbus.events.PollEvent;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.poll.PollService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.IntentDataUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.poll.PollModel;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PollChooserActivity extends ThreemaToolbarActivity implements ListView.OnItemClickListener {
    private static final Logger logger = getThreemaLogger("PollChooserActivity");

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);
    @NonNull
    private final GlobalEventFlows globalEventFlows = KoinJavaComponent.get(GlobalEventFlows.class);

    private PollOverviewListAdapter listAdapter = null;
    private ListView listView;

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

        listView = this.findViewById(android.R.id.list);
        listView.setOnItemClickListener(this);
        listView.setDividerHeight(0);

        // add text view if list is empty
        EmptyView emptyView = new EmptyView(this);
        emptyView.setup(R.string.ballot_no_ballots_yet);
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

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.ballot_copy);
        } else {
            setTitle(R.string.ballot_copy);
        }

        this.setupList();
        this.updateList();

        FlowJavaCompat.collect(this, Lifecycle.State.STARTED, globalEventFlows.getPolls(), this::handlePollEvent);

        return true;
    }

    @UiThread
    private void handlePollEvent(@NonNull PollEvent event) {
        updateList();
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(android.R.id.list),
            InsetSides.lbr()
        );
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_list_toolbar;
    }

    private void setupList() {
        final ListView listView = this.listView;

        if (listView != null) {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }
    }

    private void updateList() {
        try {
            List<PollModel> polls = dependencies.getPollService().getPolls(new PollService.PollFilter() {
                @Override
                public MessageReceiver<?> getReceiver() {
                    return null;
                }

                @Override
                public PollModel.State[] getStates() {
                    return null;
                }
            });

            if (polls != null) {
                this.listAdapter = new PollOverviewListAdapter(
                    this,
                    polls,
                    null,
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

        PollModel b = listAdapter.getItem(position);

        if (b != null) {
            Intent resultIntent = this.getIntent();
            //append poll
            IntentDataUtil.append(b, this.getIntent());

            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
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
        setResult(RESULT_CANCELED);
        finish();
    }
}
