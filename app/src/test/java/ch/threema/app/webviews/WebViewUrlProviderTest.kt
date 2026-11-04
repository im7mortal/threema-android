package ch.threema.app.webviews

import android.content.Context
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.utils.LocaleProvider
import ch.threema.test.TestContext
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebViewUrlProviderTest {
    private lateinit var contextMock: Context
    private lateinit var localeProviderMock: LocaleProvider
    private lateinit var webViewUrlProvider: WebViewUrlProvider

    @BeforeTest
    fun setUp() {
        contextMock = TestContext.create()
        localeProviderMock = mockk {
            every { getAppLanguage() } returns "gsw"
        }
        webViewUrlProvider = WebViewUrlProvider(
            appContext = contextMock,
            localeProvider = localeProviderMock,
        )
    }

    @Test
    fun `get EULA url`() {
        assertEquals(
            contextMock.getString(R.string.eula_url, "de", BuildConfig.VERSION_NAME, "light"),
            webViewUrlProvider.getEulaURL(isDarkTheme = false),
        )
        assertEquals(
            contextMock.getString(R.string.eula_url, "de", BuildConfig.VERSION_NAME, "dark"),
            webViewUrlProvider.getEulaURL(isDarkTheme = true),
        )
    }

    @Test
    fun `get privacy policy url`() {
        assertEquals(
            contextMock.getString(R.string.privacy_policy_url, "de", BuildConfig.VERSION_NAME, "light"),
            webViewUrlProvider.getPrivacyPolicyURL(isDarkTheme = false),
        )
        assertEquals(
            contextMock.getString(R.string.privacy_policy_url, "de", BuildConfig.VERSION_NAME, "dark"),
            webViewUrlProvider.getPrivacyPolicyURL(isDarkTheme = true),
        )
    }

    @Test
    fun `get referral terms of service policy url`() {
        assertEquals(
            contextMock.getString(R.string.referral_terms_of_service_url, "de", "light"),
            webViewUrlProvider.getReferralTermsOfServiceURL(isDarkTheme = false),
        )
        assertEquals(
            contextMock.getString(R.string.referral_terms_of_service_url, "de", "dark"),
            webViewUrlProvider.getReferralTermsOfServiceURL(isDarkTheme = true),
        )
    }

    @Test
    fun `get terms of service url`() {
        assertEquals(
            contextMock.getString(R.string.terms_of_service_url, "de", BuildConfig.VERSION_NAME, "light"),
            webViewUrlProvider.getTermsOfServiceURL(isDarkTheme = false),
        )
        assertEquals(
            contextMock.getString(R.string.terms_of_service_url, "de", BuildConfig.VERSION_NAME, "dark"),
            webViewUrlProvider.getTermsOfServiceURL(isDarkTheme = true),
        )
    }

    @Test
    fun `get work explain url`() {
        assertEquals(
            contextMock.getString(R.string.work_explain_url, "de"),
            webViewUrlProvider.getWorkExplainURL(),
        )
    }
}
