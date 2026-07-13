package ch.threema.storage.models.data.status

import androidx.annotation.IntDef
import ch.threema.storage.models.data.status.StatusDataModel.StatusType

@ConsistentCopyVisibility
data class ForwardSecurityStatusDataModel
private constructor(
    @ForwardSecurityStatusType
    val statusType: Int,
    val quantity: Int,
    val staticText: String?,
) : StatusDataModel {

    @IntDef(
        value = [
            ForwardSecurityStatusType.STATIC_TEXT,
            ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY,
            ForwardSecurityStatusType.FORWARD_SECURITY_RESET,
            ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED,
            ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED_RX,
            ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED,
            ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER,
            ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE,
            ForwardSecurityStatusType.FORWARD_SECURITY_ILLEGAL_SESSION_STATE,
            // TODO(ANDR-2519): Can this be removed when md supports fs?
            //  Maybe not, because theses statuses might already be saved to the database...
            ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED,
        ],
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ForwardSecurityStatusType {
        companion object {
            const val STATIC_TEXT = 0
            const val MESSAGE_WITHOUT_FORWARD_SECURITY = 1
            const val FORWARD_SECURITY_RESET = 2
            const val FORWARD_SECURITY_ESTABLISHED = 3

            /**
             * As of version 1.1 this status is not created anymore
             */
            const val FORWARD_SECURITY_ESTABLISHED_RX = 4
            const val FORWARD_SECURITY_MESSAGES_SKIPPED = 5
            const val FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER = 6
            const val FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE = 7
            const val FORWARD_SECURITY_ILLEGAL_SESSION_STATE = 8

            // TODO(ANDR-2519): We could consider removing this when md supports fs,
            //  but it would require a DB migration and a filter in backup restore to ensure that
            //  no such messages are left in the database.
            const val FORWARD_SECURITY_DISABLED = 9
        }
    }

    @StatusType
    override val type: Int
        get() = TYPE

    override fun getParams() = mapOf(
        PARAM_STATUS_TYPE to statusType,
        PARAM_QUANTITY to quantity,
        PARAM_STATIC_TEXT to staticText,
    )

    companion object {
        const val TYPE = 3

        private const val PARAM_STATUS_TYPE = "status"
        private const val PARAM_QUANTITY = "quantity"
        private const val PARAM_STATIC_TEXT = "staticText"

        @JvmStatic
        fun create(
            @ForwardSecurityStatusType statusType: Int,
            quantity: Int = 0,
            staticText: String? = null,
        ) = ForwardSecurityStatusDataModel(
            statusType = statusType,
            quantity = quantity,
            staticText = staticText,
        )

        fun createFromParams(params: Map<String, Any?>) = create(
            statusType = (params[PARAM_STATUS_TYPE] as Number).toInt(),
            quantity = (params[PARAM_QUANTITY] as? Number)?.toInt() ?: 0,
            staticText = params[PARAM_STATIC_TEXT] as? String,
        )
    }
}
