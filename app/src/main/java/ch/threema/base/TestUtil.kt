package ch.threema.base

import ch.threema.common.isClassAvailable

private val isTestClassAvailable by lazy(LazyThreadSafetyMode.PUBLICATION) {
    isClassAvailable("org.junit.Test")
}

private val isThreemaTestRunnerAvailable by lazy(LazyThreadSafetyMode.PUBLICATION) {
    isClassAvailable("ch.threema.app.ThreemaTestRunner")
}

fun isInTest(): Boolean =
    isTestClassAvailable

/**
 * Checks whether we are running in a device test. This should be used very sparingly and only as a workaround in situations where
 * proper mocking is not feasible.
 */
fun isInDeviceTest(): Boolean =
    isThreemaTestRunnerAvailable
