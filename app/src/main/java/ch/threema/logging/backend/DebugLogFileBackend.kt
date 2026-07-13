package ch.threema.logging.backend

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.AnyThread
import ch.threema.annotation.SameThread
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.common.TimeProvider
import ch.threema.logging.LogLevel
import ch.threema.logging.UncaughtExceptionsLogger
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.sql.Date
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.slf4j.helpers.MessageFormatter

/**
 * A logging backend that logs to the debug log file, or falls back to a fallback log file if the default log file can not be written.
 */
class DebugLogFileBackend(
    private val debugLogFileManager: DebugLogFileManager,
    @field:LogLevel
    @param:LogLevel
    private val minLogLevel: Int,
    private val handlerExecutor: HandlerExecutor = createHandlerExecutor(),
    private val timeProvider: TimeProvider = TimeProvider.default,
) : LogBackend {

    override fun isEnabled(level: Int): Boolean =
        enabled && level >= minLogLevel

    /**
     * If the logger is enabled, write the log to the log file.
     * Note: I/O is done asynchronously, so the log may not yet be fully written
     * to storage when this method returns!
     */
    override fun print(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        message: String?,
    ) {
        if (isEnabled(level)) {
            enqueuePrinting(level, tag, throwable, message)
        }
    }

    override fun print(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        messageFormat: String,
        args: Array<Any?>,
    ) {
        if (isEnabled(level)) {
            val message = try {
                MessageFormatter.arrayFormat(messageFormat, args).message
            } catch (_: Exception) {
                messageFormat
            }
            enqueuePrinting(level, tag, throwable, message = message)
        }
    }

    @AnyThread
    private fun enqueuePrinting(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        message: String?,
    ) {
        // If the log message comes from an uncaught exception, we intentionally block the current thread in an attempt
        // to keep the app process alive long enough for the exception details to be fully written into the log file.
        if (tag == UncaughtExceptionsLogger.tag) {
            handlerExecutor.postAndBlockUntilCompletion {
                printSynchronously(level, tag, throwable, message)
            }
        } else {
            handlerExecutor.post {
                printSynchronously(level, tag, throwable, message)
            }
        }
    }

    @SameThread
    private fun printSynchronously(
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        message: String?,
    ) {
        val now = timeProvider.get()
        val logLine = compileLogLine(now, level, tag, throwable, message)
        val logFile = debugLogFileManager.getCurrentLogFile(now)
        try {
            logFile.appendLine(logLine)
            if (!hasSuccessfullyWrittenLogLine) {
                debugLogFileManager.deleteFallbackLogFiles()
                hasSuccessfullyWrittenLogLine = true
            }
        } catch (_: Exception) {
            if (!hasSuccessfullyWrittenLogLine) {
                val fallbackLogFile = debugLogFileManager.getCurrentFallbackLogFile(now)
                fallbackLogFile.appendLine(logLine)
            }
        }
    }

    private fun compileLogLine(
        now: Instant,
        @LogLevel level: Int,
        tag: String,
        throwable: Throwable?,
        message: String?,
    ): String = buildString {
        append(getTimestamp(now))
        append('\t')
        append(getLogLevelString(level))
        append(' ')
        append(cleanTag(tag, STRIP_PREFIXES))
        append(": ")
        if (message != null) {
            append(message)
            if (throwable != null) {
                append('\n')
            }
        }
        if (throwable != null) {
            append(throwable.stackTraceToString())
        }
    }

    private fun getTimestamp(now: Instant) =
        Date.from(now).toString()

    private fun getLogLevelString(@LogLevel level: Int) =
        when (level) {
            Log.VERBOSE -> "TRACE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO "
            Log.WARN -> "WARN "
            Log.ERROR -> "ERROR"
            else -> "?    "
        }

    private fun File.appendLine(logLine: String) {
        FileWriter(this, true).use { fw ->
            PrintWriter(fw).use { pw ->
                pw.println(logLine)
            }
        }
    }

    companion object {
        private val STRIP_PREFIXES = arrayOf(
            "ch.threema.app.",
            "ch.threema.domain.",
            "ch.threema.storage.",
            "ch.threema.",
        )

        private var enabled = false
        private var hasSuccessfullyWrittenLogLine = false

        /**
         * Enable or disable logging to a debug log file.
         * It is disabled by default.
         * When disabling the logging, all log files are deleted.
         */
        @Synchronized
        fun setEnabled(logFileManager: DebugLogFileManager, enabled: Boolean) {
            if (!Companion.enabled && enabled) {
                hasSuccessfullyWrittenLogLine = false
            }
            Companion.enabled = enabled
            with(logFileManager) {
                if (enabled) {
                    createLogDirectory()
                } else {
                    deleteLogFiles()
                    deleteFallbackLogFiles()
                }
            }
        }

        private fun createHandlerExecutor(): HandlerExecutor {
            val handlerThread = HandlerThread("DebugLogWorker")
            handlerThread.start()
            val looper = handlerThread.getLooper()
            val parentHandler = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Handler.createAsync(looper)
            } else {
                Handler(looper)
            }
            return HandlerExecutor(parentHandler)
        }

        private fun HandlerExecutor.postAndBlockUntilCompletion(runnable: Runnable) {
            val deferred = CompletableDeferred<Unit>()
            post {
                runnable.run()
                deferred.complete(Unit)
            }
            runBlocking {
                deferred.await()
            }
        }
    }
}
