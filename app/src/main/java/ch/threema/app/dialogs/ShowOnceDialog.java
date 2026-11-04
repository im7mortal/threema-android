package ch.threema.app.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.preference.service.PreferenceService;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * A simple string dialog with a "don't show again" checkbox
 * If the checkbox has not previously been checked, the dialog will be shown, otherwise nothing will happen
 * Make sure to use a unique tag for this dialog in the show() method
 */
public class ShowOnceDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("ShowOnceDialog");

    public static final String RESULT_CONFIRMED = "confirmed";

    private final PreferenceService preferenceService = KoinJavaComponent.get(PreferenceService.class);

    public static final String ARG_TITLE = "title";
    public static final String ARG_MESSAGE_STRING = "messageString";
    public static final String ARG_MESSAGE_INT = "messageInt";
    public static final String ARG_ICON = "icon";

    @NonNull
    public static ShowOnceDialog newInstance(@StringRes int title, @StringRes int message) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE_INT, message);
        return newInstance(args);
    }

    @NonNull
    public static ShowOnceDialog newInstance(@StringRes int title, @StringRes int message, @DrawableRes int icon) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE_INT, message);
        args.putInt(ARG_ICON, icon);
        return newInstance(args);
    }

    @NonNull
    public static ShowOnceDialog newInstance(@StringRes int title, @NonNull String message) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_MESSAGE_STRING, message);
        return newInstance(args);
    }

    @NonNull
    private static ShowOnceDialog newInstance(@NonNull Bundle args) {
        final ShowOnceDialog dialog = new ShowOnceDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    // generally allow state loss for simple string alerts
    public void show(@Nullable FragmentManager manager, @Nullable String tag) {
        if (manager == null || tag == null) {
            return;
        }
        if (!preferenceService.isOneTimeDialogShown(tag)) {
            FragmentTransaction fragmentTransaction = manager.beginTransaction();
            fragmentTransaction.add(this, tag);
            fragmentTransaction.commitAllowingStateLoss();
        }
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        final @NonNull Bundle arguments = requireArguments();
        final @StringRes int title = arguments.getInt(ARG_TITLE);
        final @StringRes int messageInt = arguments.getInt(ARG_MESSAGE_INT);
        final @Nullable String messageString =
            (messageInt == 0)
                ? arguments.getString(ARG_MESSAGE_STRING)
                : null;
        final @DrawableRes int icon = arguments.getInt(ARG_ICON, 0);

        final @Nullable String tag = this.getTag();

        final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_show_once, null);
        final TextView textView = dialogView.findViewById(R.id.message);
        final MaterialCheckBox checkbox = dialogView.findViewById(R.id.checkbox);
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (tag != null) {
                preferenceService.setOneTimeDialogShown(tag, isChecked);
            }
        });

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme());
        builder.setView(dialogView);
        builder.setCancelable(false);

        if (title != -1) {
            builder.setTitle(title);
        }

        if (icon != 0) {
            builder.setIcon(icon);
        }

        if (tag != null) {
            builder.setPositiveButton(
                R.string.ok,
                (dialog, whichButton) -> {
                    preferenceService.setOneTimeDialogShown(tag, true);
                    emitResult(tag, true);
                }
            );
            builder.setNegativeButton(
                R.string.cancel,
                (dialog, whichButton) -> emitResult(tag, false)
            );
        }

        if (messageString != null) {
            textView.setText(messageString);
        } else {
            textView.setText(messageInt);
        }
        setCancelable(false);
        return builder.create();
    }

    private void emitResult(@NonNull String requestKey, boolean confirmed) {
        Bundle result = new Bundle();
        result.putBoolean(RESULT_CONFIRMED, confirmed);
        getParentFragmentManager().setFragmentResult(requestKey, result);
    }
}
