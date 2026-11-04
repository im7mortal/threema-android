package ch.threema.app.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.widget.PopupWindow
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.layout.WindowMetricsCalculator.Companion.getOrCreate
import ch.threema.app.utils.ConfigUtils
import com.google.android.material.textfield.TextInputLayout

/**
 * A PopupWindow that moves with its anchor view
 */
abstract class MovingPopupWindow(context: Context?) : PopupWindow(context) {
    protected lateinit var activity: Activity
    protected var lastY = 0
    protected var anchorView: TextInputLayout? = null

    protected var onLayoutChangeListener =
        OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            anchorView?.let {
                val coordinates = getPopupWindowPositionAboveAnchor(
                    activity,
                    it,
                )
                if (coordinates[1] != lastY) {
                    update(coordinates[0], coordinates[1], -1, -1)
                }
                lastY = coordinates[1]
            }
        }

    protected var windowInsetsAnimationCallback: WindowInsetsAnimationCompat.Callback =
        object : WindowInsetsAnimationCompat.Callback(
            DISPATCH_MODE_STOP,
        ) {
            var startY = 0
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                super.onPrepare(animation)
                anchorView?.let {
                    val coordinates = getPopupWindowPositionAboveAnchor(
                        activity,
                        it,
                    )
                    startY = coordinates[1]
                    lastY = 0
                }
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                anchorView?.let {
                    val coordinates = getPopupWindowPositionAboveAnchor(
                        activity,
                        it,
                    )
                    if (coordinates[1] != lastY &&
                        coordinates[1] != startY
                    ) {
                        update(coordinates[0], coordinates[1], -1, -1)
                    }
                    lastY = coordinates[1]
                }
                return insets
            }
        }

    protected fun show(activity: Activity, anchorView: TextInputLayout?) {
        this.activity = activity
        this.anchorView = anchorView
    }

    /**
     * Get position of a PopupWindow that has `Gravity.LEFT|Gravity.BOTTOM` set and is supposed to be located above the supplied anchor view
     *
     * @param activity   Activity that hosts the PopupWindow
     * @param anchorView Anchor view
     * @return int array of x/y coordinates to be supplied to showAtLocation() as well as the viewable height
     */
    protected fun getPopupWindowPositionAboveAnchor(activity: Activity, anchorView: View): IntArray {
        val windowLocation = intArrayOf(0, 0)
        anchorView.getLocationInWindow(windowLocation)

        val windowMetrics = getOrCreate().computeCurrentWindowMetrics(activity)
        val bounds = windowMetrics.bounds

        val x = windowLocation[0] + anchorView.paddingLeft
        val y = bounds.height() - windowLocation[1]

        val viewableHeight = windowLocation[1] - (ConfigUtils.getStatusBarHeight(activity) + ConfigUtils.getActionBarSize(activity))

        return intArrayOf(x, y, viewableHeight)
    }
}
