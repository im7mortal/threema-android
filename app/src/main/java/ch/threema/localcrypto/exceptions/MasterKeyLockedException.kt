package ch.threema.localcrypto.exceptions

/**
 * Thrown when the master key is accessed when it is locked, or when an operation is attempted that requires the master key to be unlocked
 * (e.g. changing the passphrase) while it is actually locked.
 */
class MasterKeyLockedException : IllegalStateException("Master key is locked")
