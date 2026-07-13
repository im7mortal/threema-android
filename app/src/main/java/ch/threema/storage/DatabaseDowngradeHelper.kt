package ch.threema.storage

interface DatabaseDowngradeHelper {
    @Throws(DatabaseDowngradeException::class)
    fun onDowngrade(oldVersion: Int)
}
