package ch.threema.app.multidevice.linking

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ServiceManager
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactService
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.app.tasks.ReflectUserProfileIdentityLinksTask
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.common.BlobData
import ch.threema.protobuf.common.DeltaImage
import ch.threema.protobuf.common.Identities
import ch.threema.protobuf.common.Image
import ch.threema.protobuf.common.blob
import ch.threema.protobuf.common.blobData
import ch.threema.protobuf.common.deltaImage
import ch.threema.protobuf.common.groupIdentity
import ch.threema.protobuf.common.identities
import ch.threema.protobuf.common.image
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.join.EssentialData
import ch.threema.protobuf.d2d.join.EssentialDataKt.augmentedContact
import ch.threema.protobuf.d2d.join.EssentialDataKt.augmentedDistributionList
import ch.threema.protobuf.d2d.join.EssentialDataKt.augmentedGroup
import ch.threema.protobuf.d2d.join.EssentialDataKt.deviceGroupData
import ch.threema.protobuf.d2d.join.EssentialDataKt.identityData
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ContactKt
import ch.threema.protobuf.d2d.sync.ContactKt.readReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.typingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility as ProtocolsConversationVisibility
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.protobuf.d2d.sync.GroupKt
import ch.threema.protobuf.d2d.sync.MdmParameters
import ch.threema.protobuf.d2d.sync.MdmParametersKt.parameter
import ch.threema.protobuf.d2d.sync.ReadReceiptPolicy
import ch.threema.protobuf.d2d.sync.Settings
import ch.threema.protobuf.d2d.sync.ThreemaWorkCredentials
import ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy
import ch.threema.protobuf.d2d.sync.UserProfile
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.d2d.sync.distributionList
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.d2d.sync.mdmParameters
import ch.threema.protobuf.d2d.sync.settings
import ch.threema.protobuf.d2d.sync.threemaWorkCredentials
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.toProtobuf
import ch.threema.storage.models.DistributionListModel
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import java.nio.ByteBuffer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("DeviceLinkingDataCollector")

data class DeviceLinkingData(val blobs: Sequence<BlobData>, val essentialDataProvider: EssentialDataProvider)

class BlobDataProvider(private val blobId: ByteArray, private val dataProvider: () -> ByteArray?) {
    private enum class BlobDataProviderState {
        SUCCESS,
        FAIL,
        NOT_USED,
    }

    private var state = BlobDataProviderState.NOT_USED

    /**
     * Get this providers [BlobData]. Note that this method must only be called once.
     *
     * @throws [IllegalStateException] if this method already has been called
     * @return [BlobData] or null if no blobId or actual data is available
     */
    fun get(): BlobData? {
        check(state == BlobDataProviderState.NOT_USED) {
            "Cannot get the blob data several times"
        }

        val blobData = constructBlobData()

        state = if (blobData != null) {
            BlobDataProviderState.SUCCESS
        } else {
            BlobDataProviderState.FAIL
        }

        return blobData
    }

    private fun constructBlobData(): BlobData? {
        logger.debug("Invoke blob data provider")
        return dataProvider.invoke()?.toByteString()?.let { blobData ->
            blobData {
                id = blobId.toByteString()
                data = blobData
            }
        }
    }

    /**
     * Check whether the blob data provider has been successfully used or not.
     * @throws [IllegalStateException] if the blob data provider has not been used yet
     */
    fun hasBeenSuccessfullyUsed(): Boolean {
        return when (state) {
            BlobDataProviderState.SUCCESS -> true
            BlobDataProviderState.FAIL -> false
            BlobDataProviderState.NOT_USED -> throw IllegalStateException("Blob data provider has not yet been used")
        }
    }
}

