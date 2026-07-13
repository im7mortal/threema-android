package ch.threema.logging

import android.util.Log
import ch.threema.app.BuildConfig
import ch.threema.app.ThreemaApplication
import ch.threema.base.HAS_DEV_FEATURES
import ch.threema.base.isInDeviceTest
import ch.threema.base.isInTest
import ch.threema.logging.backend.DebugLogFileBackend
import ch.threema.logging.backend.DebugLogFileManager
import ch.threema.logging.backend.DebugToasterBackend
import ch.threema.logging.backend.LogBackend
import ch.threema.logging.backend.LogcatBackend

class LogBackendFactoryImpl : LogBackendFactory {
    override fun getBackends(minLogLevel: Int): List<LogBackend> =
        buildList {
            val isInTest = isInTest()
            @Suppress("SimplifyBooleanWithConstants")
            if (HAS_DEV_FEATURES && (!isInTest || isInDeviceTest())) {
                add(LogcatBackend(Log.VERBOSE))
            }
            if (BuildConfig.DEBUG && !isInTest) {
                add(DebugToasterBackend(ThreemaApplication.getAppContext()))
            }
            if (!isInTest) {
                add(DebugLogFileBackend(DebugLogFileManager(ThreemaApplication.getAppContext()), minLogLevel))
            }
        }
}
