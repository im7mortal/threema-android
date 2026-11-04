package ch.threema.app.threemasafe

import ch.threema.common.toJsonArray
import ch.threema.domain.types.Identity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeDistributionListsSection private constructor(private val jsonArray: JsonArray) {
    fun getJsonArray(): JsonArray = jsonArray

    data class ThreemaSafeDistributionList(
        val id: String,
        val name: String,
        val createdAt: Long,
        val members: List<String>,
        val lastUpdate: Long,
        val private: Boolean,
    ) {
        init {
            require(idRegex.matches(id)) {
                "invalid id"
            }
            require(createdAt >= 0) {
                "negative created at timestamp"
            }
            require(members.all(Identity::isValidIdentity)) {
                "invalid member identity"
            }
            require(lastUpdate >= 0) {
                "negative last update timestamp"
            }
        }

        companion object {
            private val idRegex = "[0-9a-f]{16}".toRegex()
        }
    }

    companion object {
        const val TAG_ID = "id"
        const val TAG_NAME = "name"
        const val TAG_CREATED_AT = "createdAt"
        const val TAG_MEMBERS = "members"
        const val TAG_LAST_UPDATE = "lastUpdate"
        const val TAG_PRIVATE = "private"

        fun create(distributionLists: List<ThreemaSafeDistributionList>): ThreemaSafeDistributionListsSection =
            ThreemaSafeDistributionListsSection(
                distributionLists.map(::toJsonObject).toJsonArray(),
            )

        private fun toJsonObject(distributionList: ThreemaSafeDistributionList): JsonObject = buildJsonObject {
            put(TAG_ID, distributionList.id)
            put(TAG_NAME, distributionList.name)
            put(TAG_CREATED_AT, distributionList.createdAt)
            put(TAG_MEMBERS, JsonArray(distributionList.members.map(::JsonPrimitive)))
            put(TAG_LAST_UPDATE, distributionList.lastUpdate)
            put(TAG_PRIVATE, distributionList.private)
        }
    }
}
