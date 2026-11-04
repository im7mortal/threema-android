package ch.threema.android

import androidx.annotation.MainThread
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

@MainThread
fun FragmentManager.dismissDialog(tag: String) {
    val dialogFragment = (findFragmentByTag(tag) as? DialogFragment)
        ?: run {
            if (isDestroyed) {
                return
            }

            try {
                // make sure the fragment is really shown before removing it
                executePendingTransactions()
            } catch (_: IllegalStateException) {
            }
            findFragmentByTag(tag) as? DialogFragment?
        }
        ?: return

    try {
        dialogFragment.dismissAllowingStateLoss()
    } catch (_: Exception) {
    }
}
