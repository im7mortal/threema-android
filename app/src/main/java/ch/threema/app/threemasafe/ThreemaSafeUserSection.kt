package ch.threema.app.threemasafe

import ch.threema.common.toJsonArray
import ch.threema.domain.types.Identity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeUserSection private constructor(private val jsonObject: JsonObject) {
    fun getJsonObject(): JsonObject = jsonObject

    enum class LinkItemType {
        PHONE,
        EMAIL,
    }

    data class LinkItem(
        val type: LinkItemType,
        val name: String?,
        val value: String,
    )

    sealed interface ProfilePicRelease {
        val values: List<String?>

        object Everyone : ProfilePicRelease {
            override val values: List<String?>
                get() = listOf(PROFILE_PICTURE_RELEASE_ALL_PLACEHOLDER)
        }

        data class AllowList(override val values: List<String>) : ProfilePicRelease {
            init {
                require(values.isNotEmpty()) {
                    "allow list is empty"
                }

                require(values.all(Identity::isValidIdentity)) {
                    "invalid identity in allow list"
                }
            }
        }

        object None : ProfilePicRelease {
            override val values: List<String?>
                get() = listOf(null)
        }

        companion object {
            const val PROFILE_PICTURE_RELEASE_ALL_PLACEHOLDER = "*"
        }
    }

    data class ThreemaSafeUserInfo(
        val privateKey: String,
        val nickname: String,
        val profilePic: String?,
        val profilePicRelease: ProfilePicRelease,
        val links: List<LinkItem>,
    ) {
        init {
            require(privateKeyRegex.matches(privateKey)) {
                "invalid private key"
            }
            if (profilePic != null) {
                require(profilePicRegex.matches(profilePic)) {
                    "invalid profile picture"
                }
            }
        }

        companion object {
            private val privateKeyRegex = "^[A-Za-z0-9+/]{43}=$".toRegex()
            private val profilePicRegex = "^[A-Za-z0-9+/]*={0,2}$".toRegex()
        }
    }

    companion object {
        const val TAG_PRIVATE_KEY = "privatekey"
        const val TAG_NICKNAME = "nickname"
        const val TAG_PROFILE_PIC = "profilePic"
        const val TAG_PROFILE_PIC_RELEASE = "profilePicRelease"
        const val TAG_LINKS = "links"
        const val TAG_LINK_TYPE = "type"
        const val TAG_LINK_NAME = "name"
        const val TAG_LINK_VALUE = "value"
        const val TAG_LINK_TYPE_MOBILE = "mobile"
        const val TAG_LINK_TYPE_EMAIL = "email"

        fun create(user: ThreemaSafeUserInfo): ThreemaSafeUserSection {
            val jsonObject = buildJsonObject {
                put(TAG_PRIVATE_KEY, user.privateKey)
                put(TAG_NICKNAME, user.nickname)
                if (user.profilePic != null) {
                    put(TAG_PROFILE_PIC, user.profilePic)
                }
                put(TAG_PROFILE_PIC_RELEASE, user.profilePicRelease.values.map(::JsonPrimitive).toJsonArray())
                put(TAG_LINKS, user.links.map(::toJsonObject).toJsonArray())
            }
            return ThreemaSafeUserSection(jsonObject)
        }

        private fun toJsonObject(linkItem: LinkItem): JsonObject = buildJsonObject {
            val linkTypeName = when (linkItem.type) {
                LinkItemType.PHONE -> TAG_LINK_TYPE_MOBILE
                LinkItemType.EMAIL -> TAG_LINK_TYPE_EMAIL
            }
            put(TAG_LINK_TYPE, linkTypeName)
            if (linkItem.name != null) {
                put(TAG_LINK_NAME, linkItem.name)
            }
            put(TAG_LINK_VALUE, linkItem.value)
        }
    }
}
