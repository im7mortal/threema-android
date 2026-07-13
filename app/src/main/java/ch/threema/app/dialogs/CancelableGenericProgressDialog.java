package ch.threema.app.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;

import static androidx.fragment.app.FragmentKt.setFragmentResult;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class CancelableGenericProgressDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("CancelableGenericProgressDialog");

    @NonNull
    public static CancelableGenericProgressDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int button,
        @Nullable String requestKey
    ) {
        CancelableGenericProgressDialog dialog = new CancelableGenericProgressDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("button", button);
        if (requestKey != null) {
            args.putString("requestKey", requestKey);
        }

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        int title = requireArguments().getInt("title");
        int message = requireArguments().getInt("message");
        int button = requireArguments().getInt("button");
        final @Nullable String requestKey = requireArguments().getString("requestKey");

        final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_progress_generic, null);
        TextView textView = dialogView.findViewById(R.id.text);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme());
        builder.setCancelable(false);
        builder.setView(dialogView);

        if (title != 0) {
            builder.setTitle(title);
        }
        if (message != 0) {
            textView.setText(message);
        }

        builder.setPositiveButton(
            getString(button),
            (dialog, whichButton) -> {
                if (requestKey != null) {
                    final @NonNull Bundle resultBundle = new Bundle();
                    resultBundle.putAll(requestData);
                    resultBundle.putSerializable(ThreemaDialogFragment.BUNDLE_KEY_CLICKED_BUTTON, ClickedButton.POSITIVE);
                    setFragmentResult(
                        CancelableGenericProgressDialog.this,
                        requestKey,
                        resultBundle
                    );
                }
            }
        );

        final @NonNull AlertDialog alertDialog = builder.create();
        setCancelable(false);
        return alertDialog;
    }
}
