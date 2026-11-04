package ch.threema.app.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.OsConstants
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.content.getSystemService
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import ch.threema.common.copyTo
import ch.threema.logging.BaseErrorRecordStore
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ExitReasonLogger")

class ExitReasonLogger(
    private val appContext: Context,
    private val sharedPreferences: SharedPreferences,
    private val errorRecordStore: BaseErrorRecordStore,
    private val timeProvider: TimeProvider,
    private val appDirectoryProvider: AppDirectoryProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun logExitReason() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val activityManager = appContext.getSystemService<ActivityManager>() ?: return
        try {
            val applicationExitInfos = activityManager.getHistoricalProcessExitReasons(null, 0, 0)
            if (applicationExitInfos.isEmpty()) {
                return
            }

            val simpleDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            for (exitInfo in applicationExitInfos) {
                val timestampOfLastLog = sharedPreferences.getLong(EXIT_REASON_LOGGING_TIMESTAMP, 0L)

                if (exitInfo.timestamp > timestampOfLastLog) {
                    logger.info(
                        "*** App last exited at {} with reason: {}, description: {}, status: {}",
                        simpleDateFormat.format(exitInfo.timestamp),
                        exitInfo.getReasonText(),
                        exitInfo.description,
                        exitInfo.getStatusText(),
                    )
                    when (exitInfo.reason) {
                        ApplicationExitInfo.REASON_ANR -> {
                            logAnrDetails(exitInfo)
                        }
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> {
                            writeNativeCrashTombstoneFile(exitInfo)
                        }
                        else -> Unit
                    }
                }
            }

            sharedPreferences.edit {
                putLong(EXIT_REASON_LOGGING_TIMESTAMP, timeProvider.get().toEpochMilli())
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Unable to get reason of last exit", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun logAnrDetails(exitInfo: ApplicationExitInfo) {
        try {
            exitInfo.traceInputStream?.use { traceInputStream ->
                BufferedReader(InputStreamReader(traceInputStream)).use { reader ->
                    logger.info(reader.readText())
                }
            }
        } catch (e: IOException) {
            logger.error("Error getting ANR trace", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun writeNativeCrashTombstoneFile(exitInfo: ApplicationExitInfo) {
        if (!BuildConfig.LOG_TOMBSTONES_FOR_NATIVE_CRASHES) {
            return
        }
        if (!sharedPreferences.getBoolean(appContext.getString(R.string.preferences__store_tombstones), false)) {
            return
        }

        val tombstoneFile = File(appDirectoryProvider.tombstonesDirectory, generateTombstoneFileName())
        withContext(dispatcherProvider.io) {
            try {
                exitInfo.traceInputStream?.use { traceInputStream ->
                    traceInputStream.copyTo(tombstoneFile)
                    logger.info("Stored native crash {}", tombstoneFile.name)
                }
            } catch (e: IOException) {
                logger.error("Error writing native crash tombstone file", e)
                return@withContext
            }

            if (BuildConfig.ERROR_REPORTING_SUPPORTED) {
                try {
                    errorRecordStore.storeHandledError(
                        message = "Native crash",
                        attachments = listOf(tombstoneFile),
                    )
                } catch (e: IOException) {
                    logger.error("Failed to store error report for native crash", e)
                }
            }
        }
    }

    private fun generateTombstoneFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        return "tombstone-${formatter.format(timeProvider.get().atZone(ZoneId.systemDefault()))}"
    }

    companion object {
        private const val EXIT_REASON_LOGGING_TIMESTAMP = "exit_reason_timestamp"

        @RequiresApi(Build.VERSION_CODES.R)
        private fun ApplicationExitInfo.getStatusText(): String =
            when (status) {
                OsConstants.SIGABRT -> "SIGABRT"
                OsConstants.SIGALRM -> "SIGALRM"
                OsConstants.SIGBUS -> "SIGBUS"
                OsConstants.SIGCHLD -> "SIGCHLD"
                OsConstants.SIGCONT -> "SIGCONT"
                OsConstants.SIGFPE -> "SIGFPE"
                OsConstants.SIGHUP -> "SIGHUP"
                OsConstants.SIGILL -> "SIGILL"
                OsConstants.SIGINT -> "SIGINT"
                OsConstants.SIGIO -> "SIGIO"
                OsConstants.SIGKILL -> "SIGKILL"
                OsConstants.SIGPIPE -> "SIGPIPE"
                OsConstants.SIGPROF -> "SIGPROF"
                OsConstants.SIGPWR -> "SIGPWR"
                OsConstants.SIGQUIT -> "SIGQUIT"
                OsConstants.SIGRTMAX -> "SIGRTMAX"
                OsConstants.SIGRTMIN -> "SIGRTMIN"
                OsConstants.SIGSEGV -> "SIGSEGV"
                OsConstants.SIGSTKFLT -> "SIGSTKFLT"
                OsConstants.SIGSTOP -> "SIGSTOP"
                OsConstants.SIGSYS -> "SIGSYS"
                OsConstants.SIGTERM -> "SIGTERM"
                OsConstants.SIGTRAP -> "SIGTRAP"
                OsConstants.SIGTSTP -> "SIGTSTP"
                OsConstants.SIGTTIN -> "SIGTTIN"
                OsConstants.SIGTTOU -> "SIGTTOU"
                OsConstants.SIGURG -> "SIGURG"
                OsConstants.SIGUSR1 -> "SIGUSR1"
                OsConstants.SIGUSR2 -> "SIGUSR2"
                OsConstants.SIGVTALRM -> "SIGVTALRM"
                OsConstants.SIGWINCH -> "SIGWINCH"
                OsConstants.SIGXCPU -> "SIGXCPU"
                OsConstants.SIGXFSZ -> "SIGXFSZ"
                0 -> null
                else -> "UNKNOWN"
            }
                ?.let { "$status ($it)" }
                ?: "$status"

        @RequiresApi(Build.VERSION_CODES.R)
        private fun ApplicationExitInfo.getReasonText(): String {
            val reasonName = when (reason) {
                ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
                ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                ApplicationExitInfo.REASON_CRASH -> "APP CRASH(EXCEPTION)"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "APP CRASH(NATIVE)"
                ApplicationExitInfo.REASON_ANR -> "ANR"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION FAILURE"
                ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION CHANGE"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE RESOURCE USAGE"
                ApplicationExitInfo.REASON_USER_REQUESTED -> "USER REQUESTED"
                ApplicationExitInfo.REASON_USER_STOPPED -> "USER STOPPED"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY DIED"
                ApplicationExitInfo.REASON_OTHER -> "OTHER KILLS BY SYSTEM"
                ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
                ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "STATE CHANGE"
                ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE UPDATED"
                else -> "UNKNOWN"
            }
            return "$reason ($reasonName)"
        }
    }
}
