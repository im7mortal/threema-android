package ch.threema.test

import androidx.test.core.app.ApplicationProvider
import ch.threema.common.stateFlowOf
import ch.threema.storage.DatabaseOpenHelper
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.DatabaseState
import io.mockk.mockk
import net.zetetic.database.sqlcipher.SQLiteDatabase

class TestDatabaseProvider : DatabaseProvider {
    private val inMemoryDatabaseOpenHelper = DatabaseOpenHelper(
        appContext = ApplicationProvider.getApplicationContext(),
        databaseName = null,
        password = "test-database-key".toByteArray(),
        downgradeHelper = mockk(),
    )

    override val databaseState = stateFlowOf(DatabaseState.READY)

    override val readableDatabase: SQLiteDatabase
        get() = inMemoryDatabaseOpenHelper.readableDatabase
    override val writableDatabase: SQLiteDatabase
        get() = inMemoryDatabaseOpenHelper.writableDatabase
}
