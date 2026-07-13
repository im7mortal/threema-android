package ch.threema.logging

import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("UncaughtExceptionsLogger")

object UncaughtExceptionsLogger {
    val tag: String = logger.name

    fun logUnhandledException(e: Throwable) {
        logger.error("Uncaught exception", e)
    }
}