class AugmentedContactProvider(
    private val augmentedContact: EssentialData.AugmentedContact,
    private val contactDefinedProfilePictureProvider: BlobDataProvider?,
    private val userDefinedProfilePictureProvider: BlobDataProvider?,
) {
    /**
     * Get the augmented contact.
     *
     * @throws IllegalStateException if the profile picture providers have not been used at all
     */
    fun get(): EssentialData.AugmentedContact {
        val invalidContactDefinedProfilePicture = contactDefinedProfilePictureProvider?.hasBeenSuccessfullyUsed() == false
        val invalidUserDefinedProfilePicture = userDefinedProfilePictureProvider?.hasBeenSuccessfullyUsed() == false
        if (invalidContactDefinedProfilePicture || invalidUserDefinedProfilePicture) {
            // In case one of the profile pictures could not be used successfully, we remove it from the contact. Otherwise, the contact would contain
            // an invalid blob id.
            val contactBuilder = augmentedContact.contact.toBuilder()
            if (invalidContactDefinedProfilePicture) {
                logger.warn("Skipping contact defined profile picture for {}", augmentedContact.contact.identity)
                contactBuilder.clearContactDefinedProfilePicture()
            }
            if (invalidUserDefinedProfilePicture) {
                logger.warn("Skipping user defined profile picture for {}", augmentedContact.contact.identity)
                contactBuilder.clearUserDefinedProfilePicture()
            }
            val augmentedContactBuilder = augmentedContact.toBuilder()
            augmentedContactBuilder.setContact(contactBuilder)
            return augmentedContactBuilder.build()
        }
        return augmentedContact
    }
}

class AugmentedGroupProvider(
    private val augmentedGroup: EssentialData.AugmentedGroup,
    private val groupProfilePictureProvider: BlobDataProvider?,
) {
    /**
     * Get the augmented group.
     *
     * @throws IllegalStateException if the profile picture provider has not been used at all
     */
    fun get(): EssentialData.AugmentedGroup {
        if (groupProfilePictureProvider?.hasBeenSuccessfullyUsed() == false) {
            // In case the group profile picture could be used successfully, we remove it from the group. Otherwise, the group would contain an
            // invalid blob id.
            logger.warn("Skipping group profile picture")
            val groupBuilder = augmentedGroup.group.toBuilder()
            groupBuilder.clearProfilePicture()
            val augmentedGroupBuilder = augmentedGroup.toBuilder()
            augmentedGroupBuilder.setGroup(groupBuilder)
            return augmentedGroupBuilder.build()
        }
        return augmentedGroup
    }
}

/**
 * This provider contains the required information to create the essential data.
 *
 * The [essentialDataBuilder] must be complete except for augmented contacts and groups.
 * The provided [augmentedContactProviders] and [augmentedGroupProviders] will be used to add the augmented contacts and groups to the essential data
 * builder. Note that during this process, the required blobs for the contacts and groups should already be sent. In case some of them could not have
 * been sent successfully (because the files were corrupt), the augmented contacts or groups will be updated to not contain these blobs.
 */
class EssentialDataProvider(
    private val essentialDataBuilder: EssentialData.Builder,
    private val augmentedContactProviders: Collection<AugmentedContactProvider>,
    private val augmentedGroupProviders: Collection<AugmentedGroupProvider>,
) {
    fun get(): EssentialData {
        essentialDataBuilder.addAllContacts(augmentedContactProviders.map(AugmentedContactProvider::get))
        essentialDataBuilder.addAllGroups(augmentedGroupProviders.map(AugmentedGroupProvider::get))
        return essentialDataBuilder.build()
    }
}

