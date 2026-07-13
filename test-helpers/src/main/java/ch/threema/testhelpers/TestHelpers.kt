package ch.threema.testhelpers

import app.cash.turbine.TurbineTestContext
import ch.threema.common.DispatcherProvider
import ch.threema.common.models.CryptographicByteArray
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime

/**
 * Generate an array of length `length` and fill it using a non-cryptographically-secure
 * random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun nonSecureRandomArray(length: Int): ByteArray {
    return Random.nextBytes(length)
}

fun cryptographicByteArrayOf(vararg bytes: Byte) = CryptographicByteArray(bytes)

/**
 * Generate a random Threema ID using a non-cryptographically-secure random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun randomIdentity(): String {
    val allowedChars = ('A'..'Z') + ('0'..'9')
    return (1..8)
        .map { allowedChars.random() }
        .joinToString("")
}

@Suppress("FunctionName")
fun MUST_NOT_BE_CALLED(): Nothing {
    throw UnsupportedOperationException("This method must not be called")
}

fun Any.loadResource(file: String): String =
    loadResourceAsBytes(file).toString(Charsets.UTF_8)

fun Any.loadResourceAsBytes(file: String): ByteArray =
    (javaClass.classLoader.getResourceAsStream(file) ?: error("Resource file '$file' not found"))
        .use {
            it.readBytes()
        }

suspend fun <T> TurbineTestContext<T>.expectItem(expected: T) {
    assertEquals(expected, awaitItem())
}

fun createTempDirectory(prefix: String = "test"): File {
    val directory = File.createTempFile(prefix, "test")
    directory.delete()
    directory.mkdirs()
    return directory
}

fun utcDate(year: Int, month: Int, dayOfMonth: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Instant =
    LocalDateTime.of(year, month, dayOfMonth, hour, minute, second).atZone(ZoneOffset.UTC).toInstant()

fun TestScope.testDispatcherProvider(): DispatcherProvider =
    TestDispatcherProvider(StandardTestDispatcher(testScheduler))

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.unconfinedTestDispatcherProvider(): DispatcherProvider =
    TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))

class TestDispatcherProvider(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    private val mainDispatcher: MainCoroutineDispatcher = getTestMainDispatcher(testDispatcher),
) : DispatcherProvider {
    override val main: MainCoroutineDispatcher
        get() = mainDispatcher
    override val worker: CoroutineDispatcher
        get() = testDispatcher
    override val io: CoroutineDispatcher
        get() = testDispatcher
}

private fun getTestMainDispatcher(testDispatcher: TestDispatcher) = object : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = this

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        testDispatcher.dispatch(context, block)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val TestScope.elapsedVirtualTime: Duration
    get() = currentTime.milliseconds
