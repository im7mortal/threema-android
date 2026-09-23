package ch.threema.app.androidcontactsync.read

/**
 * The cursor could not be created.
 */
class CursorCreateException(message: String? = null, cause: Throwable? = null) : Throwable(
    message = message,
    cause = cause,
)
