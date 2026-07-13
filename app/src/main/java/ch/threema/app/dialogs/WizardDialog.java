package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("WizardDialog");

    private static final String ARG_TITLE = "title";
    private static final String ARG_TITLE_STRING = "titleString";
    private static final String ARG_POSITIVE = "positive";
    private static final String ARG_NEGATIVE = "negative";

    private WizardDialogCallback callback;
    private Activity activity;

    @NonNull
    public static WizardDialog newInstance(@StringRes int title, @StringRes int positive, @StringRes int negative) {
        WizardDialog dialog = new WizardDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_POSITIVE, positive);
        args.putInt(ARG_NEGATIVE, negative);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static WizardDialog newInstance(@StringRes int title, @StringRes int positive) {
        WizardDialog dialog = new WizardDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_POSITIVE, positive);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static WizardDialog newInstance(@NonNull String title, @StringRes int positive) {
        WizardDialog dialog = new WizardDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE_STRING, title);
        args.putInt(ARG_POSITIVE, positive);
        dialog.setArguments(args);
        return dialog;
    }

    public interface WizardDialogCallback {
        void onYes(String tag, Object data);

        void onNo(String tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        try {
            callback = (WizardDialogCallback) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // called from an activity rather than a fragment
        if (callback == null) {
            if (!(activity instanceof WizardDialogCallback)) {
                throw new ClassCastException("Calling fragment must implement WizardDialogCallback interface");
            }
            callback = (WizardDialogCallback) activity;
        }
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {

        final @StringRes int titleResOrZero = requireArguments().getInt(ARG_TITLE, 0);
        final @Nullable String titleString = requireArguments().getString(ARG_TITLE_STRING);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());

        if (titleResOrZero != 0) {
            builder.setMessage(titleResOrZero);
        } else {
            builder.setMessage(titleString);
        }

        final @StringRes int positiveButtonResOrZero = requireArguments().getInt(ARG_POSITIVE, 0);
        if (positiveButtonResOrZero != 0) {
            builder.setPositiveButton(
                positiveButtonResOrZero,
                (dialog, v) -> callback.onYes(getTag(), object)
            );
        }

        final @StringRes int negativeButtonResOrZero = requireArguments().getInt(ARG_NEGATIVE, 0);
        if (negativeButtonResOrZero != 0) {
            builder.setNegativeButton(
                negativeButtonResOrZero,
                (dialog, v) -> callback.onNo(getTag())
            );
        }

        builder.setCancelable(false);
        setCancelable(false);
        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        callback.onNo(getTag());
    }
}
