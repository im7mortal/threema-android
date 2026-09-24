package ch.threema.storage.models.data.media

import ch.threema.storage.models.data.MessageDataInterface

interface MediaMessageDataInterface : MessageDataInterface {
    val encryptionKey: ByteArray?

    val blobId: ByteArray?

    var isDownloaded: Boolean
}
