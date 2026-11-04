package ch.threema.app.activities

/**
 * Subclasses of [ThreemaActivity] marked with this interface are exempt from being protected by the app lock. Usually this means that the activity
 * handles app locking itself or does not require it at all.
 */
interface AppLockExempt
