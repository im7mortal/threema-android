package ch.threema.app.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import java.util.ArrayList;

import ch.threema.app.R;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.ui.SelectorDialogItem;

import static androidx.fragment.app.FragmentKt.setFragmentResult;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class SelectorDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("SelectorDialog");
    private @Nullable SelectorDialogClickListener callback;
    private @Nullable SelectorDialogInlineClickListener inlineCallback;

    private static final String BUNDLE_TITLE_EXTRA = "title";
    private static final String BUNDLE_ITEMS_EXTRA = "items";
    private static final String BUNDLE_TAGS_EXTRA = "tags";
    private static final String BUNDLE_NEGATIVE_EXTRA = "negative";
    private static final String BUNDLE_LISTENER_EXTRA = "listener";
    private static final String BUNDLE_REQUEST_KEY_EXTRA = "requestKey";

    // Either the defined tag integer value, or the index of the clicked item
    public static final String BUNDLE_KEY_CLICKED_ITEM = "clicked-item";

    @NonNull
    public static SelectorDialog newInstance(
        @Nullable String title,
        @NonNull ArrayList<SelectorDialogItem> items,
        @Nullable String negative,
        @Nullable String requestKey
    ) {
        SelectorDialog dialog = new SelectorDialog();
        Bundle args = new Bundle();
        args.putString(BUNDLE_TITLE_EXTRA, title);
        args.putSerializable(BUNDLE_ITEMS_EXTRA, items);
        args.putString(BUNDLE_NEGATIVE_EXTRA, negative);
        args.putString(BUNDLE_REQUEST_KEY_EXTRA, requestKey);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static SelectorDialog newInstance(
        @Nullable String title,
        @NonNull ArrayList<SelectorDialogItem> items,
        @NonNull ArrayList<Integer> tags,
        @Nullable String negative,
        @Nullable String requestKey
    ) {
        SelectorDialog dialog = new SelectorDialog();
        Bundle args = new Bundle();
        args.putString(BUNDLE_TITLE_EXTRA, title);
        args.putIntegerArrayList(BUNDLE_TAGS_EXTRA, tags);
        args.putSerializable(BUNDLE_ITEMS_EXTRA, items);
        args.putString(BUNDLE_NEGATIVE_EXTRA, negative);
        args.putString(BUNDLE_REQUEST_KEY_EXTRA, requestKey);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static SelectorDialog newInstance(
        @Nullable String title,
        @NonNull ArrayList<SelectorDialogItem> items,
        @Nullable String negative,
        @NonNull SelectorDialogInlineClickListener listener
    ) {
        SelectorDialog dialog = new SelectorDialog();
        Bundle args = new Bundle();
        args.putString(BUNDLE_TITLE_EXTRA, title);
        args.putSerializable(BUNDLE_ITEMS_EXTRA, items);
        args.putString(BUNDLE_NEGATIVE_EXTRA, negative);
        args.putParcelable(BUNDLE_LISTENER_EXTRA, listener);
        dialog.setArguments(args);
        return dialog;
    }

    public interface SelectorDialogClickListener {
        void onClick(String tag, int which, @Nullable Object data);

        default void onCancel(String tag) {
        }

        default void onNo(String tag) {
        }
    }

    public interface SelectorDialogInlineClickListener extends Parcelable {
        void onClick(String tag, int which, Object data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        try {
            callback = (SelectorDialogClickListener) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // maybe called from an activity rather than a fragment
        if (callback == null && getActivity() != null && (requireActivity() instanceof SelectorDialogClickListener)) {
            callback = (SelectorDialogClickListener) requireActivity();
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);
        if (inlineCallback == null && callback != null) {
            callback.onCancel(this.getTag());
        }
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        final @NonNull Bundle arguments = requireArguments();
        final @Nullable String title = arguments.getString(BUNDLE_TITLE_EXTRA);
        final @NonNull ArrayList<SelectorDialogItem> items = (ArrayList<SelectorDialogItem>) arguments.getSerializable(BUNDLE_ITEMS_EXTRA);
        final @Nullable ArrayList<Integer> tags = arguments.getIntegerArrayList(BUNDLE_TAGS_EXTRA);
        final @Nullable String negative = arguments.getString(BUNDLE_NEGATIVE_EXTRA);
        final @Nullable SelectorDialogInlineClickListener listener = arguments.getParcelable(BUNDLE_LISTENER_EXTRA);
        final @Nullable String requestKey = arguments.getString(BUNDLE_REQUEST_KEY_EXTRA);

        if (listener != null) {
            inlineCallback = listener;
        }

        final String fragmentTag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme());
        if (title != null) {
            EmojiTextView emojiTextView = new EmojiTextView(new ContextThemeWrapper(getContext(), R.style.MaterialAlertDialog_Material3_Title_Text));
            emojiTextView.setText(title);
            emojiTextView.setSingleLine(false);
            emojiTextView.setMaxLines(2);
            int padding = getResources().getDimensionPixelSize(R.dimen.edittext_padding);
            int paddingRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            emojiTextView.setPadding(padding, padding, paddingRight, 0);
            builder.setCustomTitle(emojiTextView);
        }

        ListAdapter adapter = new ArrayAdapter<>(
            requireActivity(),
            R.layout.item_selector_dialog,
            R.id.text1,
            items
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                //Use super class to create the View
                View v = super.getView(position, convertView, parent);
                TextView selectorOptionDesc = v.findViewById(R.id.text1);

                //Put the image on the TextView
                selectorOptionDesc.setCompoundDrawablesWithIntrinsicBounds(items.get(position).getIcon(), 0, 0, 0);

                //Add margin between image and text (support various screen densities)
                selectorOptionDesc.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.listitem_standard_margin_left_right));

                return v;
            }
        };

        builder.setAdapter(
            adapter,
            (dialog, which) -> {
                dialog.dismiss();

                final int whichItem = (tags != null && !tags.isEmpty()) ? tags.get(which) : which;

                if (inlineCallback != null) {
                    inlineCallback.onClick(fragmentTag, whichItem, object);
                } else if (callback != null) {
                    callback.onClick(fragmentTag, whichItem, object);
                }
                if (requestKey != null) {
                    final @NonNull Bundle resultBundle = new Bundle();
                    resultBundle.putAll(requestData);
                    resultBundle.putInt(BUNDLE_KEY_CLICKED_ITEM, whichItem);
                    setFragmentResult(SelectorDialog.this, requestKey, resultBundle);
                }
            }
        );

        if (negative != null) {
            builder.setNegativeButton(
                negative,
                (dialog, which) -> {
                    dialog.dismiss();
                    if (inlineCallback == null && callback != null) {
                        callback.onNo(fragmentTag);
                    }
                    if (requestKey != null) {
                        final @NonNull Bundle resultBundle = new Bundle();
                        resultBundle.putAll(requestData);
                        resultBundle.putSerializable(ThreemaDialogFragment.BUNDLE_KEY_CLICKED_BUTTON, ClickedButton.NEGATIVE);
                        setFragmentResult(SelectorDialog.this, requestKey, resultBundle);
                    }
                }
            );
        }

        return builder.create();
    }
}
