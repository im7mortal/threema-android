package ch.threema.app.activities.poll;


import androidx.fragment.app.Fragment;

abstract class PollWizardFragment extends Fragment {
    private PollWizardActivity pollWizardActivity = null;

    /**
     * update the data fields
     */
    abstract void updateView();

    /**
     * cast activity to pollActivity
     */
    public PollWizardActivity getPollActivity() {
        if (this.pollWizardActivity == null) {
            if (super.getActivity() instanceof PollWizardActivity) {
                this.pollWizardActivity = (PollWizardActivity) this.getActivity();
            }
        }

        return this.pollWizardActivity;
    }
}
