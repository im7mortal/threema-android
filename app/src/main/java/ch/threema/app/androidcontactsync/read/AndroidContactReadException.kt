package ch.threema.app.androidcontactsync.read

sealed class AndroidContactReadException : Throwable() {
    /**
     * The permission to read contacts is missing.
     */
    class MissingPermission(override val cause: Throwable? = null) : AndroidContactReadException()

    /**
     * There are raw contacts with the same lookup key but different contact ids. This indicates that the provided data is corrupt and cannot be used
     * reliably.
     *
     * Note that this seems to happen quite often. This can be the case when other apps or the system are synchronizing changes to contacts. In this
     * case it is recommended to try again later.
     */
    class MultipleContactIdsPerLookupKey : AndroidContactReadException()

    /**
     * There are raw contacts with different lookup keys but the same contact id. This indicates that the provided data is corrupt and cannot be used
     * reliably.
     */
    class MultipleLookupKeysPerContactId : AndroidContactReadException()

    /**
     * There are contact data rows that refer to the same raw contact but have different lookup keys. This indicates that the provided data is corrupt
     * and cannot be use reliably.
     */
    class MultipleLookupKeysPerRawContact(override val cause: Throwable? = null) : AndroidContactReadException()

    /**
     * There was an unspecified error reading the contacts. See [cause] for more information.
     */
    class Other(override val message: String? = null, override val cause: Throwable? = null) : AndroidContactReadException()
}
