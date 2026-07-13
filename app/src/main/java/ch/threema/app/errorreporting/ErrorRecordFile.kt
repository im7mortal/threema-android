package ch.threema.app.errorreporting

import ch.threema.common.renameOrThrow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

sealed class ErrorRecordFile {

    abstract val recordsDirectory: File
    abstract val id: UUID
    abstract val errorTypeId: ErrorTypeId?
    abstract val prefix: String

    fun read(): InputStream =
        file.inputStream()

    fun delete() {
        file.delete()
    }

    protected val file: File by lazy {
        val infix = errorTypeId?.let {
            SEPARATOR + it
        }
            ?: ""
        File(recordsDirectory, prefix + id + infix + FILE_SUFFIX)
    }

    data class Pending(
        override val recordsDirectory: File,
        override val id: UUID,
        override val errorTypeId: ErrorTypeId?,
    ) : ErrorRecordFile() {
        override val prefix: String
            get() = PENDING_PREFIX

        fun write(): OutputStream =
            file.outputStream()

        fun confirm(): Confirmed {
            val confirmed = Confirmed(
                recordsDirectory = recordsDirectory,
                id = id,
                errorTypeId = errorTypeId,
            )
            file.renameOrThrow(confirmed.file)
            return confirmed
        }

        companion object {
            fun create(recordsDirectory: File, id: UUID, message: String?): Pending =
                Pending(recordsDirectory, id, message?.let(ErrorTypeId::fromMessageFormat))
        }
    }

    data class Confirmed(
        override val recordsDirectory: File,
        override val id: UUID,
        override val errorTypeId: ErrorTypeId?,
    ) : ErrorRecordFile() {
        override val prefix: String
            get() = CONFIRMED_PREFIX
    }

    companion object {
        private const val PENDING_PREFIX = "p_"
        private const val CONFIRMED_PREFIX = "c_"
        private const val SEPARATOR = "__"
        private const val FILE_SUFFIX = "_v2.json"

        fun getPendingRecordFiles(recordsDirectory: File): List<Pending> =
            getRecordFiles(recordsDirectory, prefix = PENDING_PREFIX, ::Pending)

        fun getConfirmedRecordFiles(recordsDirectory: File): List<Confirmed> =
            getRecordFiles(recordsDirectory, prefix = CONFIRMED_PREFIX, ::Confirmed)

        private fun <T : ErrorRecordFile> getRecordFiles(
            recordsDirectory: File,
            prefix: String,
            map: (recordsDirectory: File, id: UUID, ErrorTypeId?) -> T,
        ): List<T> =
            recordsDirectory.listFiles { file ->
                file.name.startsWith(prefix) && file.name.endsWith(FILE_SUFFIX)
            }
                ?.mapNotNull { file ->
                    val rawName = file.name.removePrefix(prefix).removeSuffix(FILE_SUFFIX)
                    val parts = rawName.split(SEPARATOR)
                    val id = try {
                        UUID.fromString(parts[0])
                    } catch (_: IllegalArgumentException) {
                        file.delete()
                        return@mapNotNull null
                    }
                    val errorTypeId = parts.getOrNull(1)?.let(::ErrorTypeId)
                    map(recordsDirectory, id, errorTypeId)
                }
                ?.toList()
                ?: emptyList()
    }
}
