package ch.threema.app.webviews.usecases

import android.net.Uri
import ch.threema.app.BuildConfig

/**
 * Checks whether a URI intercepted in a web view may be sent to the system for opening,
 * to prevent the opening of arbitrary, potentially insecure URIs such as intent:// URIs.
 */
class MayOpenUriExternallyUseCase {
    fun call(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in ALLOWED_SCHEMES
    }

    companion object {
        private val ALLOWED_SCHEMES = setOf(
            "https",
            "http",
            "tel",
            "mailto",
            "threema",
            "threemawork",
            "threemaonprem",
            BuildConfig.uriScheme,
        )
    }
}
