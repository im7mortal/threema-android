package ch.threema.app.services

import ch.threema.app.BuildFlavor
import ch.threema.app.services.MessageSendingService.MessageSendingProcess
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.isHttpAuthError
import ch.threema.common.runWithRetry
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.MessageModel
import kotlin.synchronized
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("MessageSendingServiceImpl")

class MessageSendingServiceImpl(
    private val apiService: ApiService,
    private val onSendingFailed: (MessageModel) -> Unit,
) : MessageSendingService {
    private val coroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(parallelism = 1),
    )
    private val jobs = mutableMapOf<MessageUid, Job>()

    override fun addToQueue(process: MessageSendingProcess) {
        val messageUid = process.messageModel.uid!!
        logger.debug("Add message ({}) to queue", messageUid)

        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                runWithRetry(
                    action = { process.send() },
                    onException = { e, attempt ->
                        if (e.isHttpAuthError()) {
                            if (BuildFlavor.IS_ON_PREM) {
                                apiService.invalidateAuthToken()
                            }
                            throw e
                        }
                        if (attempt > SEND_MESSAGE_MAX_RETRY_ATTEMPTS) {
                            throw e
                        }
                        logger.warn("Sending message failed (attempt {})", attempt, e)
                    },
                    getRetryDelay = { attempt ->
                        (2L shl (attempt - 1)).seconds
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Sending message failed", e)
                onSendingFailed(process.messageModel)
            } finally {
                synchronized(jobs) {
                    if (jobs[messageUid] === coroutineContext.job) {
                        jobs.remove(messageUid)
                    }
                }
            }
        }
        synchronized(jobs) {
            jobs[messageUid]?.cancel()
            jobs[messageUid] = job
        }
        job.start()
    }

    override fun abort(messageUid: MessageUid) {
        synchronized(jobs) {
            jobs.remove(messageUid)?.cancel()
        }
    }

    companion object {
        private const val SEND_MESSAGE_MAX_RETRY_ATTEMPTS = 5
    }
}
