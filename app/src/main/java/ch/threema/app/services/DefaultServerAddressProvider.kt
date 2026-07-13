package ch.threema.app.services

import ch.threema.app.BuildConfig
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.domain.protocol.urls.BlobMirrorUrl
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl
import ch.threema.domain.protocol.urls.MediatorUrl

class DefaultServerAddressProvider(
    preferenceService: PreferenceService,
) : ServerAddressProvider {
    private val ipv6 by lazy { preferenceService.isIpv6Preferred() }

    override fun getChatServerNamePrefix(): String =
        if (ipv6) {
            BuildConfig.CHAT_SERVER_IPV6_PREFIX
        } else {
            BuildConfig.CHAT_SERVER_PREFIX
        }

    override fun getChatServerNameSuffix(): String = BuildConfig.CHAT_SERVER_SUFFIX

    override fun getChatServerPorts(): IntArray = BuildConfig.CHAT_SERVER_PORTS

    override fun getChatServerUseServerGroups() = BuildConfig.CHAT_SERVER_GROUPS

    override fun getChatServerPublicKey(): ByteArray =
        BuildConfig.SERVER_PUBKEY ?: error("Chat server public key was unexpectedly missing from BuildConfig")

    override fun getChatServerPublicKeyAlt(): ByteArray =
        BuildConfig.SERVER_PUBKEY_ALT ?: error("Chat server alt public key was unexpectedly missing from BuildConfig")

    override fun getDirectoryServerUrl(): String =
        if (ipv6) {
            BuildConfig.DIRECTORY_SERVER_IPV6_URL
        } else {
            BuildConfig.DIRECTORY_SERVER_URL
        }

    override fun getWorkServerUrlLegacy(): String? =
        if (ipv6) {
            BuildConfig.WORK_SERVER_IPV6_URL_LEGACY
        } else {
            BuildConfig.WORK_SERVER_URL_LEGACY
        }

    override fun getWorkServerUrl(): String? = BuildConfig.WORK_SERVER_URL

    /**
     * @throws ThreemaException if the build config field for the correct base url is missing in current build flavor. This
     * is the case if this implementation is incorrectly used in an on-prem build.
     */
    @Throws(ThreemaException::class)
    private fun getBlobBaseUrlDefaultServer(): String {
        // TODO(ANDR-3753): Remove suppression and comment
        @Suppress("RedundantNullableReturnType")
        val baseUrlRawValue: String? = if (ipv6) BuildConfig.BLOB_SERVER_IPV6_URL else BuildConfig.BLOB_SERVER_URL
        if (baseUrlRawValue == null) {
            // Could actually be null, if the build-config field was explicitly set to value "null" in build.gradle.kts
            throw ThreemaException("Missing value for blob server url in current build flavor")
        }
        return baseUrlRawValue
    }

    /**
     * @throws ThreemaException if the build config field for the correct base url is missing in current build flavor. This
     * is the case if this implementation is incorrectly used in an on-prem build.
     */
    @Throws(ThreemaException::class)
    private fun getBlobBaseUrlMirrorServer(multiDevicePropertyProvider: MultiDevicePropertyProvider): String {
        // TODO(ANDR-3753): Remove suppression and comment
        // Could actually be null, if the build-config field was explicitly set to value "null" in build.gradle.kts
        @Suppress("SENSELESS_COMPARISON")
        if (BuildConfig.BLOB_MIRROR_SERVER_URL == null) {
            throw ThreemaException("Missing value for blob server url in current build flavor")
        }
        try {
            return BlobMirrorUrl(BuildConfig.BLOB_MIRROR_SERVER_URL)
                .get(deviceGroupId = multiDevicePropertyProvider.get().keys.dgid)
        } catch (e: IllegalArgumentException) {
            throw ThreemaException(e.message, e)
        }
    }

    @Throws(ThreemaException::class)
    override fun getBlobServerDownloadUrl(): BlobUrl {
        val blobBaseUrlDefaultServer = getBlobBaseUrlDefaultServer()
        return BlobUrl("$blobBaseUrlDefaultServer/{blobId}")
    }

    override fun getBlobServerUploadUrl(): String =
        if (ipv6) {
            BuildConfig.BLOB_SERVER_IPV6_URL_UPLOAD
        } else {
            BuildConfig.BLOB_SERVER_URL_UPLOAD
        }

    @Throws(ThreemaException::class)
    override fun getBlobServerDoneUrl(): BlobUrl {
        val blobBaseUrlDefaultServer = getBlobBaseUrlDefaultServer()
        return BlobUrl("$blobBaseUrlDefaultServer/{blobId}/done")
    }

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl {
        val blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider)
        return BlobUrl("$blobBaseUrlMirrorServer/{blobId}")
    }

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerUploadUrl(
        multiDevicePropertyProvider: MultiDevicePropertyProvider,
    ): String {
        val blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider)
        return "$blobBaseUrlMirrorServer/upload"
    }

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerDoneUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl {
        val blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider)
        return BlobUrl("$blobBaseUrlMirrorServer/{blobId}/done")
    }

    override fun getAvatarServerUrl(): String = BuildConfig.AVATAR_FETCH_URL

    override fun getSafeServerUrl(): String = BuildConfig.SAFE_SERVER_URL

    override fun getWebServerUrl(): String = BuildConfig.WEB_SERVER_URL

    override fun getWebOverrideSaltyRtcHost(): String? = null

    override fun getWebOverrideSaltyRtcPort(): Int = 0

    override fun getThreemaPushPublicKey(): ByteArray? = BuildConfig.THREEMA_PUSH_PUBLIC_KEY

    override fun getMediatorUrl() = MediatorUrl(BuildConfig.MEDIATOR_SERVER_URL)

    override fun getAppRatingUrl(): AppRatingUrl =
        AppRatingUrl(BuildConfig.APP_RATING_URL)

    override fun getMapStyleUrl(): String = BuildConfig.MAP_STYLES_URL

    override fun getMapPoiAroundUrl() = MapPoiAroundUrl(BuildConfig.MAP_POI_AROUND_URL)

    override fun getMapPoiNamesUrl() = MapPoiNamesUrl(BuildConfig.MAP_POI_NAMES_URL)
}
