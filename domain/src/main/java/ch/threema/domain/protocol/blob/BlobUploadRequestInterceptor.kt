package ch.threema.domain.protocol.blob

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Http
import ch.threema.common.HttpResponseException
import ch.threema.common.buildNew
import ch.threema.common.buildRequest
import ch.threema.common.copyHeaderFrom
import ch.threema.common.decodeJson
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.models.CryptographicByteArray
import ch.threema.common.postJson
import ch.threema.common.serializers.CryptographicBase64Serializer
import ch.threema.common.serializers.SecondsDurationSerializer
import ch.threema.common.toCryptographicByteArray
import ch.threema.domain.protocol.ChallengeResponseHelper
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.IdentityString
import java.io.IOException
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

private val logger = getThreemaLogger("BlobUploadRequestInterceptor")

class BlobUploadRequestInterceptor(
    private val serverAddressProvider: ServerAddressProvider,
    private val identityStore: IdentityStore,
    private val challengeResponseHelper: ChallengeResponseHelper,
    private val timeSource: TimeSource.WithComparableMarks,
    private val getLegacyAuthToken: () -> String?,
) : Interceptor {

    /**
     * If the auth token request fails with a 404, we are likely dealing with an old OnPrem server,
     * which does not yet support this authentication mechanism. Therefore, we temporarily fall back to the legacy
     * authentication mechanism if we have a token for it. After [TOKEN_SUPPORT_RECHECK_TIME], we check again.
     */
    @Volatile
    private var useLegacyAuthTokenUntil: ComparableTimeMark? = null

    @Volatile
    private var cachedAuthToken: CachedAuthToken? = null
        get() = field?.takeIf { it.validUntil > timeSource.markNow() }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (useLegacyAuthTokenUntil?.let { it > timeSource.markNow() } == true) {
            return proceedWithLegacyAuthentication(chain)
        }

        val authToken = try {
            getAuthToken(chain)
        } catch (e: HttpResponseException) {
            if (e.code == Http.StatusCode.NOT_FOUND) {
                val legacyToken = getLegacyAuthToken()
                if (legacyToken != null) {
                    logger.info("Switching to legacy auth for blob server requests")
                    useLegacyAuthTokenUntil = timeSource.markNow() + TOKEN_SUPPORT_RECHECK_TIME
                    return proceedWithLegacyAuthentication(chain)
                }
            }
            throw e
        }

        val request = chain.request().buildNew {
            header(Http.Header.AUTHORIZATION, "ThreemaBlobToken $authToken")
        }
        return chain.proceed(request)
    }

    private fun proceedWithLegacyAuthentication(chain: Interceptor.Chain): Response {
        val request = chain.request().buildNew {
            getLegacyAuthToken()?.let { authToken ->
                header(Http.Header.AUTHORIZATION, "Token $authToken")
            }
        }
        return chain.proceed(request)
    }

    private fun getAuthToken(chain: Interceptor.Chain): String {
        cachedAuthToken?.let {
            return it.authToken
        }

        val challenge = chain.proceed(
            buildRequest {
                url(serverAddressProvider.getDirectoryServerUrl(API_PATH))
                postJson(json, BlobServerCredentialsRequest(identityStore.requireIdentityString()))
                copyHeaderFrom(chain.request(), Http.Header.USER_AGENT)
            },
        )
            .getSuccessBodyOrThrow()
            .use { responseBody ->
                responseBody.decodeJson<BlobServerCredentialsResponse>(json)
            }

        val sharedSecret = identityStore.calcSharedSecret(challenge.publicKey.value)
            ?: throw IOException("Failed to calculate shared secret")
        val challengeResponseData = try {
            challengeResponseHelper.calculateResponse(sharedSecret, challenge.token.value)
        } catch (e: IllegalArgumentException) {
            throw IOException("Failed to calculate challenge response", e)
        }

        val authTokenResponse = chain.proceed(
            buildRequest {
                url(serverAddressProvider.getDirectoryServerUrl(API_PATH))
                postJson(
                    json,
                    BlobServerCredentialsChallengeResponseRequest(
                        identity = identityStore.requireIdentityString(),
                        token = challenge.token,
                        response = challengeResponseData.toCryptographicByteArray(),
                    ),
                )
                copyHeaderFrom(chain.request(), Http.Header.USER_AGENT)
            },
        )
            .getSuccessBodyOrThrow()
            .use { responseBody ->
                responseBody.decodeJson<BlobServerCredentialsAuthTokenResponse>(json)
            }

        val authToken = authTokenResponse.authToken
            ?: throw IOException("Failed to obtain auth token")

        updateTokenCache(authTokenResponse)

        return authToken
    }

    private fun updateTokenCache(authTokenResponse: BlobServerCredentialsAuthTokenResponse) {
        authTokenResponse.expiresIn
            ?.let { expiresIn -> expiresIn - TOKEN_EXPIRATION_SAFETY_MARGIN }
            ?.let { expiresIn ->
                cachedAuthToken = CachedAuthToken(
                    authToken = authTokenResponse.authToken ?: return,
                    validUntil = timeSource.markNow() + expiresIn,
                )
            }
    }

    @Serializable
    private data class BlobServerCredentialsRequest(
        val identity: IdentityString,
    )

    @Serializable
    private data class BlobServerCredentialsResponse(
        @Serializable(with = CryptographicBase64Serializer::class)
        val token: CryptographicByteArray,
        @SerialName("tokenRespKeyPub")
        @Serializable(with = CryptographicBase64Serializer::class)
        val publicKey: CryptographicByteArray,
    )

    @Serializable
    private data class BlobServerCredentialsChallengeResponseRequest(
        val identity: IdentityString,
        @Serializable(with = CryptographicBase64Serializer::class)
        val token: CryptographicByteArray,
        @Serializable(with = CryptographicBase64Serializer::class)
        val response: CryptographicByteArray,
    )

    @Serializable
    private data class BlobServerCredentialsAuthTokenResponse(
        @SerialName("token")
        val authToken: String? = null,
        @SerialName("expiration")
        @Serializable(with = SecondsDurationSerializer::class)
        val expiresIn: Duration? = null,
    )

    private data class CachedAuthToken(
        val authToken: String,
        val validUntil: ComparableTimeMark,
    )

    companion object {
        private const val API_PATH = "identity/blob_cred"

        /**
         * To avoid problems with network delays, out-of-sync clocks etc., we discard the cached auth token
         * already shortly before its expiration.
         */
        private val TOKEN_EXPIRATION_SAFETY_MARGIN = 1.minutes

        /**
         * If we detect that the server does not (yet) support the regular token auth mechanism,
         * we remember this for a while and then recheck again on the first request after this timeout.
         */
        private val TOKEN_SUPPORT_RECHECK_TIME = 10.minutes

        private val json = Json {
            ignoreUnknownKeys = true
        }
    }
}
