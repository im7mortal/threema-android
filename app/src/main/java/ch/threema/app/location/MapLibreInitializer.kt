package ch.threema.app.location

import android.content.Context
import androidx.annotation.UiThread
import ch.threema.base.utils.getThreemaLogger
import java.lang.ref.WeakReference
import org.maplibre.android.MapLibre

private val logger = getThreemaLogger("MapLibreInitializer")

class MapLibreInitializer(
    private val appContext: Context,
) {
    private var mapLibreWeakReference: WeakReference<MapLibre>? = null

    @UiThread
    fun initialize() {
        if (mapLibreWeakReference?.get() != null) {
            return
        }
        mapLibreWeakReference = WeakReference(MapLibre.getInstance(appContext))
        logger.info("MapLibre initialized")
    }
}
