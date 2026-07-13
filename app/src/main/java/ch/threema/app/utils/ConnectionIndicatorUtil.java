package ch.threema.app.utils;

import android.content.Context;
import android.view.View;

import org.koin.java.KoinJavaComponent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import ch.threema.app.R;
import ch.threema.domain.protocol.connection.ConnectionState;

public class ConnectionIndicatorUtil {
    private final @ColorInt int red, orange, transparent;

    @Deprecated
    @NonNull
    public static ConnectionIndicatorUtil getInstance() {
        return KoinJavaComponent.get(ConnectionIndicatorUtil.class);
    }

    public ConnectionIndicatorUtil(Context context) {
        this.red = context.getResources().getColor(R.color.material_red);
        this.orange = context.getResources().getColor(R.color.material_orange);
        this.transparent = context.getResources().getColor(android.R.color.transparent);
    }

    @UiThread
    public void updateConnectionIndicator(View connectionIndicator, ConnectionState connectionState) {
        if (connectionIndicator != null) {
            if (connectionState == ConnectionState.CONNECTED) {
                connectionIndicator.setBackgroundColor(this.orange);
            } else if (connectionState == ConnectionState.LOGGED_IN) {
                connectionIndicator.setBackgroundColor(this.transparent);
            } else {
                connectionIndicator.setBackgroundColor(this.red);
            }
            connectionIndicator.invalidate();
        }
    }
}
