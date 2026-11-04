package ch.threema.app.threemasafe

import ch.threema.common.toJsonArray
import ch.threema.domain.types.Identity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeGroupsSection private constructor(private val jsonArray: JsonArray) {
    fun getJsonArray(): JsonArray = jsonArray

    data class ThreemaSafeGroup(
        /**
         * The group id as lowercase hex string.
         */
        val id: String,
        /**
         * The group creator's Threema ID. Must be a valid Threema ID.
         */
        val creator: String,
        /**
         * The group name.
         */
        val groupname: String?,
        /**
         * The created at unix-ish timestamp in milliseconds. Must be greater or equal to 0.
         */
        val createdAt: Long,
        /**
         * The group members. If the list does not include the user itself, the group is marked as left.
         */
        val members: List<String>,
        /**
         * Whether the group has been deleted.
         */
        val deleted: Boolean,
        /**
         * The last update flag as unix-ish timestamp in milliseconds or null.
         */
        val lastUpdate: Long?,
        /**
         * Whether the conversation with this group should be protected (private chat) or not.
         */
        val private: Boolean,
    ) {
        init {
            require(idRegex.matches(id)) {
                "invalid id"
            }
            require(Identity.isValidIdentity(creator)) {
                "invalid creator identity"
            }
            require(createdAt >= 0) {
                "negative created at timestamp"
            }
            require(members.all(Identity::isValidIdentity)) {
                "invalid member identity"
            }
            require((lastUpdate ?: 0) >= 0) {
                "negative last update timestamp"
            }
        }

        companion object {
            private val idRegex = "[0-9a-f]{16}".toRegex()
        }
    }

    companion object {
        const val TAG_ID = "id"
        const val TAG_CREATOR = "creator"
        const val TAG_NAME = "groupname"
        const val TAG_CREATED_AT = "createdAt"
        const val TAG_MEMBERS = "members"
        const val TAG_DELETED = "deleted"
        const val TAG_LAST_UPDATE = "lastUpdate"
        const val TAG_PRIVATE = "private"

        fun create(groups: List<ThreemaSafeGroup>): ThreemaSafeGroupsSection =
            ThreemaSafeGroupsSection(
                groups.map(::toJsonObject).toJsonArray(),
            )

        private fun toJsonObject(threemaSafeGroup: ThreemaSafeGroup) = buildJsonObject {
            put(TAG_ID, threemaSafeGroup.id)
            put(TAG_CREATOR, threemaSafeGroup.creator)
            if (threemaSafeGroup.groupname != null) {
                put(TAG_NAME, threemaSafeGroup.groupname)
            }
            put(TAG_CREATED_AT, threemaSafeGroup.createdAt)
            put(TAG_MEMBERS, JsonArray(threemaSafeGroup.members.map(::JsonPrimitive)))
            put(TAG_DELETED, threemaSafeGroup.deleted)
            if (threemaSafeGroup.lastUpdate != null) {
                put(TAG_LAST_UPDATE, threemaSafeGroup.lastUpdate)
            }
            put(TAG_PRIVATE, threemaSafeGroup.private)
        }
    }
}