class DeviceLinkingDataCollector(
    serviceManager: ServiceManager,
) : KoinComponent {
    private val identityStore by lazy { serviceManager.identityStore }
    private val preferenceService by lazy { serviceManager.preferenceService }
    private val userService by lazy { serviceManager.userService }
    private val contactService by lazy { serviceManager.contactService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }
    private val distributionListService by lazy { serviceManager.distributionListService }
    private val deviceCookieManager: DeviceCookieManager by inject()
    private val synchronizedSettingsService by lazy { serviceManager.synchronizedSettingsService }
    private val blockedIdentitiesService by lazy { serviceManager.blockedIdentitiesService }
    private val excludeFromSyncService by lazy { serviceManager.excludedSyncIdentitiesService }
    private val fileService by lazy { serviceManager.fileService }
    private val conversationCategoryService by lazy { serviceManager.conversationCategoryService }
    private val conversationService by lazy { serviceManager.conversationService }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val licenseService by lazy { serviceManager.licenseService }
    private val appRestrictions: AppRestrictions by inject()
    private val context
        get() = ThreemaApplication.getAppContext()

    @WorkerThread
    fun collectData(dgk: ByteArray): DeviceLinkingData {
        val blobDataProviders = mutableListOf<BlobDataProvider>()
        val augmentedContactProviders = mutableListOf<AugmentedContactProvider>()
        val augmentedGroupProviders = mutableListOf<AugmentedGroupProvider>()

        val essentialDataBuilder = EssentialData.newBuilder()

        logger.trace("Collect identity data")
        essentialDataBuilder.setIdentityData(collectIdentityData())

        logger.trace("Collect device group data")
        essentialDataBuilder.setDeviceGroupData(
            deviceGroupData {
                this.dgk = dgk.toByteString()
            },
        )

        logger.trace("Collect user profile")
        val (userProfileBlobProvider, userProfileData) = collectUserProfile()
        userProfileBlobProvider?.let {
            blobDataProviders.add(it)
        }
        essentialDataBuilder.setUserProfile(userProfileData)

        logger.trace("Collect settings")
        essentialDataBuilder.setSettings(collectSettings())

        logger.trace("Collect contacts")
        collectContacts().forEach { (contactBlobDataProviders, augmentedContactProvider) ->
            blobDataProviders.addAll(contactBlobDataProviders)
            augmentedContactProviders.add(augmentedContactProvider)
        }

        logger.trace("Collect groups")
        collectGroups().forEach { (groupBlobDataProviders, augmentedGroupProvider) ->
            blobDataProviders.addAll(groupBlobDataProviders)
            augmentedGroupProviders.add(augmentedGroupProvider)
        }

        if (BuildConfig.MD_SYNC_DISTRIBUTION_LISTS) {
            logger.trace("Collect distribution lists")
            essentialDataBuilder.addAllDistributionLists(collectDistributionLists())
        } else {
            logger.trace("Skip collection of distribution lists")
            essentialDataBuilder.clearDistributionLists()
        }

        logger.trace("Collect csp nonce hashes")
        essentialDataBuilder.addAllCspHashedNonces(collectCspNonceHashes())

        logger.trace("Collect d2d nonce hashes")
        essentialDataBuilder.addAllD2DHashedNonces(collectD2dNonceHashes())

        // work
        if (ConfigUtils.isWorkBuild()) {
            logger.trace("Collect work credentials")
            essentialDataBuilder.setWorkCredentials(
                collectWorkCredentials()
                    ?: throw IllegalStateException("No work credentials available in work build"),
            )
            collectMdmParameters()?.let { mdmParameters ->
                essentialDataBuilder.setMdmParameters(mdmParameters)
            }
        }

        logger.debug("Number of blobDataProviders: {}", blobDataProviders.size)
        val blobsSequence = blobDataProviders
            .asSequence()
            .mapNotNull { it.get() }

        val essentialDataProvider = EssentialDataProvider(
            essentialDataBuilder = essentialDataBuilder,
            augmentedContactProviders = augmentedContactProviders,
            augmentedGroupProviders = augmentedGroupProviders,
        )

        return DeviceLinkingData(blobsSequence, essentialDataProvider)
    }

    private fun collectIdentityData(): EssentialData.IdentityData {
        return identityData {
            identity = identityStore.getIdentityString()!!
            ck = identityStore.getPrivateKey()!!.toByteString()
            cspDeviceCookie = deviceCookieManager.getOrCreateDeviceCookie().toByteString()
            cspServerGroup = identityStore.getServerGroup()!!
        }
    }

    private fun collectUserProfile(): Pair<BlobDataProvider?, UserProfile> {
        return collectUserProfilePicture().let { profilePictureData ->
            profilePictureData?.first to userProfile {
                nickname = identityStore.getPublicNickname()
                profilePictureData?.second?.let {
                    profilePicture = it
                }
                profilePictureShareWith = collectProfilePictureShareWith()
                identityLinks = collectIdentityLinks()
                if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
                    val availabilityStatus = preferenceService.getAvailabilityStatus()
                    if (availabilityStatus != null) {
                        workAvailabilityStatus = availabilityStatus.toProtocolModel()
                    }
                }
            }
        }
    }

    private fun collectUserProfilePicture(): Pair<BlobDataProvider, DeltaImage>? {
        val profilePictureData = userService.uploadUserProfilePictureOrGetPreviousUploadData()

        val hasProfilePicture = profilePictureData.blobId != null &&
            !profilePictureData.blobId.contentEquals(ch.threema.storage.models.ContactModel.NO_PROFILE_PICTURE_BLOB_ID)

        return if (hasProfilePicture) {
            val blobMeta = blob {
                id = profilePictureData.blobId.toByteString()
                nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                key = profilePictureData.encryptionKey.toByteString()
                assertValidTimestamp(profilePictureData.uploadedAt, "Profile picture uploadedAt")
                uploadedAt = profilePictureData.uploadedAt
            }

            val profilePicture = deltaImage {
                updated = image {
                    type = Image.Type.JPEG
                    blob = blobMeta
                }
            }

            val blobDataProvider = BlobDataProvider(profilePictureData.blobId) {
                profilePictureData.profilePicture.bytes
            }

            blobDataProvider to profilePicture
        } else {
            null
        }
    }

    private fun collectProfilePictureShareWith(): UserProfile.ProfilePictureShareWith {
        val policy = contactService.profilePictureSharePolicy

        return profilePictureShareWith {
            when (policy.policy) {
                ContactService.ProfilePictureSharePolicy.Policy.NOBODY -> nobody = unit {}
                ContactService.ProfilePictureSharePolicy.Policy.EVERYONE -> everyone = unit {}
                ContactService.ProfilePictureSharePolicy.Policy.ALLOW_LIST -> {
                    allowList = identities { identities += policy.allowedIdentities }
                }
            }
        }
    }

    private fun collectIdentityLinks(): UserProfile.IdentityLinks {
        return ReflectUserProfileIdentityLinksTask.getUserProfileSyncIdentityLinks(userService)
    }

    private fun collectSettings(): Settings {
        return settings {
            contactSyncPolicy = if (synchronizedSettingsService.isSyncContacts()) {
                Settings.ContactSyncPolicy.SYNC
            } else {
                Settings.ContactSyncPolicy.NOT_SYNCED
            }
            unknownContactPolicy = if (synchronizedSettingsService.isBlockUnknown()) {
                Settings.UnknownContactPolicy.BLOCK_UNKNOWN
            } else {
                Settings.UnknownContactPolicy.ALLOW_UNKNOWN
            }
            readReceiptPolicy = if (synchronizedSettingsService.areReadReceiptsEnabled()) {
                ReadReceiptPolicy.SEND_READ_RECEIPT
            } else {
                ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }
            typingIndicatorPolicy = if (synchronizedSettingsService.isTypingIndicatorEnabled()) {
                TypingIndicatorPolicy.SEND_TYPING_INDICATOR
            } else {
                TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
            o2OCallPolicy = if (synchronizedSettingsService.isVoipEnabled()) {
                Settings.O2oCallPolicy.ALLOW_O2O_CALL
            } else {
                Settings.O2oCallPolicy.DENY_O2O_CALL
            }
            o2OCallConnectionPolicy = if (synchronizedSettingsService.isForceTURN()) {
                Settings.O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION
            } else {
                Settings.O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION
            }
            o2OCallVideoPolicy = if (synchronizedSettingsService.areVideoCallsEnabled()) {
                Settings.O2oCallVideoPolicy.ALLOW_VIDEO
            } else {
                Settings.O2oCallVideoPolicy.DENY_VIDEO
            }
            groupCallPolicy = if (synchronizedSettingsService.areGroupCallsEnabled()) {
                Settings.GroupCallPolicy.ALLOW_GROUP_CALL
            } else {
                Settings.GroupCallPolicy.DENY_GROUP_CALL
            }
            screenshotPolicy = if (synchronizedSettingsService.areScreenshotsDisabled()) {
                Settings.ScreenshotPolicy.DENY_SCREENSHOT
            } else {
                Settings.ScreenshotPolicy.ALLOW_SCREENSHOT
            }
            keyboardDataCollectionPolicy = if (synchronizedSettingsService.isIncognitoKeyboardRequested()) {
                Settings.KeyboardDataCollectionPolicy.DENY_DATA_COLLECTION
            } else {
                Settings.KeyboardDataCollectionPolicy.ALLOW_DATA_COLLECTION
            }
            blockedIdentities = collectBlockedIdentities()
            excludeFromSyncIdentities = collectExcludeFromSyncIdentities()
        }
    }

    private fun collectBlockedIdentities(): Identities {
        return identities {
            identities += blockedIdentitiesService.getAllBlockedIdentities()
        }
    }

    private fun collectExcludeFromSyncIdentities(): Identities {
        return identities {
            identities += excludeFromSyncService.getExcludedIdentities()
        }
    }

    private data class ConversationStats(
        val isArchived: Boolean,
        val isPinned: Boolean,
    )

    private fun collectContacts(): List<Pair<List<BlobDataProvider>, AugmentedContactProvider>> {
        return contactModelRepository.getAll()
            .mapNotNull(this::getAndValidateData)
            .map { contactModelData ->
                mapToAugmentedContact(contactModelData)
            }
            .also { logger.trace("{} contacts", it.size) }
    }

    private fun mapToAugmentedContact(
        contactModelData: ContactModelData,
    ): Pair<List<BlobDataProvider>, AugmentedContactProvider> {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val conversationId = ContactConversationId(identity = contactModelData.identity)

        val contactDefinedProfilePictureInfo: Pair<BlobDataProvider, DeltaImage>? = collectContactDefinedProfilePicture(contactModelData)
        val userDefinedProfilePictureInfo: Pair<BlobDataProvider, DeltaImage>? = collectUserDefinedProfilePicture(contactModelData)

        val contact = contact {
            identity = contactModelData.identity
            publicKey = contactModelData.publicKey.toByteString()
            assertValidTimestamp(contactModelData.createdAt.toEpochMilli(), "Contact createdAt (${contactModelData.identity})")
            createdAt = contactModelData.createdAt.toEpochMilli()
            firstName = contactModelData.firstName
            lastName = contactModelData.lastName
            nickname = contactModelData.nickname ?: ""
            verificationLevel = mapVerificationLevel(contactModelData)
            workVerificationLevel = mapWorkVerificationLevel(contactModelData)
            identityType = mapIdentityState(contactModelData)
            acquaintanceLevel = mapAcquaintanceLevel(contactModelData)
            activityState = mapActivityState(contactModelData)
            featureMask = contactModelData.featureMask.toLong()
            syncState = collectSyncState(contactModelData)
            readReceiptPolicyOverride = mapReadReceiptPolicyOverride(contactModelData)
            typingIndicatorPolicyOverride = mapTypingIndicatorPolicyOverride(contactModelData)
            notificationTriggerPolicyOverride = contactModelData.notificationTriggerPolicyOverride.toProtobuf()
            deprecatedNotificationSoundPolicyOverride = ContactKt.deprecatedNotificationSoundPolicyOverride {
                default = unit {}
            }

            if (contactDefinedProfilePictureInfo != null) {
                blobDataProviders.add(contactDefinedProfilePictureInfo.first)
                contactDefinedProfilePicture = contactDefinedProfilePictureInfo.second
            }

            if (userDefinedProfilePictureInfo != null) {
                blobDataProviders.add(userDefinedProfilePictureInfo.first)
                userDefinedProfilePicture = userDefinedProfilePictureInfo.second
            }

            conversationCategory =
                if (conversationCategoryService.isMarkedAsPrivate(conversationId)) {
                    ConversationCategory.PROTECTED
                } else {
                    ConversationCategory.DEFAULT
                }

            conversationVisibility = mapConversationVisibility(contactModelData.conversationVisibility)

            if (ConfigUtils.isWorkBuild() && contactModelData.workLastFullSyncAt != null) {
                workLastFullSyncAt = contactModelData.workLastFullSyncAt.toEpochMilli()
            }

            if (BuildConfig.AVAILABILITY_STATUS_ENABLED && contactModelData.availabilityStatus != AvailabilityStatus.None) {
                workAvailabilityStatus = contactModelData.availabilityStatus.toProtocolModel()
            }
        }

        val augmentedContact = augmentedContact {
            this.contact = contact
            contactService.getLastUpdate(contactModelData.identity)?.let {
                assertValidTimestamp(it.toEpochMilli(), "Contact updatedAt (${contactModelData.identity})")
                this.lastUpdateAt = it.toEpochMilli()
            }
        }

        val augmentedContactProvider = AugmentedContactProvider(
            augmentedContact = augmentedContact,
            contactDefinedProfilePictureProvider = contactDefinedProfilePictureInfo?.first,
            userDefinedProfilePictureProvider = userDefinedProfilePictureInfo?.first,
        )

        return blobDataProviders to augmentedContactProvider
    }

    private fun collectSyncState(contactModelData: ContactModelData): Contact.SyncState {
        // TODO(ANDR-2327): Consolidate this mechanism
        return if (contactModelData.isLinkedToAndroidContact()) {
            Contact.SyncState.IMPORTED
        } else if (contactModelData.lastName.isBlank() && contactModelData.firstName.isBlank()) {
            Contact.SyncState.INITIAL
        } else {
            Contact.SyncState.CUSTOM
        }
    }

    private fun mapReadReceiptPolicyOverride(contactModelData: ContactModelData): Contact.ReadReceiptPolicyOverride {
        return readReceiptPolicyOverride {
            when (contactModelData.readReceiptPolicy) {
                ch.threema.domain.models.ReadReceiptPolicy.DEFAULT -> default = unit {}
                ch.threema.domain.models.ReadReceiptPolicy.SEND -> policy = ReadReceiptPolicy.SEND_READ_RECEIPT
                ch.threema.domain.models.ReadReceiptPolicy.DONT_SEND -> policy = ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }
        }
    }

    private fun mapTypingIndicatorPolicyOverride(contactModelData: ContactModelData): Contact.TypingIndicatorPolicyOverride {
        return typingIndicatorPolicyOverride {
            when (contactModelData.typingIndicatorPolicy) {
                ch.threema.domain.models.TypingIndicatorPolicy.DEFAULT -> default = unit {}
                ch.threema.domain.models.TypingIndicatorPolicy.SEND -> policy = TypingIndicatorPolicy.SEND_TYPING_INDICATOR
                ch.threema.domain.models.TypingIndicatorPolicy.DONT_SEND -> policy = TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
        }
    }

    private fun mapConversationVisibility(
        conversationVisibility: ConversationVisibility,
    ): ProtocolsConversationVisibility = when (conversationVisibility) {
        ConversationVisibility.NORMAL -> ProtocolsConversationVisibility.NORMAL
        ConversationVisibility.ARCHIVED -> ProtocolsConversationVisibility.ARCHIVED
        ConversationVisibility.PINNED -> ProtocolsConversationVisibility.PINNED
    }

    private fun collectContactDefinedProfilePicture(contactModelData: ContactModelData): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasContactDefinedProfilePicture(contactModelData.identity)) {
            createJpegBlobAssets { fileService.getContactDefinedProfilePicture(contactModelData.identity) }
        } else {
            null
        }
    }

    private fun collectUserDefinedProfilePicture(contactModelData: ContactModelData): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasUserDefinedProfilePicture(contactModelData.identity)) {
            createJpegBlobAssets { fileService.getUserDefinedProfilePicture(contactModelData.identity) }
        } else {
            null
        }
    }

    private fun createJpegBlobAssets(bitmapProvider: () -> Bitmap?): Pair<BlobDataProvider, DeltaImage> {
        val blobId = getNextBlobId()

        val blobDataProvider = BlobDataProvider(blobId) {
            bitmapProvider.invoke()?.let { BitmapUtil.bitmapToJpegByteArray(it) }
        }

        val blobMeta = blob { id = blobId.toByteString() }

        val picture = deltaImage {
            updated = image {
                type = Image.Type.JPEG
                blob = blobMeta
            }
        }
        return blobDataProvider to picture
    }

    private fun mapVerificationLevel(contactModelData: ContactModelData): Contact.VerificationLevel {
        return when (contactModelData.verificationLevel) {
            VerificationLevel.UNVERIFIED -> Contact.VerificationLevel.UNVERIFIED
            VerificationLevel.SERVER_VERIFIED -> Contact.VerificationLevel.SERVER_VERIFIED
            VerificationLevel.FULLY_VERIFIED -> Contact.VerificationLevel.FULLY_VERIFIED
        }
    }

    private fun mapWorkVerificationLevel(contactModelData: ContactModelData): Contact.WorkVerificationLevel {
        return when (contactModelData.workVerificationLevel) {
            WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            WorkVerificationLevel.NONE -> Contact.WorkVerificationLevel.NONE
        }
    }

    private fun mapIdentityState(contactModelData: ContactModelData): Contact.IdentityType {
        return when (contactModelData.identityType) {
            IdentityType.REGULAR -> Contact.IdentityType.REGULAR
            IdentityType.WORK -> Contact.IdentityType.WORK
        }
    }

    private fun mapAcquaintanceLevel(contactModelData: ContactModelData): Contact.AcquaintanceLevel {
        return when (contactModelData.acquaintanceLevel) {
            AcquaintanceLevel.GROUP_OR_DELETED -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
            AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
        }
    }

    private fun mapActivityState(contactModelData: ContactModelData): Contact.ActivityState {
        return when (contactModelData.activityState) {
            IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
            IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
            IdentityState.INVALID -> Contact.ActivityState.INVALID
        }
    }

    private fun getAndValidateData(contactModel: ContactModel): ContactModelData? {
        val contactModelData = contactModel.data ?: return null

        if (contactModelData.publicKey.size != NaCl.PUBLIC_KEY_BYTES) {
            logger.error("Public key of contact {} has an invalid length: {}", contactModelData.identity, contactModelData.publicKey.size)
            throw DeviceLinkingInvalidContactException(contactModel.identity)
        }

        return contactModelData
    }

    private fun collectGroups(): List<Pair<List<BlobDataProvider>, AugmentedGroupProvider>> {
        return groupModelRepository.getAll()
            .map { groupModel ->
                mapToAugmentedGroup(groupModel)
            }
            .also { logger.trace("{} groups", it.size) }
    }

    private fun mapToAugmentedGroup(
        groupModel: GroupModel,
    ): Pair<List<BlobDataProvider>, AugmentedGroupProvider> {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val conversationId = GroupConversationId(groupDatabaseId = groupModel.getDatabaseId())

        val groupModelData = groupModel.data!!

        val groupAvatarInfo = collectGroupAvatar(groupModel)

        val group = group {
            groupIdentity = groupIdentity {
                groupId = groupModel.groupIdentity.groupId
                creatorIdentity = groupModel.groupIdentity.creatorIdentity
            }
            name = groupModelData.name ?: ""
            assertValidTimestamp(groupModelData.createdAt.toEpochMilli(), "Group createdAt (${groupModel.groupIdentity})")
            createdAt = groupModelData.createdAt.toEpochMilli()
            userState = collectUserState(groupModelData)
            notificationTriggerPolicyOverride = groupModelData.notificationTriggerPolicyOverride.toProtobuf()
            deprecatedNotificationSoundPolicyOverride = GroupKt.deprecatedNotificationSoundPolicyOverride {
                default = unit {}
            }

            if (groupAvatarInfo != null) {
                blobDataProviders.add(groupAvatarInfo.first)
                profilePicture = groupAvatarInfo.second
            }
            memberIdentities = collectGroupIdentities(groupModelData)
            conversationCategory =
                if (conversationCategoryService.isMarkedAsPrivate(conversationId)) {
                    ConversationCategory.PROTECTED
                } else {
                    ConversationCategory.DEFAULT
                }
            conversationVisibility = mapConversationVisibility(groupModelData.conversationVisibility)
        }

        val augmentedGroup = augmentedGroup {
            this.group = group
            groupModelData.lastUpdate?.let {
                assertValidTimestamp(it.toEpochMilli(), "Group updatedAt (${groupModelData.groupIdentity})")
                this.lastUpdateAt = it.toEpochMilli()
            }
        }

        val augmentedGroupProvider = AugmentedGroupProvider(
            augmentedGroup = augmentedGroup,
            groupProfilePictureProvider = groupAvatarInfo?.first,
        )

        return blobDataProviders to augmentedGroupProvider
    }

    private fun collectGroupAvatar(groupModel: GroupModel): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasGroupProfilePicture(groupModel.getDatabaseId())) {
            createJpegBlobAssets { fileService.getGroupProfilePictureBitmap(groupModel.getDatabaseId()) }
        } else {
            null
        }
    }

    private fun collectUserState(groupModelData: GroupModelData): Group.UserState {
        return when (groupModelData.userState) {
            UserState.MEMBER -> Group.UserState.MEMBER
            UserState.LEFT -> Group.UserState.LEFT
            UserState.KICKED -> Group.UserState.KICKED
        }
    }

    /**
     * @return The provided group's member identities NOT including the user itself
     */
    private fun collectGroupIdentities(groupModelData: GroupModelData): Identities {
        return identities {
            identities += groupModelData.otherMembers
        }
    }

    /**
     * Collect the distribution lists and ignore lists without members.
     */
    private fun collectDistributionLists(): List<EssentialData.AugmentedDistributionList> {
        return distributionListService
            .all
            .mapNotNull { distributionListModel ->
                mapToAugmentedDistributionList(distributionListModel)
            }
            .also {
                logger.trace("{} distribution lists", it.size)
            }
    }

    /**
     * Returns null if the [distributionListModel] does not have any members
     */
    private fun mapToAugmentedDistributionList(
        distributionListModel: DistributionListModel,
    ): EssentialData.AugmentedDistributionList? {
        val conversationId = DistributionListConversationId(distributionListId = distributionListModel.id)

        return collectDistributionListIdentities(distributionListModel)?.let { identities ->
            distributionList {
                distributionListId = distributionListModel.id
                name = distributionListModel.name ?: ""
                assertValidTimestamp(distributionListModel.createdAt.toEpochMilli(), "DistributionList createdAt (${distributionListModel.id})")
                createdAt = distributionListModel.createdAt.toEpochMilli()
                memberIdentities = identities

                conversationCategory =
                    if (conversationCategoryService.isMarkedAsPrivate(conversationId)) {
                        ConversationCategory.PROTECTED
                    } else {
                        ConversationCategory.DEFAULT
                    }
                conversationVisibility = mapConversationVisibility(distributionListModel.conversationVisibility)
            }
        }?.let {
            augmentedDistributionList {
                this.distributionList = it
                distributionListModel.lastUpdate?.let { lastUpdate ->
                    assertValidTimestamp(lastUpdate.toEpochMilli(), "DistributionList updatedAt (${distributionListModel.id})")
                    this.lastUpdateAt = lastUpdate.toEpochMilli()
                }
            }
        }
    }

    private fun collectDistributionListIdentities(distributionListModel: DistributionListModel): Identities? {
        return distributionListService
            .getDistributionListIdentities(distributionListModel)
            .toList()
            .ifEmpty { null }
            ?.let {
                identities {
                    identities += it
                }
            }
    }

    private fun collectCspNonceHashes(): Set<ByteString> {
        return nonceFactory.getAllHashedNonces(NonceScope.CSP).map { it.bytes.toByteString() }
            .toSet()
            .also { logger.trace("{} csp nonce hashes", it.size) }
    }

    private fun collectD2dNonceHashes(): Set<ByteString> {
        return nonceFactory.getAllHashedNonces(NonceScope.D2D).map { it.bytes.toByteString() }
            .toSet()
            .also { logger.trace("{} d2d nonce hashes", it.size) }
    }

    private fun collectWorkCredentials(): ThreemaWorkCredentials? {
        val credentials = licenseService.let {
            if (it is LicenseServiceUser) {
                it.loadCredentials()
            } else {
                null
            }
        }
        return credentials?.let {
            threemaWorkCredentials {
                username = it.username
                password = it.password
            }
        }
    }

    // TODO(ANDR-2670): Collect all mdm parameters
    private fun collectMdmParameters(): MdmParameters? {
        // Currently we only send the remote secret mdm parameter
        val remoteSecretMdmParamValue = appRestrictions.isRemoteSecretEnabledOrNull()
        if (remoteSecretMdmParamValue != null) {
            logger.info("Including remote secret mdm parameter")
            return mdmParameters {
                // Note that we currently set it as a threema parameter as we can't distinguish it easily here.
                threemaParameters.put(
                    context.getString(R.string.restriction__enable_remote_secret),
                    parameter {
                        booleanValue = remoteSecretMdmParamValue
                    },
                )
            }
        }

        // In case the remote secret mdm parameter is not set, we don't include any mdm parameters
        return null
    }

    /**
     * Assert that the provided [timestamp] is withing the range supported by threema desktop.
     * The timestamp range that is supported by threema desktop is 0L..8_640_000_000_000_000L.
     * See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date
     *
     * To support only unsigned data types for timestamps is defined on libthreema protocol level.
     *
     * If the [timestamp] is invalid a [DeviceLinkingInvalidTimestampException] with the provided [timestampDescription] will be thrown.
     * Note that the [timestampDescription] might be displayed to the user.
     */
    private fun assertValidTimestamp(timestamp: Long, timestampDescription: String) {
        if (timestamp !in 0L..8_640_000_000_000_000L) {
            throw DeviceLinkingInvalidTimestampException(
                timestamp = timestamp,
                timestampDescription = timestampDescription,
            )
        }
    }

    private companion object {
        private var nextBlobId = 1L
        fun getNextBlobId(): ByteArray {
            return ByteBuffer.wrap(ByteArray(ProtocolDefines.BLOB_ID_LEN))
                .putLong(nextBlobId++)
                .array()
                .also {
                    check(it.size == ProtocolDefines.BLOB_ID_LEN) { "Invalid blob id generated" }
                }
        }
    }
}
