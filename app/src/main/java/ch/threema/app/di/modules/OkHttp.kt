package ch.threema.app.di.modules

import ch.threema.app.di.Qualifiers
import ch.threema.app.services.ApiService
import ch.threema.app.utils.AppVersionProvider
import ch.threema.base.HAS_DEV_FEATURES
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.buildNew
import ch.threema.common.withUserAgent
import ch.threema.domain.protocol.ChallengeResponseHelper
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.blob.BlobUploadRequestInterceptor
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.getUserAgent
import ch.threema.domain.stores.IdentityStore
import kotlin.time.TimeSource
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module

private val logger = getThreemaLogger("OkHttp")

val okHttpClientsModule = module {
    single<OkHttpClient>(qualifier = Qualifiers.okHttpBase) {
        buildBaseOkHttpClient()
    }
    single<OkHttpClient>(qualifier = Qualifiers.okHttpBlobDownload) {
        buildBlobDownloadOkHttpClient(
            baseOkHttpClient = get(qualifier = Qualifiers.okHttpBase),
        )
    }
    single<OkHttpClient>(qualifier = Qualifiers.okHttpBlobUpload) {
        buildBlobUploadOkHttpClient(
            baseOkHttpClient = get(qualifier = Qualifiers.okHttpBase),
            identityStore = get(),
            challengeResponseHelper = get(),
            timeSource = get(),
            serverAddressProvider = get(),
            getLegacyAuthToken = {
                get<ApiService>().getAuthToken()
            },
        )
    }
}

private fun buildBaseOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .apply {
            connectTimeout(ProtocolDefines.CONNECT_TIMEOUT)
            writeTimeout(ProtocolDefines.WRITE_TIMEOUT)
            readTimeout(ProtocolDefines.READ_TIMEOUT)
            withUserAgent(getUserAgent(AppVersionProvider.appVersion))
            if (HAS_DEV_FEATURES) {
                val interceptor = HttpLoggingInterceptor(logger::debug)
                interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
                addNetworkInterceptor(interceptor)
            }
        }
        .build()

private fun buildBlobDownloadOkHttpClient(
    baseOkHttpClient: OkHttpClient,
): OkHttpClient =
    baseOkHttpClient
        .buildNew {
            connectTimeout(ProtocolDefines.BLOB_CONNECT_TIMEOUT)
            readTimeout(ProtocolDefines.BLOB_LOAD_TIMEOUT)
        }

private fun buildBlobUploadOkHttpClient(
    baseOkHttpClient: OkHttpClient,
    identityStore: IdentityStore,
    challengeResponseHelper: ChallengeResponseHelper,
    timeSource: TimeSource.WithComparableMarks,
    serverAddressProvider: ServerAddressProvider,
    getLegacyAuthToken: () -> String?,
): OkHttpClient =
    baseOkHttpClient
        .buildNew {
            connectTimeout(ProtocolDefines.BLOB_CONNECT_TIMEOUT)
            readTimeout(ProtocolDefines.BLOB_LOAD_TIMEOUT)
            addInterceptor(
                BlobUploadRequestInterceptor(
                    serverAddressProvider,
                    identityStore,
                    challengeResponseHelper,
                    timeSource,
                    getLegacyAuthToken,
                ),
            )
        }
