package ch.threema.app.activities

/**
 * Subclasses of [ThreemaAppCompatActivity] may implement this interface to handle back pressed events, in a lifecycle-aware manner.
 */
interface BackPressHandler {
    /**
     * Handle an on back pressed event.
     * Note that this method is lifecycle aware, i.e., it is not called if the activity has been destroyed.
     */
    fun handleOnBackPressed()
}
