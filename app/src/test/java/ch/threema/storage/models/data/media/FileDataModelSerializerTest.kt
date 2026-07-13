package ch.threema.storage.models.data.media

import ch.threema.domain.protocol.csp.messages.file.FileData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileDataModelSerializerTest {
    @Test
    fun `serialize file model data body`() {
        val body = FileDataModelSerializer.serializeFileDataBody(
            blobId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            encryptionKey = ByteArray(32) { 0xff.toByte() },
            mimeType = "image/jpeg",
            fileSize = 500 * 1024L,
            fileName = "my-file.jpg",
            renderingType = FileData.RENDERING_MEDIA,
            isDownloaded = true,
            caption = "Hello",
            thumbnailMimeType = "image/jpeg",
            metaData = mapOf(
                FileDataModel.METADATA_KEY_WIDTH to 100,
                FileDataModel.METADATA_KEY_HEIGHT to 200,
            ),
        )

        assertEquals(
            """["0102030405060708090a0b0c0d0e0f10","ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",""" +
                """"image/jpeg",512000,"my-file.jpg",1,true,"Hello","image/jpeg",{"w":100,"h":200}]""",
            body,
        )
    }

    @Test
    fun `deserialize file model data body`() {
        val body = """["0102030405060708090a0b0c0d0e0f10","ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",""" +
            """"image/jpeg",512000,"my-file.jpg",1,true,"Hello","image/jpeg",{"w":100,"h":200}]"""

        val fileDataModel = FileDataModelSerializer.deserializeFileDataBody(body)

        assertNotNull(fileDataModel)
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            fileDataModel.blobId,
        )
        assertContentEquals(
            ByteArray(32) { 0xff.toByte() },
            fileDataModel.encryptionKey,
        )
        assertEquals(
            "image/jpeg",
            fileDataModel.mimeType,
        )
        assertEquals(
            500 * 1024L,
            fileDataModel.fileSize,
        )
        assertEquals(
            FileData.RENDERING_MEDIA,
            fileDataModel.renderingType,
        )
        assertEquals(
            true,
            fileDataModel.isDownloaded,
        )
        assertEquals(
            "Hello",
            fileDataModel.caption,
        )
        assertEquals(
            "image/jpeg",
            fileDataModel.thumbnailMimeType,
        )
        assertEquals(
            mapOf(
                FileDataModel.METADATA_KEY_WIDTH to 100.0,
                FileDataModel.METADATA_KEY_HEIGHT to 200.0,
            ),
            fileDataModel.metaData,
        )
    }

    @Test
    fun `deserialize file model data body with invalid rendering type`() {
        val body = """["0102030405060708090a0b0c0d0e0f10","ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",""" +
            """"image/jpeg",512000,"my-file.jpg","invalid",true,"Hello","image/jpeg",{"w":100,"h":200}]"""

        val fileDataModel = FileDataModelSerializer.deserializeFileDataBody(body)

        assertNotNull(fileDataModel)
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            fileDataModel.blobId,
        )
        assertEquals(
            FileData.RENDERING_DEFAULT,
            fileDataModel.renderingType,
        )
        assertEquals(
            "image/jpeg",
            fileDataModel.thumbnailMimeType,
        )
    }

    @Test
    fun `deserialize file model data body without thumbnail-mime-type and meta-data fields`() {
        val body = """["0102030405060708090a0b0c0d0e0f10","ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",""" +
            """"image/jpeg",512000,"my-file.jpg",1,true,"Hello"]"""

        val fileDataModel = FileDataModelSerializer.deserializeFileDataBody(body)

        assertNotNull(fileDataModel)
        assertNull(fileDataModel.thumbnailMimeType)
        assertNull(fileDataModel.metaData)
    }

    @Test
    fun `deserialize file model data body without meta-data`() {
        val body = """["0102030405060708090a0b0c0d0e0f10","ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","image/jpeg",512000,""" +
            """"my-file.jpg",1,true,"Hello","image/jpeg"]"""

        val fileDataModel = FileDataModelSerializer.deserializeFileDataBody(body)

        assertNotNull(fileDataModel)
        assertEquals(
            "image/jpeg",
            fileDataModel.thumbnailMimeType,
        )
        assertNull(fileDataModel.metaData)
    }

    @Test
    fun `deserializing invalid file model data body returns null`() {
        assertNull(FileDataModelSerializer.deserializeFileDataBody(""))
        assertNull(FileDataModelSerializer.deserializeFileDataBody("[]"))
        assertNull(FileDataModelSerializer.deserializeFileDataBody("{}"))
    }
}
