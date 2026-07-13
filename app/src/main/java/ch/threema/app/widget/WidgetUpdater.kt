package ch.threema.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ch.threema.app.R
import ch.threema.app.receivers.WidgetProvider

// TODO(ANDR-4706): The functionality of this class should only be called from [WidgetUpdaterMonitor]
class WidgetUpdater(
    private val appContext: Context,
) {
    fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(appContext, WidgetProvider::class.java))
        // TODO(ANDR-4366): Replace use of deprecated method
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_list)
    }
}
