package ch.threema.app.dialogs;

import android.os.Build;
import android.os.Bundle;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class ThreemaDialogFragment extends DialogFragment {
    private static final Logger logger = getThreemaLogger("ThreemaDialogFragment");

    public static final String BUNDLE_KEY_CLICKED_BUTTON = "clicked-button";

    protected @Nullable Object object;
    protected @NonNull Bundle requestData = new Bundle();

    enum ClickedButton {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        setRetainInstance(true);
    }

    /**
     * Shows a DialogFragment. Can be used from onActivityResult() without provoking "IllegalStateException: Can not perform this action after onSaveInstanceState"
     *
     * @param manager FragmentManager
     * @param tag     Arbitrary tag for this DialogFragment
     */
    @Override
    public void show(@Nullable FragmentManager manager, @Nullable String tag) {
        if (manager != null) {
            try {
                super.show(manager, tag);
            } catch (IllegalStateException e) {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitAllowingStateLoss();
            }
        }
    }

    /**
     * Shows a DialogFragment. Can be used from onActivityResult() without provoking "IllegalStateException: Can not perform this action after onSaveInstanceState"
     */
    public void show(@Nullable FragmentManager manager) {
        if (manager != null) {
            try {
                super.show(manager, null);
            } catch (IllegalStateException e) {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, null);
                ft.commitAllowingStateLoss();
            }
        }
    }

    /**
     * Immediately shows a DialogFragment. Can be used from onActivityResult() without provoking "IllegalStateException: Can not perform this action after onSaveInstanceState"
     *
     * @param manager FragmentManager
     * @param tag     Arbitrary tag for this DialogFragment
     */
    @Override
    public void showNow(@Nullable FragmentManager manager, String tag) {
        if (manager != null) {
            try {
                super.showNow(manager, tag);
            } catch (IllegalStateException e) {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitNowAllowingStateLoss();
            }
        }
    }

    public ThreemaDialogFragment setData(@NonNull Object data) {
        this.object = data;
        return this;
    }

    public ThreemaDialogFragment setRequestData(@NonNull Bundle bundle) {
        this.requestData = bundle;
        return this;
    }

    public static void onClickPositiveButton(@NonNull Bundle result, @NonNull Runnable onClick) {
        final @Nullable ClickedButton clickedButton = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ? result.getSerializable(BUNDLE_KEY_CLICKED_BUTTON, ClickedButton.class)
            : (ClickedButton) result.getSerializable(BUNDLE_KEY_CLICKED_BUTTON);
        if (clickedButton == ClickedButton.POSITIVE) {
            onClick.run();
        }
    }
}
