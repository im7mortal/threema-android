package ch.threema.app.errorreporting

import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.withContext

class ErrorReportDetailsProvider(
    private val errorRecordStore: ErrorRecordStore,
    private val sentryServiceMetaInfo: SentryService.MetaInfo,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun get(): String = withContext(dispatcherProvider.io) {
        buildString {
            appendLine(sentryServiceMetaInfo.toString())

            val previousErrorMessages = mutableSetOf<String>()
            errorRecordStore
                .getPendingRecords()
                .filter { errorRecord ->
                    // Ignore duplicate errors
                    val message = errorRecord.message?.message
                    if (message != null) {
                        if (message in previousErrorMessages) {
                            return@filter false
                        }
                        previousErrorMessages.add(message)
                    }
                    return@filter true
                }
                .forEach { errorRecord ->
                    appendLine()
                    when (errorRecord.level) {
                        ErrorRecordLevel.FATAL -> appendLine("Crash")
                        ErrorRecordLevel.ERROR -> appendLine("Error")
                    }
                    val formattedMessage = errorRecord.message?.formatted()
                    if (formattedMessage != null) {
                        appendLine(formattedMessage)
                    } else {
                        appendLine(errorRecord.exceptionDetails?.format())
                    }
                }
        }
            .trimEnd()
    }

    private fun List<ErrorRecordExceptionDetails>.format(): String = buildString {
        this@format.reversed().forEach { errorRecordExceptionDetails ->
            errorRecordExceptionDetails.packageName?.let { append(it) }
            append(errorRecordExceptionDetails.type)
            errorRecordExceptionDetails.message?.let { message ->
                append(": ")
                append(message)
            }
            appendLine()

            errorRecordExceptionDetails.stackTrace.reversed().forEach { stackTraceElement ->
                append("  - ")
                (stackTraceElement.className ?: stackTraceElement.fileName)?.let { prefix ->
                    append(prefix)
                }
                stackTraceElement.methodName?.let { methodName ->
                    append(".")
                    append(methodName)
                }
                stackTraceElement.lineNumber.takeIf { it > 0 }?.let { lineNumber ->
                    append("@")
                    append(lineNumber)
                }
                appendLine()
            }
        }
    }
        .trim()
}
