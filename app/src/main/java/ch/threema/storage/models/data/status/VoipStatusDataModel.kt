package ch.threema.storage.models.data.status

import androidx.annotation.IntDef
import ch.threema.common.putIfNotNull
import ch.threema.storage.models.data.status.StatusDataModel.StatusType
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @param instant used for hangup messages that indicate a missed call. It is not included in the params and thus never persisted.
 * If it is null, then the current time should be used.
 */
@ConsistentCopyVisibility
data class VoipStatusDataModel
private constructor(
    val callId: Long,
    @VoipStatusType
    val status: Int,
    val reason: Byte? = null,
    val duration: Duration? = null,
    val instant: Instant? = null,
) : StatusDataModel {

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        MISSED,
        FINISHED,
        REJECTED,
        ABORTED,
    )
    annotation class VoipStatusType

    @StatusType
    override val type: Int
        get() = TYPE

    override fun getParams() = buildMap {
        put(PARAM_STATUS, status)
        if (callId != NO_CALL_ID) {
            put(PARAM_CALL_ID, callId)
        }
        putIfNotNull(PARAM_REASON, reason)
        putIfNotNull(PARAM_DURATION, duration?.inWholeSeconds)
    }

    val durationInSeconds: Int?
        get() = duration?.inWholeSeconds?.toInt()

    companion object {
        const val TYPE = 1

        const val MISSED = 1
        const val FINISHED = 2
        const val REJECTED = 3
        const val ABORTED = 4

        const val NO_CALL_ID = 0L

        private const val PARAM_STATUS = "status"
        private const val PARAM_CALL_ID = "callId"
        private const val PARAM_REASON = "reason"
        private const val PARAM_DURATION = "duration"

        @JvmStatic
        fun createRejected(callId: Long, reason: Byte?) = VoipStatusDataModel(
            callId = callId,
            reason = reason,
            status = REJECTED,
        )

        fun createFinished(callId: Long, duration: Duration) = VoipStatusDataModel(
            callId = callId,
            duration = duration,
            status = FINISHED,
        )

        @JvmStatic
        fun createMissed(callId: Long, instant: Instant? = null) = VoipStatusDataModel(
            callId = callId,
            status = MISSED,
            instant = instant,
        )

        fun createAborted(callId: Long) = VoipStatusDataModel(
            callId = callId,
            status = ABORTED,
        )

        fun createFromParams(params: Map<String, Any?>) = VoipStatusDataModel(
            callId = (params[PARAM_CALL_ID] as? Number)?.toLong() ?: NO_CALL_ID,
            status = (params[PARAM_STATUS] as Number).toInt(),
            duration = (params[PARAM_DURATION] as? Number)?.toInt()?.seconds,
            reason = (params[PARAM_REASON] as? Number)?.toInt()?.takeIf { it <= 0xff }?.toByte(),
        )
    }
}
