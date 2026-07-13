package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;


import static androidx.fragment.app.FragmentKt.setFragmentResult;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.JavaCompat.isNullOrEmpty;

public class GenericAlertDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("GenericAlertDialog");

    private @Nullable DialogClickListener callback;
    private Activity activity;

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int positive,
        @StringRes int negative
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int positive,
        @StringRes int negative,
        @Nullable String requestKey
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        if (requestKey != null) {
            args.putString("requestKey", requestKey);
        }

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int positive,
        @StringRes int negative,
        @StringRes int neutral,
        @DrawableRes int icon
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putInt("neutral", neutral);
        args.putInt("icon", icon);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int positive,
        @StringRes int negative,
        @DrawableRes int icon
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putInt("icon", icon);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int positive,
        @StringRes int negative,
        boolean cancelable
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("cancelable", cancelable);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        String messageString,
        @StringRes int positive,
        @StringRes int negative,
        boolean cancelable
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putString("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("cancelable", cancelable);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        @StringRes int title,
        CharSequence messageString,
        @StringRes int positive,
        @StringRes int negative
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putCharSequence("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        String titleString,
        CharSequence messageString,
        @StringRes int positive,
        @StringRes int negative
    ) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putString("titleString", titleString);
        args.putCharSequence("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static GenericAlertDialog newInstance(
        String titleString,
        CharSequence messageString,
        @StringRes int positive,
        @StringRes int negative,
        @StringRes int neutral
    ) {
        GenericAlertDialog dialog = newInstance(titleString, messageString, positive, negative);
        if (dialog.getArguments() != null) {
            dialog.getArguments().putInt("neutral", neutral);
        }
        return dialog;
    }


    public interface DialogClickListener {
        void onYes(@Nullable String tag, @Nullable Object data);

        default void onNo(@Nullable String tag, @Nullable Object data) {
        }

        default void onNeutral(@Nullable String tag, @Nullable Object data) {
            // optional interface
        }
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
        if (targetFragment instanceof DialogClickListener) {
            callback = (DialogClickListener) targetFragment;
            return;
        }

        // called from an activity rather than a fragment
        if (activity instanceof DialogClickListener) {
            callback = (DialogClickListener) activity;
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
        int title = requireArguments().getInt("title");
        String titleString = requireArguments().getString("titleString");
        int message = requireArguments().getInt("message");
        CharSequence messageString = requireArguments().getCharSequence("messageString");
        int positive = requireArguments().getInt("positive");
        int negative = requireArguments().getInt("negative");
        int neutral = requireArguments().getInt("neutral");
        @DrawableRes int icon = requireArguments().getInt("icon", 0);
        boolean cancelable = requireArguments().getBoolean("cancelable", true);
        final @Nullable String requestKey = requireArguments().getString("requestKey");
        final @Nullable String tag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());

        if (isNullOrEmpty(titleString)) {
            if (title != 0) {
                builder.setTitle(title);
            }
        } else {
            builder.setTitle(titleString);
        }
        if (TextUtils.isEmpty(messageString)) {
            if (message != 0) {
                builder.setMessage(message);
            }
        } else {
            builder.setMessage(messageString);
        }

        builder.setPositiveButton(
            getString(positive),
            (dialog, whichButton) -> {
                if (callback != null) {
                    callback.onYes(tag, object);
                }
                if (requestKey != null) {
                    final @NonNull Bundle resultBundle = new Bundle();
                    resultBundle.putAll(requestData);
                    resultBundle.putSerializable(ThreemaDialogFragment.BUNDLE_KEY_CLICKED_BUTTON, ClickedButton.POSITIVE);
                    setFragmentResult(GenericAlertDialog.this, requestKey, resultBundle);
                }
            }
        );
        if (negative != 0) {
            builder.setNegativeButton(
                getString(negative),
                (dialog, whichButton) -> {
                    if (callback != null) {
                        callback.onNo(tag, object);
                    }
                    if (requestKey != null) {
                        final @NonNull Bundle resultBundle = new Bundle();
                        resultBundle.putAll(requestData);
                        resultBundle.putSerializable(ThreemaDialogFragment.BUNDLE_KEY_CLICKED_BUTTON, ClickedButton.NEGATIVE);
                        setFragmentResult(GenericAlertDialog.this, requestKey, resultBundle);
                    }
                }
            );
        }

        if (neutral != 0) {
            builder.setNeutralButton(
                getString(neutral),
                (dialog, whichButton) -> {
                    if (callback != null) {
                        callback.onNeutral(tag, object);
                    }
                    if (requestKey != null) {
                        final @NonNull Bundle resultBundle = new Bundle();
                        resultBundle.putAll(requestData);
                        resultBundle.putSerializable(ThreemaDialogFragment.BUNDLE_KEY_CLICKED_BUTTON, ClickedButton.NEUTRAL);
                        setFragmentResult(GenericAlertDialog.this, requestKey, resultBundle);
                    }
                }
            );
            cancelable = false;
        }

        if (icon != 0) {
            builder.setIcon(icon);
        }

        final @NonNull AlertDialog alertDialog = builder.create();

        if (!cancelable) {
            setCancelable(false);
        }

        return alertDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        if (callback != null) {
            callback.onNo(getTag(), object);
        }
    }

    /**
     * @deprecated Specify request keys and use a {@code FragmentResultListener} instead.
     */
    @Deprecated
    public GenericAlertDialog setTargetFragment(@Nullable Fragment fragment) {
        setTargetFragment(fragment, 0);
        return this;
    }

    /**
     * Set the callback of this dialog.
     * <br><br>
     * <b>Warning:</b> It does not handle the case of a recreated fragment. Specify a request key and use a {@code FragmentResultListener} instead.
     */
    // TODO(ANDR-4620): Remove, as this is not lifecycle-aware
    public void setCallback(@NonNull DialogClickListener dialogClickListener) {
        this.callback = dialogClickListener;
    }
}

