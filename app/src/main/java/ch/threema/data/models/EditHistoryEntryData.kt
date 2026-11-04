package ch.threema.data.models

import androidx.compose.runtime.Immutable
import ch.threema.data.storage.DbEditHistoryEntry
import java.time.Instant

@Immutable
data class EditHistoryEntryData(
    /** unique id. */
    @JvmField val uid: Int,
    /** The id of the edited message referencing the db row. */
    @JvmField val messageUid: String,
    /** The former text of the edited message. */
    @JvmField val text: String?,
    /** Timestamp when the former text of the message was created. */
    @JvmField val createdAt: Instant,
)

fun DbEditHistoryEntry.toDataType() = EditHistoryEntryData(
    uid = this.uid,
    messageUid = this.messageUid,
    text = this.text,
    createdAt = this.createdAt,
)
