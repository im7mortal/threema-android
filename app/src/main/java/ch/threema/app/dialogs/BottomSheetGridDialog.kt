package ch.threema.app.dialogs

import androidx.annotation.StringRes
import ch.threema.android.buildBundle
import ch.threema.app.ui.BottomSheetItem
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("BottomSheetGridDialog")

class BottomSheetGridDialog : BottomSheetDialog() {
    init {
        logScreenVisibility(logger)
    }

    override fun onPause() {
        // Hack to prevent TransactionTooLargeException when hosting activity goes into the background
        dismiss()

        super.onPause()
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun newInstance(
            @StringRes title: Int = 0,
            items: List<BottomSheetItem>,
            requestKey: String? = null,
        ): BottomSheetGridDialog {
            val dialog = BottomSheetGridDialog()
            dialog.setArguments(
                buildBundle {
                    putInt(ARG_TITLE, title)
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
