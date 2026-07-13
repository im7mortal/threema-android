package ch.threema.app.location;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;

import org.slf4j.Logger;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;

import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.R;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.utils.GeoLocationUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.JavaCompat.isNullOrEmpty;


public class LocationPickerConfirmDialog extends ThreemaDialogFragment {
    private LocationConfirmDialogClickListener callback;

    private static final String ARG_TITLE = "title";
    private static final String ARG_NAME = "name";
    private static final String ARG_LAT_LNG = "latLng";
    private static final String ARG_LAT_LNG_BOUNDS = "latLngBounds";

    private static final Logger logger = getThreemaLogger("LocationPickerConfirmDialog");

    public static LocationPickerConfirmDialog newInstance(String title, String name, LatLng latLng, LatLngBounds latLngBounds, LocationConfirmDialogClickListener callback) {
        LocationPickerConfirmDialog dialog = new LocationPickerConfirmDialog();
        dialog.callback = callback;

        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_NAME, name);
        args.putParcelable(ARG_LAT_LNG, latLng);
        args.putParcelable(ARG_LAT_LNG_BOUNDS, latLngBounds);

        dialog.setArguments(args);
        return dialog;
    }

    public interface LocationConfirmDialogClickListener {
        void onOK(String tag, Object object);

        default void onCancel(String tag) {
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        String title = "";
        String name = "";
        LatLng latLng = null;

        Bundle arguments = getArguments();
        if (arguments != null) {
            title = arguments.getString(ARG_TITLE);
            name = arguments.getString(ARG_NAME);
            latLng = arguments.getParcelable(ARG_LAT_LNG);
        }

        final String tag = this.getTag();

        final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_location_picker_confirm, null);

        TextView addressText = dialogView.findViewById(R.id.place_address);
        TextView nameText = dialogView.findViewById(R.id.place_name);
        TextView coordinatesText = dialogView.findViewById(R.id.place_coordinates);

        addressText.setVisibility(View.GONE);

        if (!isNullOrEmpty(name)) {
            nameText.setText(name);
        } else {
            nameText.setVisibility(View.GONE);
        }

        if (latLng != null) {
            coordinatesText.setText(String.format(Locale.US, "%f, %f", latLng.getLatitude(), latLng.getLongitude()));
        } else {
            coordinatesText.setVisibility(View.GONE);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), getTheme());
        builder.setView(dialogView);

        if (!isNullOrEmpty(title)) {
            builder.setTitle(title);
        }

        builder.setPositiveButton(R.string.ok, (dialog, which) -> callback.onOK(tag, object));
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> callback.onCancel(tag)
        );

        AlertDialog alertDialog = builder.create();

        if (latLng != null) {
            new LocationNameAsyncTask(getContext(), addressText, latLng.getLatitude(), latLng.getLongitude())
                .execute(alertDialog, null);
        }

        setCancelable(false);

        return alertDialog;
    }

    /**
     * AsyncTask that loads the address retrieved from the Geocoder to the supplied TextView
     */
    private static class LocationNameAsyncTask extends LifecycleAwareAsyncTask<Void, String> {
        private final WeakReference<Context> contextWeakReference;
        private final WeakReference<TextView> textViewWeakReference;
        private final double latitude;
        private final double longitude;

        public LocationNameAsyncTask(@Nullable Context context, @NonNull TextView textView, double latitude, double longitude) {
            this.contextWeakReference = new WeakReference<>(context);
            this.textViewWeakReference = new WeakReference<>(textView);
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        protected String doInBackground(Void params) {
            var context = contextWeakReference.get();
            if (context != null) {
                try {
                    return GeoLocationUtil.getAddressFromLocation(context, latitude, longitude);
                } catch (IOException e) {
                    logger.error("Exception", e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (!isNullOrEmpty(s)) {
                var textView = textViewWeakReference.get();
                if (textView != null) {
                    textView.setText(s);
                    textView.setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
