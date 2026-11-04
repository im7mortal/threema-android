package ch.threema.app.errorreporting

import ch.threema.common.serializers.FileSerializer
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class ErrorRecordAttachment(
    @Serializable(with = FileSerializer::class)
    val file: File,
    val mimeType: String?,
)
