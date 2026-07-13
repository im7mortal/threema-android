package ch.threema.app.usecases.avatar

import android.graphics.Bitmap
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.glide.AvatarOptions
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.GroupModelRepository
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("GetAndPrepareAvatarUseCase")

class GetAndPrepareAvatarUseCase(
    private val avatarCacheService: AvatarCacheService,
    private val dispatcherProvider: DispatcherProvider,
    private val groupModelRepository: GroupModelRepository,
) {

    /**
     *  Load the avatar bitmap data for the given [conversationId] and prepare it **for display** by uploading data into the GPUs vRAM.
     *
     *  Devices with hardware acceleration benefit from this bitmap preparation. On devices without hardware acceleration, this preparation is
     *  effectively no-op.
     *
     *  @see [Bitmap.prepareToDraw]
     */
    suspend fun call(conversationId: ConversationId): ImmutableBitmap? = withContext(dispatcherProvider.io) {
        val bitmap: Bitmap? = when (conversationId) {
            is ContactConversationId -> getContactAvatar(conversationId)
            is GroupConversationId -> getGroupAvatar(conversationId)
            is DistributionListConversationId -> getDistributionListAvatar(conversationId)
        }
        try {
            bitmap?.prepareToDraw()
        } catch (exception: Exception) {
            logger.warn("Could not prepare bitmap for draw", exception)
        }
        bitmap?.toImmutableBitmap()
    }

    private fun getContactAvatar(contactConversationId: ContactConversationId): Bitmap? =
        avatarCacheService.getIdentityAvatar(
            /* identity = */
            contactConversationId.identity,
            /* options = */
            AvatarOptions.PRESET_DEFAULT_FALLBACK,
        )

    private fun getGroupAvatar(groupConversationId: GroupConversationId): Bitmap? {
        val groupIdentity = groupModelRepository
            .getByGroupDatabaseId(groupDatabaseId = groupConversationId.groupDatabaseId)
            ?.groupIdentity
            ?: return null
        return avatarCacheService.getGroupAvatar(
            /* groupIdentity = */
            groupIdentity,
            /* options = */
            AvatarOptions.PRESET_DEFAULT_FALLBACK,
        )
    }

    private fun getDistributionListAvatar(distributionListConversationId: DistributionListConversationId): Bitmap? =
        avatarCacheService.getDistributionListAvatarLow(
            /* distributionListId = */
            distributionListConversationId.distributionListId,
        )
}
