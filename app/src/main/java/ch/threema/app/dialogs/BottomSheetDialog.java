package ch.threema.app.dialogs;

import static androidx.fragment.app.FragmentKt.setFragmentResult;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import ch.threema.app.R;
import ch.threema.app.adapters.BottomSheetGridAdapter;
import ch.threema.app.adapters.BottomSheetListAdapter;
import ch.threema.app.ui.BottomSheetItem;

public abstract class BottomSheetDialog extends BottomSheetDialogFragment {

    public static final String ARG_TITLE = "title";
    public static final String ARG_SELECTED = "selected";
    public static final String ARG_ITEMS = "items";
    public static final String ARG_REQUEST_KEY = "requestKey";

    private static final String RESULT_TAG = "tag";
    private static final String RESULT_DATA = "data";
    private static final String RESULT_CANCEL_TAG = "cancel_tag";

    @Override
    public void onResume() {
        super.onResume();

        // Hack to set width of bottom sheet
        WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        int width = metrics.widthPixels < 1440 ? metrics.widthPixels : 1440;
        int height = -1;

        Window window = getDialog().getWindow();
        if (window != null) {
            window.setLayout(width, height);
        }
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        int title = getArguments().getInt(ARG_TITLE);
        int selectedItem = getArguments().getInt(ARG_SELECTED);
        final ArrayList<BottomSheetItem> items = getArguments().getParcelableArrayList(ARG_ITEMS);
        final @Nullable String requestKey = requireArguments().getString(ARG_REQUEST_KEY);

        final com.google.android.material.bottomsheet.BottomSheetDialog dialog = (com.google.android.material.bottomsheet.BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        final View dialogView = requireActivity().getLayoutInflater().inflate(
            this instanceof BottomSheetGridDialog ?
                R.layout.dialog_bottomsheet_grid :
                R.layout.dialog_bottomsheet_list,
            null);
        final AbsListView listView = dialogView.findViewById(R.id.list_view);
        final TextView titleView = dialogView.findViewById(R.id.title_text);

        if (title != 0) {
            titleView.setText(title);
        } else {
            titleView.setVisibility(View.GONE);
        }

        listView.setAdapter(
            this instanceof BottomSheetGridDialog ?
                new BottomSheetGridAdapter(getContext(), items) :
                new BottomSheetListAdapter(getContext(), items, selectedItem));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (items != null && i < items.size()) {
                    dismiss();

                    if (requestKey != null) {
                        var resultBundle = new Bundle();
                        resultBundle.putString(RESULT_TAG, items.get(i).getTag());
                        resultBundle.putString(RESULT_DATA, items.get(i).getData());
                        setFragmentResult(BottomSheetDialog.this, requestKey, resultBundle);
                    }
                }
            }
        });

        dialog.setContentView(dialogView);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;

                final FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    bottomSheet.post(new Runnable() {
                        @Override
                        public void run() {
                            // there will be no rounded corners in expanded state due to this
                            // https://github.com/material-components/material-components-android/pull/437#issuecomment-536668983
                            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                        }
                    });
                }
            }
        });

        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);

        final @Nullable String requestKey = requireArguments().getString(ARG_REQUEST_KEY);
        if (requestKey != null) {
            var resultBundle = new Bundle();
            resultBundle.putString(RESULT_TAG, RESULT_CANCEL_TAG);
            setFragmentResult(BottomSheetDialog.this, requestKey, resultBundle);
        }
    }

    public interface BottomSheetDialogSelectionHandler {
        void onSelected(@NonNull String tag, @Nullable String data);
    }

    public static void onItemClicked(@NonNull Bundle result, @NonNull BottomSheetDialogSelectionHandler handler) {
        var tag = result.getString(RESULT_TAG);
        if (tag != null && !tag.equals(RESULT_CANCEL_TAG)) {
            var data = result.getString(RESULT_DATA);
            handler.onSelected(tag, data);
        }
    }

    public static void onCanceled(@NonNull Bundle result, @NonNull Runnable onCancel) {
        var tag = result.getString(RESULT_TAG);
        if (tag != null && tag.equals(RESULT_CANCEL_TAG)) {
            onCancel.run();
        }
    }
}
