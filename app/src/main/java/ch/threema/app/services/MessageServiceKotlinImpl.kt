package ch.threema.app.services

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import ch.threema.app.preference.service.PreferenceService
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType

abstract class MessageServiceKotlinImpl(
    private val context: Context,
    private val preferenceService: PreferenceService,
) : MessageService {

    /**
     * Check whether the file in question should be automatically downloaded.
     * This depends on rendering type, message content type, file size and user preference (settings).
     *
     * @param messageModel MessageModel to check
     * @return true if file should be downloaded immediately, false otherwise
     */
    override fun shouldAutoDownload(messageModel: MessageModel): Boolean {
        val autoDownloadType = messageModel.getAutoDownloadType()
            ?: return false

        val connectivityManager = context.getSystemService<ConnectivityManager>()
            ?: return false
        val autoDownloadTypes = if (connectivityManager.isActiveNetworkMetered) {
            preferenceService.getMobileAutoDownload()
        } else {
            preferenceService.getWifiAutoDownload()
        }

        // TODO(ANDR-5174): The behavior here differs from the description in the settings
        return autoDownloadType in autoDownloadTypes &&
            messageModel.fileData.fileSize <= MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO
    }

    private fun MessageModel.getAutoDownloadType(): PreferenceService.AutoDownloadType? {
        if (type != MessageType.FILE) {
            return null
        }
        val fileData = fileData
        if (fileData.renderingType == FileData.RENDERING_DEFAULT) {
            return PreferenceService.AutoDownloadType.FILE
        }
        return when (messageContentsType) {
            MessageContentsType.IMAGE,
            MessageContentsType.GIF,
            -> PreferenceService.AutoDownloadType.IMAGE
            MessageContentsType.VIDEO -> PreferenceService.AutoDownloadType.VIDEO
            MessageContentsType.VOICE_MESSAGE -> PreferenceService.AutoDownloadType.VOICE_MESSAGE
            else -> PreferenceService.AutoDownloadType.FILE
        }
    }
}
