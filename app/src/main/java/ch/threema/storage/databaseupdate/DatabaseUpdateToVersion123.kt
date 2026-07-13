package ch.threema.storage.databaseupdate

/**
 * The logic of this [DatabaseUpdate] was moved to [DatabaseUpdateToVersion128] to re-apply it.
 */
class DatabaseUpdateToVersion123() : DatabaseUpdate {
    override fun run() {
    }

    override val version = 123
}
