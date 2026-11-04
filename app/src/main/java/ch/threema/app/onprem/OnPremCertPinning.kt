package ch.threema.app.onprem

import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.buildNew
import ch.threema.domain.onprem.OnPremConfig
import ch.threema.domain.onprem.OnPremConfigDomains
import ch.threema.domain.onprem.OnPremConfigFetcher
import ch.threema.domain.onprem.OnPremConfigStore
import java.io.IOException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("OnPremCertPinning")

class OnPremCertPinning(
    getOnPremConfigFetcherProvider: () -> OnPremConfigFetcherProvider,
    getOnPremConfigStore: () -> OnPremConfigStore,
) : KoinComponent {

    // The OnPremConfigFetcherProvider depends on OkHttpClient, so to avoid cyclic dependencies in Koin,
    // it needs to be injected lazily here.
    private val onPremConfigFetcherProvider: OnPremConfigFetcherProvider by lazy(getOnPremConfigFetcherProvider)
    private val onPremConfigStore: OnPremConfigStore by lazy(getOnPremConfigStore)

    /**
     * Creates an OkHttpClient which enforces certificate pinning based on the config in the OPPF.
     * When a request is made with this client and the locally cached OPPF is not available or has expired, the OPPF will first be
     * fetched. If this fetching fails, the original request will fail as well.
     * If a certificate pinning error is encountered, this client will try to fetch the OPPF from the fallback URL and then retry the
     * original request.
     */
    fun createDefaultOkHttpClient(
        baseClient: OkHttpClient,
    ): OkHttpClient =
        createOkHttpClient(
            baseClient = baseClient,
            getOnPremConfig = { onPremConfigFetcher ->
                onPremConfigFetcher.getOrFetch()
            },
        )

    /**
     * Creates an OkHttpClient which enforces certificate pinning based on the config in the OPPF, if possible.
     * If no OPPF is cached in memory or if it is expired, the stored on-prem config will be used, even if it is expired.
     * As opposed to the client created by [createDefaultOkHttpClient], this client will never fetch the OPPF from the regular endpoint.
     * Instead, if it encounters a certificate pinning error, it will try to fetch the OPPF from the fallback URL.
     *
     * This client can be used in special cases where fetching the OPPF is not possible, e.g. because the credentials aren't available,
     * as is the case when the master key is locked with a remote secret.
     *
     * @param requireCertificatePinning Whether certificate pinning must be enforced.
     * If this is set to true and both the in-memory cache and the on-prem config store are empty, requests will fail.
     * If this is set to false and both the in-memory cache and the on-prem config store are empty, requests will be made
     * WITHOUT on-prem certificate pinning.
     * Note: the stored on-prem config store is expected to always be available, except for the very first request
     * made by the app to obtain the OPPF. This request is allowed to happen without certificate pinning, as the OPPF itself
     * is not considered secret and is signed, meaning a potential man-in-the-middle could not do much with it.
     */
    fun createOkHttpClientWithoutRegularOppfFetching(
        baseClient: OkHttpClient,
        requireCertificatePinning: Boolean = true,
    ): OkHttpClient =
        createOkHttpClient(
            baseClient = baseClient,
            getOnPremConfig = { onPremConfigFetcher ->
                onPremConfigFetcher.getCached()
                    ?: onPremConfigStore.get()
                    ?: if (requireCertificatePinning) {
                        throw IOException("Cannot enforce certificate pinning, no store OnPrem config found")
                    } else {
                        null
                    }
            },
        )

    private fun createOkHttpClient(
        baseClient: OkHttpClient,
        getOnPremConfig: (OnPremConfigFetcher) -> OnPremConfig?,
    ): OkHttpClient {
        val hostnameProvider = ThreadLocalHostnameProvider()
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = {
                getOnPremConfigDomains(
                    getOnPremConfig = getOnPremConfig,
                )
            },
            hostnameProvider = hostnameProvider,
        )
        return baseClient.buildNew {
            sslSocketFactory(createSocketFactory(trustManager), trustManager)
            addInterceptor(OnPremCertPinningInterceptor(hostnameProvider))
            addInterceptor(
                OnPremCertificateErrorInterceptor(
                    onCertificateError = { sslHandshakeException: SSLHandshakeException ->
                        try {
                            onPremConfigFetcherProvider.getOnPremConfigFetcher().fetchFallback()
                        } catch (e: ThreemaException) {
                            logger.error("Failed to fetch OPPF from fallback URL", e)
                            throw sslHandshakeException
                        } catch (e: IllegalStateException) {
                            logger.error("Failed to fetch OPPF from fallback URL", e)
                            throw sslHandshakeException
                        }
                    },
                ),
            )
        }
    }

    private fun getOnPremConfigDomains(
        getOnPremConfig: (OnPremConfigFetcher) -> OnPremConfig?,
    ): OnPremConfigDomains? {
        val onPremConfigFetcher = try {
            onPremConfigFetcherProvider.getOnPremConfigFetcher()
        } catch (e: IllegalStateException) {
            throw IOException(e)
        }
        val config = try {
            getOnPremConfig(onPremConfigFetcher)
        } catch (e: ThreemaException) {
            throw IOException("Failed to fetch OPPF", e)
        }
        return config?.domains
    }

    private fun createSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }

    fun createSslSocketFactory(hostname: String): SSLSocketFactory {
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = {
                getOnPremConfigDomains(
                    getOnPremConfig = { onPremConfigFetcher ->
                        onPremConfigFetcher.getOrFetch()
                    },
                )
            },
            hostnameProvider = { hostname },
        )
        return createSocketFactory(trustManager)
    }
}
