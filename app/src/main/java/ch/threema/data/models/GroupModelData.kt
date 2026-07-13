package ch.threema.data.models

import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.IdColor
import ch.threema.domain.models.UserState
import ch.threema.domain.types.IdentityString
import java.time.Instant
import java.util.Collections

data class GroupModelData(
    @JvmField val groupIdentity: GroupIdentity,
    @JvmField val name: String?,
    @JvmField val createdAt: Instant,
    /** Currently not used. Might be used for periodic group sync in the future. TODO(SE-146) */
    @JvmField val synchronizedAt: Instant?,
    /** Last update flag. */
    @JvmField val lastUpdate: Instant?,
    /** The conversation visibility of this group chat. */
    @JvmField val conversationVisibility: ConversationVisibility,
    /**
     * The precomputed id color if it is already known. If the id color is not set, it will be
     * computed lazily. Access the id color with [idColor].
     */
    private val precomputedIdColor: IdColor = IdColor.invalid(),
    @JvmField val groupDescription: String?,
    @JvmField val groupDescriptionChangedAt: Instant?,
    /**
     * The group members' identities. This does not include the user's and creator's identity.
     *
     * Note that this set cannot be modified.
     */
    @JvmField val otherMembers: Set<IdentityString>,
    @JvmField val userState: UserState,
    @JvmField val notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride?,
) {
    init {
        require(groupIdentity.creatorIdentity !in otherMembers) {
            "the creator identity must not be included in member list"
        }
    }

    /**
     * Is true if the user state is set to member, false if the user has left the group or was
     * kicked.
     */
    val isMember: Boolean
        get() = userState == UserState.MEMBER

    /**
     * The group member's identities including the creator identity. Note that the user's identity is not included unless it is the creator.
     */
    val otherMembersAndCreator: Set<IdentityString> =
        otherMembers + groupIdentity.creatorIdentity

    /**
     * The group members' identities. This includes the user's identity and the creator's identity.
     *
     * Note that this set cannot be modified.
     *
     * @param myIdentity the user's identity
     */
    fun getAllMembers(myIdentity: IdentityString): Set<IdentityString> {
        // Note that the unmodifiable set is used to enforce immutability in java
        return Collections.unmodifiableSet(
            buildSet {
                if (isMember) {
                    add(myIdentity)
                }
                add(groupIdentity.creatorIdentity)
                addAll(otherMembers)
            },
        )
    }

    /**
     * The color index.
     */
    val idColor: IdColor by lazy {
        if (precomputedIdColor.isValid) {
            precomputedIdColor
        } else {
            IdColor.ofGroup(groupIdentity)
        }
    }
}
