package ch.threema.storage

class DatabaseDowngradeHelperImpl : DatabaseDowngradeHelper {
    @Throws(DatabaseDowngradeException::class)
    override fun onDowngrade(oldVersion: Int) {
        // Downgrading is only supported on dev builds
        throw DatabaseDowngradeException(oldVersion)
    }
}
