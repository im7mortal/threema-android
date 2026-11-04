package ch.threema.domain.protocol.blob

import ch.threema.common.Http
import ch.threema.common.HttpResponseException
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.domain.protocol.ChallengeResponseHelper
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.stores.IdentityStore
import ch.threema.testhelpers.bodyAsString
import ch.threema.testhelpers.createOkHttpClient
import ch.threema.testhelpers.path
import ch.threema.testhelpers.verifyNoUncheckedRequests
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import testdata.TestData

class BlobUploadRequestInterceptorTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var serverAddressProviderMock: ServerAddressProvider
    private lateinit var identityStoreMock: IdentityStore
    private lateinit var challengeResponseHelperMock: ChallengeResponseHelper
    private lateinit var timeSource: TestTimeSource
    private lateinit var mockWebServer: MockWebServer
    private var legacyToken: String? = null

    @BeforeTest
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        serverAddressProviderMock = mockk {
            every { getDirectoryServerUrl(any()) } answers { mockWebServer.url(firstArg()).toString() }
        }
        identityStoreMock = mockk {
            every { requireIdentityString() } returns TestData.Identities.ME.value
            every { calcSharedSecret(PUBLIC_KEY) } returns SHARED_SECRET
        }
        challengeResponseHelperMock = mockk {
            every { calculateResponse(SHARED_SECRET, CHALLENGE_TOKEN) } returns CHALLENGE_RESPONSE
        }
        timeSource = TestTimeSource()
        okHttpClient = createOkHttpClient {
            addInterceptor(
                BlobUploadRequestInterceptor(
                    serverAddressProvider = serverAddressProviderMock,
                    identityStore = identityStoreMock,
                    challengeResponseHelper = challengeResponseHelperMock,
                    timeSource = timeSource,
                    getLegacyAuthToken = { legacyToken },
                ),
            )
        }
    }

    @Test
    fun `blob upload request is made with token authentication`() {
        enqueueCredentialsResponse()
        enqueueChallengeResponse()
        enqueueBlobResponse()

        val response = makeBlobUploadRequest()

        verifyCredentialsRequest()
        verifyChallengeResponseRequest()
        verifyBlobUploadRequest()
        verifyBlobResponse(response)

        mockWebServer.verifyNoUncheckedRequests()
    }

    @Test
    fun `token authentication is cached until it expires`() {
        enqueueCredentialsResponse()
        enqueueChallengeResponse()
        enqueueBlobResponse()

        // The first request fetches the auth token and caches it
        makeBlobUploadRequest()
        verifyCredentialsRequest()
        verifyChallengeResponseRequest()
        verifyBlobUploadRequest()

        // The second request should use the cached token
        timeSource += 5.minutes
        enqueueBlobResponse()
        val blobResponse2 = makeBlobUploadRequest()
        verifyBlobResponse(blobResponse2)
        verifyBlobUploadRequest()

        // The third request should fetch a new auth token
        timeSource += 5.minutes
        enqueueCredentialsResponse()
        enqueueChallengeResponse()
        enqueueBlobResponse()
        val blobResponse3 = makeBlobUploadRequest()
        verifyBlobResponse(blobResponse3)
        verifyCredentialsRequest()
        verifyChallengeResponseRequest()
        verifyBlobUploadRequest()

        mockWebServer.verifyNoUncheckedRequests()
    }

    @Test
    fun `fall back to legacy auth token if fetching auth token is not supported and legacy token is available`() {
        legacyToken = LEGACY_AUTH_TOKEN
        enqueueNotFoundResponse()
        enqueueBlobResponse()

        val response = makeBlobUploadRequest()

        verifyCredentialsRequest()
        verifyLegacyBlobRequest()
        verifyBlobResponse(response)

        // subsequent requests will always use the legacy auth token
        timeSource += 5.minutes
        enqueueBlobResponse()
        val response2 = makeBlobUploadRequest()
        verifyLegacyBlobRequest()
        verifyBlobResponse(response2)

        // after 2 hours, we check again if the server might support the new auth mechanism now, but it still doesn't
        timeSource += 2.hours
        enqueueNotFoundResponse()
        enqueueBlobResponse()
        val response3 = makeBlobUploadRequest()
        verifyCredentialsRequest()
        verifyLegacyBlobRequest()
        verifyBlobResponse(response3)

        // if the server starts supporting the new auth mechanism, we switch back again from legacy auth
        timeSource += 2.hours
        enqueueCredentialsResponse()
        enqueueChallengeResponse()
        enqueueBlobResponse()
        val response4 = makeBlobUploadRequest()
        verifyCredentialsRequest()
        verifyChallengeResponseRequest()
        verifyBlobUploadRequest()
        verifyBlobResponse(response4)

        mockWebServer.verifyNoUncheckedRequests()
    }

    @Test
    fun `do not fall back to legacy auth token if no legacy token is available`() {
        legacyToken = null
        enqueueNotFoundResponse()

        val exception = assertFailsWith<HttpResponseException> {
            makeBlobUploadRequest()
        }

        assertEquals(Http.StatusCode.NOT_FOUND, exception.code)
        verifyCredentialsRequest()
        mockWebServer.verifyNoUncheckedRequests()
    }

    private fun makeBlobUploadRequest(): Response {
        val blobRequest = buildRequest {
            post(RequestBody.EMPTY)
            url(mockWebServer.url("/upload"))
            header(Http.Header.USER_AGENT, USER_AGENT)
        }
        return okHttpClient.execute(blobRequest)
    }

    private fun enqueueCredentialsResponse() {
        mockWebServer.enqueue(
            MockResponse(
                body = """{"token":"dG9rZW4=","tokenRespKeyPub":"cHVibGljLWtleQ=="}""",
            ),
        )
    }

    private fun enqueueChallengeResponse() {
        mockWebServer.enqueue(
            MockResponse(
                body = """{"success":true,"token":"$AUTH_TOKEN","expiration":600}""",
            ),
        )
    }

    private fun enqueueBlobResponse() {
        mockWebServer.enqueue(
            MockResponse(
                body = "blob-data",
            ),
        )
    }

    private fun enqueueNotFoundResponse() {
        mockWebServer.enqueue(
            MockResponse(code = Http.StatusCode.NOT_FOUND),
        )
    }

    private fun verifyCredentialsRequest() {
        mockWebServer.takeRequest().let { request ->
            assertEquals("POST", request.method)
            assertEquals("/identity/blob_cred", request.path())
            assertEquals(
                """{"identity":"${TestData.Identities.ME}"}""",
                request.bodyAsString(),
            )
            assertEquals(USER_AGENT, request.headers[Http.Header.USER_AGENT])
        }
    }

    private fun verifyChallengeResponseRequest() {
        mockWebServer.takeRequest().let { request ->
            assertEquals("POST", request.method)
            assertEquals("/identity/blob_cred", request.path())
            assertEquals(
                """{"identity":"${TestData.Identities.ME}","token":"dG9rZW4=","response":"cmVzcG9uc2U="}""",
                request.bodyAsString(),
            )
            assertEquals(USER_AGENT, request.headers[Http.Header.USER_AGENT])
        }
    }

    private fun verifyBlobUploadRequest() {
        mockWebServer.takeRequest().let { request ->
            assertEquals("POST", request.method)
            assertEquals("/upload", request.path())
            assertEquals("ThreemaBlobToken $AUTH_TOKEN", request.headers[Http.Header.AUTHORIZATION])
            assertEquals(USER_AGENT, request.headers[Http.Header.USER_AGENT])
        }
    }

    private fun verifyLegacyBlobRequest() {
        mockWebServer.takeRequest().let { request ->
            assertEquals("POST", request.method)
            assertEquals("/upload", request.path())
            assertEquals("Token $LEGACY_AUTH_TOKEN", request.headers[Http.Header.AUTHORIZATION])
            assertEquals(USER_AGENT, request.headers[Http.Header.USER_AGENT])
        }
    }

    private fun verifyBlobResponse(response: Response) {
        assertEquals(200, response.code)
        assertEquals("blob-data", response.body.string())
    }

    companion object {
        private val PUBLIC_KEY = "public-key".toByteArray()
        private val SHARED_SECRET = "shared-secret".toByteArray()
        private val CHALLENGE_TOKEN = "token".toByteArray()
        private val CHALLENGE_RESPONSE = "response".toByteArray()
        private const val AUTH_TOKEN = "auth-token"
        private const val LEGACY_AUTH_TOKEN = "legacy-auth-token"
        private const val USER_AGENT = "Test-User-Agent"
    }
}
