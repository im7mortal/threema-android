package ch.threema.app.activities.poll;

import org.koin.java.KoinJavaComponent;

import androidx.annotation.NonNull;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.services.poll.PollService;
import ch.threema.storage.models.poll.PollModel;

abstract class PollDetailActivity extends ThreemaToolbarActivity {

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private PollModel pollModel = null;

    interface ServiceCall {
        void call(PollService service);
    }

    protected boolean setPollModel(final PollModel pollModel) {
        this.pollModel = pollModel;
        this.updateViewState();

        return this.pollModel != null;
    }

    protected PollModel getPollModel() {
        return this.pollModel;
    }

    protected Integer getPollModelId() {
        if (this.pollModel != null) {
            return this.pollModel.getId();
        }

        return null;
    }

    private void updateViewState() {
        if (this.pollModel != null) {
            this.callService(service -> {
                service.viewingPoll(pollModel, true);
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.pollModel != null) {
            this.callService(service -> {
                service.viewingPoll(pollModel, true);
            });
        }
    }

    @Override
    public void onPause() {
        if (this.pollModel != null) {
            this.callService(service -> {
                service.viewingPoll(pollModel, false);
            });
        }
        super.onPause();
    }

    private void callService(ServiceCall serviceCall) {
        serviceCall.call(dependencies.getPollService());
    }
}
