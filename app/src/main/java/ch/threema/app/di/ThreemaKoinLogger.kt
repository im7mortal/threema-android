package ch.threema.app.di

import android.annotation.SuppressLint
import ch.threema.app.di.modules.SessionScopeUnavailableException
import ch.threema.base.utils.getThreemaLogger
import org.koin.core.logger.Level
import org.koin.core.logger.Logger as KoinLogger

@SuppressLint("LoggerName")
private val logger = getThreemaLogger("Koin")

object ThreemaKoinLogger : KoinLogger() {
    override fun display(level: Level, msg: String) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.WARNING -> {
                if (!msg.contains("override index 'androidx.lifecycle.ViewModel::_root_'")) {
                    logger.warn(msg)
                }
            }
            Level.ERROR -> {
                if (SessionScopeUnavailableException::class.simpleName?.let { it in msg } != true) {
                    logger.error(msg)
                }
            }
            Level.NONE -> Unit
        }
    }
}
