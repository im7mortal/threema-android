package ch.threema.app.webviews.usecases

import androidx.core.net.toUri
import ch.threema.app.BuildConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MayOpenUriExternallyUseCaseTest {

    @Test
    fun mayOpenUri() {
        listOf(
            "https://threema.com",
            "http://example.com",
            "mailto:john.doe@example.com",
            "tel:+4100000000",
            "threema://compose",
            "threemawork://add",
            "threemaonprem://add",
            "${BuildConfig.uriScheme}://foobar",
        )
            .forEach { uri ->
                assertTrue(MayOpenUriExternallyUseCase().call(uri.toUri()))
            }
    }

    @Test
    fun mayNotOpenUri() {
        listOf(
            "intent://foobar",
            "file:///foobar",
            "content://foobar",
        )
            .forEach { uri ->
                assertFalse(MayOpenUriExternallyUseCase().call(uri.toUri()))
            }
    }
}
