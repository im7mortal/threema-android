package ch.threema.app.backupcenter.models

import ch.threema.common.ByteSize
import ch.threema.common.serializers.ByteSizeSerializer
import ch.threema.common.serializers.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LastLocalDataBackupInfo(
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = ByteSizeSerializer::class)
    val size: ByteSize,
    val mediaIncluded: Boolean,
    val location: String,
)
