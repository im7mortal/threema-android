package ch.threema.app.errorreporting

import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentErrorTypeIdStoreTest {

    private lateinit var filesDirectory: File
    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var recentErrorTypeIdStore: RecentErrorTypeIdStore

    @BeforeTest
    fun setUp() {
        filesDirectory = createTempDirectory()
        testTimeProvider = TestTimeProvider()
        recentErrorTypeIdStore = RecentErrorTypeIdStore(
            appDirectoryProvider = mockk {
                every { appDataDirectory } returns filesDirectory
            },
            timeProvider = testTimeProvider,
        )
    }

    @AfterTest
    fun tearDown() {
        filesDirectory.deleteRecursively()
    }

    @Test
    fun `recent error type ids can be recorded and retrieved`() {
        assertEquals(emptySet(), recentErrorTypeIdStore.getRecentErrorTypeIds())

        recentErrorTypeIdStore.recordAsRecent(setOf(ID1, ID2))
        assertEquals(setOf(ID1, ID2), recentErrorTypeIdStore.getRecentErrorTypeIds())

        recentErrorTypeIdStore.recordAsRecent(setOf(ID3))
        assertEquals(setOf(ID1, ID2, ID3), recentErrorTypeIdStore.getRecentErrorTypeIds())
    }

    @Test
    fun `error type ids are no longer considered recent after a while`() {
        recentErrorTypeIdStore.recordAsRecent(setOf(ID1))
        testTimeProvider.advanceBy(MAX_AGE / 2)
        assertEquals(setOf(ID1), recentErrorTypeIdStore.getRecentErrorTypeIds())

        recentErrorTypeIdStore.recordAsRecent(setOf(ID2))
        assertEquals(setOf(ID1, ID2), recentErrorTypeIdStore.getRecentErrorTypeIds())

        testTimeProvider.advanceBy(MAX_AGE / 2)
        assertEquals(setOf(ID2), recentErrorTypeIdStore.getRecentErrorTypeIds())
    }

    @Test
    fun `recording an error type id resets its time`() {
        recentErrorTypeIdStore.recordAsRecent(setOf(ID1))
        testTimeProvider.advanceBy(MAX_AGE / 2)
        recentErrorTypeIdStore.recordAsRecent(setOf(ID1))
        testTimeProvider.advanceBy(MAX_AGE / 2)
        assertEquals(setOf(ID1), recentErrorTypeIdStore.getRecentErrorTypeIds())
    }

    companion object {
        private val ID1 = ErrorTypeId("1")
        private val ID2 = ErrorTypeId("2")
        private val ID3 = ErrorTypeId("3")

        private val MAX_AGE = RecentErrorTypeIdStore.MAX_AGE
    }
}
