package ch.threema.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public abstract class DialogUtil {
    private static final Logger logger = getThreemaLogger("DialogUtil");

    public static void dismissDialog(@Nullable FragmentManager fragmentManager, String tag, boolean allowStateLoss) {
        logger.debug("dismissDialog: {}", tag);

        if (fragmentManager == null) {
            return;
        }

        DialogFragment dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);

        if (dialogFragment == null && !fragmentManager.isDestroyed()) {
            // make sure dialogfragment is really shown before removing it
            try {
                fragmentManager.executePendingTransactions();
            } catch (IllegalStateException e) {
                // catch illegal state exception
            }
            dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
        }

        if (dialogFragment != null) {
            if (allowStateLoss) {
                try {
                    dialogFragment.dismissAllowingStateLoss();
                } catch (Exception e) {
                    // catch illegal state exception
                }
            } else {
                try {
                    dialogFragment.dismiss();
                } catch (Exception e) {
                    // catch illegal state exception
                }
            }
        }
    }

    @UiThread
    public static void updateProgress(FragmentManager fragmentManager, String tag, int progress) {
        if (fragmentManager != null) {
            DialogFragment dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
            if (dialogFragment instanceof CancelableHorizontalProgressDialog) {
                CancelableHorizontalProgressDialog progressDialog = (CancelableHorizontalProgressDialog) dialogFragment;
                progressDialog.setProgress(progress);
            }
        }
    }

    @NonNull
    public static ColorStateList getButtonColorStateList(@NonNull Context context) {
        // Fix for appcompat bug. Set button text color from theme
        final @NonNull TypedArray typedArray = context.getTheme().obtainStyledAttributes(
            new int[]{R.attr.colorPrimary}
        );
        try (typedArray) {
            final @ColorInt int accentColor = typedArray.getColor(0, 0);
            // You can't have attrs in XML color-state-lists :-(
            return new ColorStateList(
                new int[][]{
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{}
                },
                new int[]{
                    context.getResources().getColor(R.color.material_grey_400, context.getTheme()),
                    accentColor,
                }
            );
        }
    }
}
