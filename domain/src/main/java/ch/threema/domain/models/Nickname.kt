package ch.threema.domain.models

import ch.threema.common.truncateUTF8String
import ch.threema.domain.protocol.csp.ProtocolDefines

// TODO(ANDR-3180): Consider using this class everywhere where the nickname is used, instead of a plain string. For this,
//  we first need to define in the protocol what the maximum length should be and whether and how it should be enforced.
class Nickname(
    nickname: String,
) {
    val nickname: String = nickname.truncateUTF8String(MAX_BYTE_LENGTH)

    override fun equals(other: Any?): Boolean =
        (other as? Nickname)?.nickname == nickname

    override fun hashCode() = nickname.hashCode()

    override fun toString() = nickname

    companion object {
        const val MAX_BYTE_LENGTH = ProtocolDefines.PUSH_FROM_LEN
    }
}
