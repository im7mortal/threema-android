package ch.threema.data.datatypes

import android.os.Parcelable
import ch.threema.common.toByteArray
import ch.threema.common.toHexString
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.GroupIdentity
import java.nio.ByteOrder
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * The group identity uniquely identifies a group. It consists of the creator identity and the api-group-id.
 */
@Parcelize
@Serializable
data class GroupIdentity(
    val creatorIdentity: IdentityString,
    /** The api group id of the group. */
    val groupId: Long,
) : Parcelable {

    @IgnoredOnParcel
    val apiGroupIdHexString: String by lazy { apiGroupIdByteArray.toHexString() }

    /**
     * The group id as little endian byte array.
     */
    @IgnoredOnParcel
    val apiGroupIdByteArray: ByteArray by lazy { groupId.toByteArray(order = ByteOrder.LITTLE_ENDIAN) }

    fun toProtobuf(): GroupIdentity = GroupIdentity.newBuilder()
        .setCreatorIdentity(creatorIdentity)
        .setGroupId(groupId)
        .build()
}
