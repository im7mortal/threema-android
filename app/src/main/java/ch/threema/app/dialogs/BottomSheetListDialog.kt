package ch.threema.app.dialogs

import androidx.annotation.StringRes
import ch.threema.android.buildBundle
import ch.threema.app.ui.BottomSheetItem
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("BottomSheetListDialog")

class BottomSheetListDialog : BottomSheetDialog() {
    init {
        logScreenVisibility(logger)
    }

    companion object {
        @JvmStatic
        fun newInstance(
            @StringRes title: Int = 0,
            items: List<BottomSheetItem>,
            selectedIndex: Int,
            requestKey: String? = null,
        ): BottomSheetListDialog {
            val dialog = BottomSheetListDialog()
            dialog.setArguments(
                buildBundle {
                    putInt(ARG_TITLE, title)
                    putInt(ARG_SELECTED, selectedIndex)
                    putParcelableArrayList(ARG_ITEMS, ArrayList(items))
                    if (requestKey != null) {
                        putString(ARG_REQUEST_KEY, requestKey)
                    }
                },
            )
            return dialog
        }
    }
}
