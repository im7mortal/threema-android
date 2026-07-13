package ch.threema.logging

import androidx.annotation.WorkerThread

interface BaseErrorRecordStore {
    @WorkerThread
    fun storeFatalError(e: Throwable)

    /**
     * @param message A message describing the error. It may contain placeholders of the form `%s`.
     * @param parameters The parameters that will be inserted into the placeholders in [message]
     */
    @WorkerThread
    fun storeHandledError(message: String, parameters: List<String>, e: Throwable?)
}
