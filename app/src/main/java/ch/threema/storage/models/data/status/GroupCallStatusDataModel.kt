package ch.threema.storage.models.data.status

import ch.threema.common.putIfNotNull
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.data.status.StatusDataModel.StatusType

@ConsistentCopyVisibility
data class GroupCallStatusDataModel
private constructor(
    val callId: String?,
    val groupId: Int = 0,
    val callerIdentity: IdentityString? = null,
    val status: Int,
) : StatusDataModel {
    @StatusType
    override val type
        get() = TYPE

    override fun getParams() = buildMap {
        put(PARAM_STATUS, status)
        putIfNotNull(PARAM_CALL_ID, callId)
        putIfNotNull(PARAM_CALLER_IDENTITY, callerIdentity)
        if (groupId != 0) {
            put(PARAM_GROUP_ID, groupId)
        }
    }

    companion object {
        const val TYPE = 2

        const val STATUS_STARTED = 1
        const val STATUS_ENDED = 2

        private const val PARAM_CALL_ID = "callId"
        private const val PARAM_CALLER_IDENTITY = "callerIdentity"
        private const val PARAM_GROUP_ID = "groupId"
        private const val PARAM_STATUS = "status"

        fun createStarted(
            callId: String,
            groupId: Int,
            callerIdentity: IdentityString,
        ) = GroupCallStatusDataModel(
            callId = callId,
            groupId = groupId,
            callerIdentity = callerIdentity,
            status = STATUS_STARTED,
        )

        fun createEnded(
            callId: String,
        ) = GroupCallStatusDataModel(
            callId = callId,
            status = STATUS_ENDED,
        )

        fun createFromParams(params: Map<String, Any?>) = GroupCallStatusDataModel(
            callId = params[PARAM_CALL_ID] as? String,
            callerIdentity = params[PARAM_CALLER_IDENTITY] as? String,
            groupId = (params[PARAM_GROUP_ID] as? Number)?.toInt() ?: 0,
            status = (params[PARAM_STATUS] as? Number)?.toInt() ?: 0,
        )
    }
}
