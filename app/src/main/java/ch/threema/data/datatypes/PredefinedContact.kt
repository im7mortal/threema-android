package ch.threema.data.datatypes

import ch.threema.app.BuildFlavor
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.IdentityString

/**
 * A predefined contact can be added to the contact list and is automatically
 * initialized with its identity, nickname, hard-coded public key and the
 * verification level fully verified. Once a predefined contact is in the
 * contact list, it is treated like any other normal contact (with editable
 * properties like first and last name, etc.).
 *
 * A predefined contact may be marked special meaning it follows special logic.
 * These are also known as _Special Contacts_. Even though special contacts
 * should not normally appear in the contact list, there's nothing stopping a
 * user from adding a special contact to their contact list. While they are
 * treated like normal contacts in the contact list, depending on the special
 * handling logic, it may not be possible to send or receive normal messages
 * from them.
 */
@OptIn(ExperimentalStdlibApi::class) // For hexToByteArray
data class PredefinedContact(
    /** Threema ID of the predefined contact. */
    val identity: IdentityString,

    /** Whether the predefined contact is marked as a _special contact_. */
    val isSpecial: Boolean,

    /** Public key of the predefined contact. */
    val publicKey: ByteArray,

    /** Nickname of the contact (without `~` prefix). */
    val nickname: String,
) {

    /**
     * Convert the predefined contact to a basic contact. Note that the verification level is set to "full" and the feature mask to 0. With this
     * conversion, the nickname is lost.
     */
    fun toBasicContact(): BasicContact = BasicContact(
        identity = identity,
        publicKey = publicKey,
        featureMask = 0u,
        identityState = IdentityState.ACTIVE,
        identityType = IdentityType.REGULAR,
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
    )

    companion object {
        const val THREEMA_CHANNEL_IDENTITY: IdentityString = "*THREEMA"
        const val THREEMA_SUPPORT_IDENTITY: IdentityString = "*SUPPORT"
        const val SPECIAL_CONTACT_3MAPUSH_IDENTITY: IdentityString = "*3MAPUSH"
        const val SPECIAL_CONTACT_3MAW0RK_IDENTITY: IdentityString = "*3MAW0RK"

        /** Map containing all predefined contacts for current build environment. */
        private val all: Map<IdentityString, PredefinedContact> by lazy { getAllForCurrentEnvironment() }

        /**
         * The support contact with identity [THREEMA_SUPPORT_IDENTITY]. Note that on onprem builds this contact is null.
         */
        val supportContact: PredefinedContact? by lazy { all[THREEMA_SUPPORT_IDENTITY] }

        /**
         * The threema channel contact with identity [THREEMA_CHANNEL_IDENTITY]. Note that on onprem build this contact is null.
         */
        val threemaChannelContact: PredefinedContact? by lazy { all[THREEMA_CHANNEL_IDENTITY] }

        /**
         * Return whether the specified identity is a predefined contact.
         */
        @JvmStatic
        fun isPredefinedContact(identity: IdentityString): Boolean = identity in all

        /**
         * Return the predefined contact with [identity] if it is a predefined contact.
         */
        @JvmStatic
        fun getPredefinedContact(identity: IdentityString): PredefinedContact? =
            all[identity]

        /**
         * Return whether the specified identity is a special contact.
         */
        @JvmStatic
        fun isSpecialContact(identity: IdentityString): Boolean =
            all[identity]?.isSpecial == true

        /**
         * Return the special contact with [identity] if it is a special contact.
         */
        @JvmStatic
        fun getSpecialContact(identity: IdentityString): PredefinedContact? =
            all[identity]
                .takeIf { predefinedContact -> predefinedContact?.isSpecial == true }

        @JvmStatic
        fun getAllPredefinedContacts(): Collection<PredefinedContact> =
            all.values

        /**
         * Return all predefined contacts for current build environment.
         */
        private fun getAllForCurrentEnvironment(): Map<IdentityString, PredefinedContact> =
            when (BuildFlavor.current.buildEnvironment) {
                BuildFlavor.BuildEnvironment.LIVE -> buildProduction()
                BuildFlavor.BuildEnvironment.SANDBOX -> buildSandbox()
                BuildFlavor.BuildEnvironment.ONPREM -> emptyMap() // TODO(ONPREM-164)
            }

        private fun buildProduction(): Map<IdentityString, PredefinedContact> = listOf(
            PredefinedContact(
                identity = SPECIAL_CONTACT_3MAPUSH_IDENTITY,
                isSpecial = true,
                publicKey = "fd711e1a0db0e2f03fcaab6c43da2575b9513664a62a12bd0728d87f7125cc24".hexToByteArray(),
                nickname = "Threema Push",
            ),
            PredefinedContact(
                identity = "*3MATOKN",
                isSpecial = false,
                publicKey = "04884d12d668f855d00d71fb1d9d413c95f271312f7e077846af671875c4101b".hexToByteArray(),
                nickname = "Threema Token",
            ),
            PredefinedContact(
                identity = SPECIAL_CONTACT_3MAW0RK_IDENTITY,
                isSpecial = true,
                publicKey = "c0e8ad0f50c5c7315c402d3dc26db169408c117613e9b852d3d6c0e87fca536b".hexToByteArray(),
                nickname = "Threema Work Delta Sync",
            ),
            PredefinedContact(
                identity = "*3MAWORK",
                isSpecial = false,
                publicKey = "9aa0a72a8fb6f0cc53727fea6096f1b7b0ebefcc2650ad39a1e54837bba0bc4b".hexToByteArray(),
                nickname = "Threema Work Channel",
            ),
            PredefinedContact(
                identity = "*BETAFBK",
                isSpecial = false,
                publicKey = "5684d6dcd32a16488df8371095fc9a1fc25baeb6b97366d99fdf2aba00e2bc5c".hexToByteArray(),
                nickname = "Threema Beta Feedback",
            ),
            PredefinedContact(
                identity = "*MY3DATA",
                isSpecial = false,
                publicKey = "3b01854f24736e2d0d2dc387eaf2c0273c5049052147132369bf3960d0a0bf02".hexToByteArray(),
                nickname = "My Threema Data",
            ),
            PredefinedContact(
                identity = THREEMA_SUPPORT_IDENTITY,
                isSpecial = false,
                publicKey = "0f944d18324b2132c61d8e40afce60a0ebd701bb11e89be94972d4229e94722a".hexToByteArray(),
                nickname = "Threema Support",
            ),
            PredefinedContact(
                identity = THREEMA_CHANNEL_IDENTITY,
                isSpecial = false,
                publicKey = "3a38650c681435bd1fb8498e213a2919b09388f5803aa44640e0f706326a865c".hexToByteArray(),
                nickname = "Threema Channel",
            ),
        ).associateBy { it.identity }

        private fun buildSandbox(): Map<IdentityString, PredefinedContact> = listOf(
            PredefinedContact(
                identity = SPECIAL_CONTACT_3MAPUSH_IDENTITY,
                isSpecial = true,
                publicKey = "fd711e1a0db0e2f03fcaab6c43da2575b9513664a62a12bd0728d87f7125cc24".hexToByteArray(),
                nickname = "Threema Push",
            ),
            PredefinedContact(
                identity = SPECIAL_CONTACT_3MAW0RK_IDENTITY,
                isSpecial = true,
                publicKey = "c79d9e0f70342e653b0c6df027af8c8681db40e11bf556dd33ec78ee6f810c6d".hexToByteArray(),
                nickname = "Threema Work Delta Sync",
            ),
            PredefinedContact(
                identity = "*3MAWORK",
                isSpecial = false,
                publicKey = "9aa0a72a8fb6f0cc53727fea6096f1b7b0ebefcc2650ad39a1e54837bba0bc4b".hexToByteArray(),
                nickname = "Threema Work Channel",
            ),
            PredefinedContact(
                identity = "*MY3DATA",
                isSpecial = false,
                publicKey = "83adfee6558b68ae3cd6bbe2a33f4e4409d5624a7cea23a18975aea6272a0070".hexToByteArray(),
                nickname = "My Threema Data",
            ),
            PredefinedContact(
                identity = THREEMA_SUPPORT_IDENTITY,
                isSpecial = false,
                publicKey = "0f944d18324b2132c61d8e40afce60a0ebd701bb11e89be94972d4229e94722a".hexToByteArray(),
                nickname = "Threema Support",
            ),
            PredefinedContact(
                identity = THREEMA_CHANNEL_IDENTITY,
                isSpecial = false,
                publicKey = "3a38650c681435bd1fb8498e213a2919b09388f5803aa44640e0f706326a865c".hexToByteArray(),
                nickname = "Threema Channel",
            ),
        ).associateBy { it.identity }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedContact

        if (isSpecial != other.isSpecial) return false
        if (identity != other.identity) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (nickname != other.nickname) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isSpecial.hashCode()
        result = 31 * result + identity.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + nickname.hashCode()
        return result
    }
}
