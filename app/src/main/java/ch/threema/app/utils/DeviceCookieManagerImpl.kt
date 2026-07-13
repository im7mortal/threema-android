package ch.threema.app.utils

import android.content.Context
import ch.threema.app.R
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.generateRandomBytes
import ch.threema.common.toHexString
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.storage.models.ServerMessageModel
import java.security.SecureRandom

private val logger = getThreemaLogger("DeviceCookieManagerImpl")

class DeviceCookieManagerImpl(
    private val appContext: Context,
    private val encryptedPreferenceStore: EncryptedPreferenceStore,
    private val serverMessageModelRepository: ServerMessageModelRepository,
    private val secureRandom: SecureRandom,
) : DeviceCookieManager {
    private var skipNextIndication = false

    override fun getOrCreateDeviceCookie(): ByteArray {
        var deviceCookie = encryptedPreferenceStore.getBytes(appContext.getString(R.string.preferences__device_cookie))
        if (deviceCookie?.size == DEVICE_COOKIE_SIZE) {
            logger.debug("Got existing device cookie {}...", deviceCookie.toHexString(maxBytes = 2))
            return deviceCookie
        }

        deviceCookie = secureRandom.generateRandomBytes(DEVICE_COOKIE_SIZE)
        encryptedPreferenceStore.save(appContext.getString(R.string.preferences__device_cookie), deviceCookie)

        logger.info("Generated new device cookie {}", deviceCookie.toHexString(maxBytes = 2))

        // Skip the next indication, as we have just generated a new cookie and
        // will get an indication for sure if this is a restored ID (where the
        // server has already stored a device cookie).
        skipNextIndication = true

        return deviceCookie
    }

    override fun onChangeIndicationReceived() {
        if (skipNextIndication) {
            logger.info("Skipping change indication because new cookie has been generated")
            skipNextIndication = false
            return
        }

        logger.info("Device cookie change indication received, creating server warning")
        serverMessageModelRepository.saveServerMessage(
            ServerMessageModel(appContext.getString(R.string.rogue_device_warning), ServerMessageModel.TYPE_ALERT),
        )
    }

    companion object {
        private const val DEVICE_COOKIE_SIZE = 16
    }
}
