package ch.threema.app.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.slf4j.Logger;

import androidx.fragment.app.Fragment;
import ch.threema.app.R;

import static androidx.fragment.app.FragmentKt.setFragmentResult;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * A dialog with a title and a checkbox
 */
public class TextWithCheckboxDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("TextWithCheckboxDialog");
    private @Nullable TextWithCheckboxDialogClickListener callback;
    private Activity activity;

    private final static String TITLE_KEY = "title";
    private final static String MESSAGE_KEY = "message";
    private final static String MESSAGE_RES_KEY = "messageRes";
    private final static String CHECKBOX_LABEL_RES_KEY = "checkboxLabel";
    private final static String POSITIVE_BUTTON_RES_KEY = "positive";
    private final static String NEGATIVE_BUTTON_RES_KEY = "negative";
    private static final String ARG_ICON_RES = "icon";
    private static final String IS_CANCELABLE = "isCancelable";

    public static final String BUNDLE_KEY_REQUEST_KEY = "request-key";
    public static final String BUNDLE_KEY_IS_CHECKED = "is-checked";

    public interface TextWithCheckboxDialogClickListener {
        void onYes(@Nullable String tag, @Nullable Object data, boolean checked);
    }

    @NonNull
    public static TextWithCheckboxDialog newInstance(
        String title,
        @DrawableRes int icon,
        @NonNull String message,
        @StringRes int checkboxLabelRes,
        @StringRes int positiveButtonRes,
        @StringRes int negativeButtonRes,
        boolean isCancelable,
        @Nullable String requestKey
    ) {
        TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putString(MESSAGE_KEY, message);
        args.putInt(CHECKBOX_LABEL_RES_KEY, checkboxLabelRes);
        args.putInt(POSITIVE_BUTTON_RES_KEY, positiveButtonRes);
        args.putInt(NEGATIVE_BUTTON_RES_KEY, negativeButtonRes);
        args.putInt(ARG_ICON_RES, icon);
        args.putBoolean(IS_CANCELABLE, isCancelable);
        if (requestKey != null) {
            args.putString(BUNDLE_KEY_REQUEST_KEY, requestKey);
        }
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static TextWithCheckboxDialog newInstance(
        String title,
        @StringRes int messageRes,
        @StringRes int checkboxLabelRes,
        @StringRes int positiveButtonRes,
        @StringRes int negativeButtonRes,
        boolean isCancelable,
        @Nullable String requestKey
    ) {
        TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putInt(MESSAGE_RES_KEY, messageRes);
        args.putInt(CHECKBOX_LABEL_RES_KEY, checkboxLabelRes);
        args.putInt(POSITIVE_BUTTON_RES_KEY, positiveButtonRes);
        args.putInt(NEGATIVE_BUTTON_RES_KEY, negativeButtonRes);
        args.putBoolean(IS_CANCELABLE, isCancelable);
        if (requestKey != null) {
            args.putString(BUNDLE_KEY_REQUEST_KEY, requestKey);
        }
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static TextWithCheckboxDialog newInstance(
        String title,
        String message,
        @StringRes int checkboxLabelRes,
        @StringRes int positiveButtonRes,
        @StringRes int negativeButtonRes,
        boolean isCancelable,
        @Nullable String requestKey
    ) {
        TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putString(MESSAGE_KEY, message);
        args.putInt(CHECKBOX_LABEL_RES_KEY, checkboxLabelRes);
        args.putInt(POSITIVE_BUTTON_RES_KEY, positiveButtonRes);
        args.putInt(NEGATIVE_BUTTON_RES_KEY, negativeButtonRes);
        args.putBoolean(IS_CANCELABLE, isCancelable);
        if (requestKey != null) {
            args.putString(BUNDLE_KEY_REQUEST_KEY, requestKey);
        }
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (callback != null) {
            return;
        }

        // Check if the target fragment implements our callback
        final @Nullable Fragment targetFragment = getTargetFragment();
        if (targetFragment instanceof TextWithCheckboxDialogClickListener) {
            callback = (TextWithCheckboxDialogClickListener) targetFragment;
            return;
        }

        // called from an activity rather than a fragment
        if (activity instanceof TextWithCheckboxDialogClickListener) {
            callback = (TextWithCheckboxDialogClickListener) activity;
        }
    }

    public void setCallback(@Nullable TextWithCheckboxDialogClickListener callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        final @Nullable String title = requireArguments().getString(TITLE_KEY);
        final @Nullable String message = requireArguments().getString(MESSAGE_KEY);
        final @StringRes int messageRes = requireArguments().getInt(MESSAGE_RES_KEY, 0);
        final @StringRes int checkboxLabelRes = requireArguments().getInt(CHECKBOX_LABEL_RES_KEY);
        final @StringRes int positiveButtonRes = requireArguments().getInt(POSITIVE_BUTTON_RES_KEY);
        final @StringRes int negativeButtonRes = requireArguments().getInt(NEGATIVE_BUTTON_RES_KEY);
        final @DrawableRes int iconRes = requireArguments().getInt(ARG_ICON_RES, 0);
        final boolean isCancelable = requireArguments().getBoolean(IS_CANCELABLE, false);
        final @Nullable String requestKey = requireArguments().getString(BUNDLE_KEY_REQUEST_KEY);

        final @NonNull View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_text_with_checkbox, null);
        final @NonNull TextView messageTextView = dialogView.findViewById(R.id.message);
        final @NonNull MaterialSwitch checkbox = dialogView.findViewById(R.id.checkbox);

        final @Nullable String tag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme())
            .setTitle(title != null ? title : message)
            .setCancelable(false)
            .setNegativeButton(
                negativeButtonRes,
                (dialog, which) -> {
                    if (requestKey != null) {
                        setResult(requestKey, ClickedButton.NEGATIVE, checkbox.isChecked());
                    }
                }
            )
            .setPositiveButton(
                positiveButtonRes,
                (dialog, which) -> {
                    final boolean isCheckboxChecked = checkbox.isChecked();
                    if (callback != null) {
                        callback.onYes(tag, object, isCheckboxChecked);
                    }
                    if (requestKey != null) {
                        setResult(requestKey, ClickedButton.POSITIVE, isCheckboxChecked);
                    }
                }
            );

        if (iconRes != 0) {
            builder.setIcon(iconRes);
        }
        if (messageRes != 0) {
            messageTextView.setText(messageRes);
        } else if (message != null) {
            messageTextView.setText(message);
        } else {
            messageTextView.setVisibility(View.GONE);
        }
        if (checkboxLabelRes != 0) {
            checkbox.setText(checkboxLabelRes);
        } else {
            checkbox.setVisibility(View.GONE);
        }
        builder.setView(dialogView);
        setCancelable(isCancelable);
        return builder.create();
    }

    private void setResult(@NonNull String requestKey, @NonNull ClickedButton clickedButton, boolean isChecked) {
        final @NonNull Bundle resultBundle = new Bundle();
        resultBundle.putAll(requestData);
        resultBundle.putBoolean(BUNDLE_KEY_IS_CHECKED, isChecked);
        resultBundle.putSerializable(ThreemaDialogFragment.BUNDLE_KEY_CLICKED_BUTTON, clickedButton);
        setFragmentResult(TextWithCheckboxDialog.this, requestKey, resultBundle);
    }
}
