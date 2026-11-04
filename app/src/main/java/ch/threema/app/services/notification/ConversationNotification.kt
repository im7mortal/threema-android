package ch.threema.app.services.notification

import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.app.Person
import ch.threema.app.services.notification.NotificationService.FetchCacheUri
import ch.threema.domain.types.MessageUid
import java.time.Instant

sealed class ConversationNotification(
    val messageId: Int,
    val messageUid: MessageUid,
    val conversationHandle: NotificationConversationHandle,
    val createdAt: Instant,
    val thumbnailMimeType: String?,
    val fetchThumbnailUri: FetchCacheUri?,
    val senderPerson: Person?,
) {

    abstract val shouldApplySenderPerson: Boolean

    private var thumbnailUri: Uri? = null

    @WorkerThread
    fun getOrCreateThumbnail(): Uri? {
        if (thumbnailUri == null) {
            thumbnailUri = fetchThumbnailUri?.fetch()
        }
        return thumbnailUri
    }
}
