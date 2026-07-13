package ch.threema.storage.databaseupdate

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.JsonArrayIterator
import ch.threema.storage.buildContentValues
import ch.threema.storage.runQuery
import ch.threema.storage.runUpdate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("DatabaseUpdateToVersion120")

@OptIn(ExperimentalSerializationApi::class)
class DatabaseUpdateToVersion120(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrateLegacyMediaMessages(table = "message")
        migrateLegacyMediaMessages(table = "m_group_message")
        migrateLegacyMediaMessages(table = "distribution_list_message")
    }

    private fun migrateLegacyMediaMessages(table: String) {
        queryLegacyMediaMessages(table).use { cursor ->
            while (cursor.moveToNext()) {
                val messageId = cursor.getLong(0)
                val type = cursor.getInt(1)
                val body: String? = cursor.getString(2)

                val transformedBody: String?
                val messageContentsType: Int

                when (type) {
                    MESSAGE_TYPE_IMAGE -> {
                        transformedBody = withNonNullBody(
                            typeName = "image",
                            mimeType = "image/jpeg",
                            body = body,
                            transformation = ::transformImageBodyToFileBody,
                        )
                        messageContentsType = MESSAGE_CONTENT_TYPE_IMAGE
                    }
                    MESSAGE_TYPE_AUDIO -> {
                        transformedBody = withNonNullBody(
                            typeName = "audio",
                            mimeType = "audio/aac",
                            body = body,
                            transformation = ::transformAudioBodyToFileBody,
                        )
                        messageContentsType = MESSAGE_CONTENT_TYPE_AUDIO
                    }
                    MESSAGE_TYPE_VIDEO -> {
                        transformedBody = withNonNullBody(
                            typeName = "video",
                            mimeType = "video/mpeg",
                            body = body,
                            transformation = ::transformVideoBodyToFileBody,
                        )
                        messageContentsType = MESSAGE_CONTENT_TYPE_VIDEO
                    }
                    else -> error("Unexpected message type $type")
                }

                sqLiteDatabase.runUpdate(
                    table = table,
                    values = buildContentValues {
                        put("type", MESSAGE_TYPE_FILE)
                        put("messageContentsType", messageContentsType)
                        put("body", transformedBody)
                    },
                    whereClause = "id = ?",
                    whereArgs = arrayOf(messageId.toString()),
                )
            }
        }
    }

    private fun queryLegacyMediaMessages(table: String) =
        sqLiteDatabase.runQuery(
            table = table,
            columns = arrayOf("id", "type", "body"),
            selection = "type = $MESSAGE_TYPE_IMAGE OR type = $MESSAGE_TYPE_VIDEO OR type = $MESSAGE_TYPE_AUDIO",
        )

    private fun withNonNullBody(typeName: String, mimeType: String, body: String?, transformation: (String) -> String): String =
        when {
            body == null -> {
                logger.warn("Body of {} message is null", typeName)
                null
            }
            body.isEmpty() -> {
                logger.warn("Body of {} message is empty", typeName)
                null
            }
            else -> try {
                transformation.invoke(body)
            } catch (e: Exception) {
                logger.error("Failed to transform {} body", typeName, e)
                null
            }
        }
            ?: createFallbackBody(mimeType)

    /**
     * If a legacy message body can not be parsed, we construct a basic one. If the file was actually already downloaded,
     * this will result in a message that can still be viewed normally. If the file was not downloaded, it will just not be openable/displayable.
     */
    private fun createFallbackBody(mimeType: String): String =
        buildJsonArray {
            add(null) // blobId
            add(null) // encryption key
            add(mimeType)
            add(0) // file size
            add(null) // file name
            add(1) // rendering type "media"
            add(true) // is downloaded
            add(null) // caption
            add(null) // thumbnail mime type
            addJsonObject {}
        }
            .toString()

    private fun transformImageBodyToFileBody(imageBody: String): String {
        val iterator = JsonArrayIterator(imageBody)
        val isDownloaded = iterator.nextBoolean()
        val encryptionKey = iterator.nextString()
        val blobId = iterator.nextString()
        val nonce = iterator.nextString()

        return buildJsonArray {
            add(blobId)
            add(encryptionKey)
            add("image/jpeg")
            add(0) // file size
            add(null) // file name
            add(1) // rendering type "media"
            add(isDownloaded)
            add(null) // caption
            add(null) // thumbnail mime type
            addJsonObject {
                if (!nonce.isNullOrEmpty() && !isDownloaded) {
                    put("_legacy_nonce", nonce)
                }
            }
        }
            .toString()
    }

    private fun transformAudioBodyToFileBody(audioBody: String): String {
        val iterator = JsonArrayIterator(audioBody)
        val duration = iterator.nextInt()
        val isDownloaded = iterator.nextBoolean()
        val encryptionKey = iterator.nextString()
        val blobId = iterator.nextString()

        return buildJsonArray {
            add(blobId)
            add(encryptionKey)
            add("audio/aac")
            add(0) // file size
            add(null) // file name
            add(1) // rendering type "media"
            add(isDownloaded)
            add(null) // caption
            add(null) // thumbnail mime type
            addJsonObject {
                put("d", duration)
            }
        }
            .toString()
    }

    private fun transformVideoBodyToFileBody(videoBody: String): String {
        val iterator = JsonArrayIterator(videoBody)
        val duration = iterator.nextInt()
        val isDownloaded = iterator.nextBoolean()
        val encryptionKey = iterator.nextString()
        val blobId = iterator.nextString()
        val fileSize = if (iterator.hasNext()) {
            iterator.nextInt().toLong()
        } else {
            0L
        }

        return buildJsonArray {
            add(blobId)
            add(encryptionKey)
            add("video/mpeg")
            add(fileSize)
            add(null) // file name
            add(1) // rendering type "media"
            add(isDownloaded)
            add(null) // caption
            add(null) // thumbnail mime type
            addJsonObject {
                put("d", duration)
            }
        }
            .toString()
    }

    override val version = 120

    override fun getDescription() = "Convert legacy image, audio and video messages to file messages"

    companion object {
        private const val MESSAGE_TYPE_IMAGE = 1
        private const val MESSAGE_TYPE_AUDIO = 3
        private const val MESSAGE_TYPE_VIDEO = 2
        private const val MESSAGE_TYPE_FILE = 8
        private const val MESSAGE_CONTENT_TYPE_IMAGE = 2
        private const val MESSAGE_CONTENT_TYPE_AUDIO = 4
        private const val MESSAGE_CONTENT_TYPE_VIDEO = 3
    }
}
