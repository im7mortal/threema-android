package ch.threema.logging

import ch.threema.app.BuildConfig
import ch.threema.base.isInTest
import ch.threema.common.DispatcherProvider
import kotlin.getValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger

/**
 * Logs the message as an error and creates an error record, such that this error can eventually also be sent to Sentry.
 *
 * This should be used for errors that we expect to never happen, but if they do happen, they should not be critical enough to warrant
 * crashing the app, but we nonetheless want to know when they happen.
 *
 * Note that this message may be displayed to the user as well, if they inspect the details of an error report.
 */
fun Logger.logAndReportError(message: String, vararg parameters: Any?) {
    error(message, *parameters)

    @Suppress("SimplifyBooleanWithConstants")
    if (BuildConfig.ERROR_REPORTING_SUPPORTED && !isInTest()) {
        ErrorRecorder.storeErrorRecord(
            messageFormat = name?.let { loggerName ->
                "${loggerName.removePrefix("ch.threema.")}: $message"
            }
                ?: message,
            parameters,
        )
    }
}

private object ErrorRecorder : KoinComponent {
    private val errorRecordStore: BaseErrorRecordStore by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    @OptIn(DelicateCoroutinesApi::class)
    fun storeErrorRecord(messageFormat: String, parameters: Array<out Any?>) {
        val e = parameters.lastOrNull() as? Throwable
        val transformedParameters = if (e != null) {
            parameters.dropLast(1)
        } else {
            parameters.toList()
        }
            .map { it.toString() }

        GlobalScope.launch(dispatcherProvider.io) {
            errorRecordStore.storeHandledError(
                message = messageFormat.replace("{}", "%s"),
                parameters = transformedParameters,
                e = e,
            )
        }
    }
}
