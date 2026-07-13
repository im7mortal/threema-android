package ch.threema.storage.models.data.status

import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.data.status.StatusDataModel.StatusType

@ConsistentCopyVisibility
data class GroupStatusDataModel
private constructor(
    val statusType: GroupStatusType,
    val identity: IdentityString? = null,
    val pollName: String? = null,
    val newGroupName: String? = null,
) : StatusDataModel {

    enum class GroupStatusType(
        val type: Int,
        val requiresIdentity: Boolean = false,
        val requiresPollName: Boolean = false,
        val requiresNewGroupName: Boolean = false,
    ) {
        /** Group has been created */
        CREATED(0),

        /** Group has been renamed */
        RENAMED(1, requiresNewGroupName = true),

        /** Group picture has been updated */
        PROFILE_PICTURE_UPDATED(2),

        /** A member has been added */
        MEMBER_ADDED(3, requiresIdentity = true),

        /** A member has left the group */
        MEMBER_LEFT(4, requiresIdentity = true),

        /** A member has been kicked */
        MEMBER_KICKED(5, requiresIdentity = true),

        /** A group is now a notes group */
        IS_NOTES_GROUP(6),

        /** A group is not a notes group anymore */
        IS_PEOPLE_GROUP(7),

        /** A member has cast a vote */
        FIRST_VOTE(8, requiresPollName = true, requiresIdentity = true),

        /** A member has changed a vote */
        MODIFIED_VOTE(9, requiresPollName = true, requiresIdentity = true),

        /** A member has cast a vote anonymously */
        RECEIVED_VOTE(10, requiresPollName = true),

        /** Votes are complete */
        VOTES_COMPLETE(11, requiresPollName = true),

        /** Group description changed */
        GROUP_DESCRIPTION_CHANGED(12),

        /** The creator left the group */
        ORPHANED(13),
        ;

        companion object {
            fun fromInt(value: Int) =
                entries.first { it.type == value }
        }
    }

    @StatusType
    override val type
        get() = TYPE

    override fun getParams() = buildMap<String, Any?> {
        put(PARAM_STATUS, statusType.type)
        if (statusType.requiresIdentity && identity != null) {
            put(PARAM_IDENTITY, identity)
        }
        if (statusType.requiresPollName && pollName != null) {
            put(PARAM_POLL_NAME, pollName)
        }
        if (statusType.requiresNewGroupName && newGroupName != null) {
            put(PARAM_NEW_GROUP_NAME, newGroupName)
        }
    }

    companion object {
        const val TYPE = 4

        private const val PARAM_STATUS = "status"
        private const val PARAM_IDENTITY = "identity"
        private const val PARAM_POLL_NAME = "ballotName"
        private const val PARAM_NEW_GROUP_NAME = "newGroupName"

        // TODO(ANDR-4885) Convert GroupStatusDataModel into sealed class for improved type safety
        @JvmStatic
        fun create(
            type: GroupStatusType,
            identity: IdentityString? = null,
            pollName: String? = null,
            newGroupName: String? = null,
        ) = GroupStatusDataModel(
            statusType = type,
            identity = if (type.requiresIdentity) identity!! else null,
            pollName = if (type.requiresPollName) pollName!! else null,
            newGroupName = if (type.requiresNewGroupName) newGroupName!! else null,
        )

        fun createFromParams(params: Map<String, Any?>) = create(
            type = (params[PARAM_STATUS] as Number).toInt().let(GroupStatusType::fromInt),
            identity = params[PARAM_IDENTITY] as? String,
            pollName = params[PARAM_POLL_NAME] as? String,
            newGroupName = params[PARAM_NEW_GROUP_NAME] as? String,
        )
    }
}
