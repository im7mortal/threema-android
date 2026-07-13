package ch.threema.common

import java.security.SecureRandom
import kotlin.time.TimeSource
import org.koin.dsl.module

val commonModule = module {
    factory<DispatcherProvider> { DispatcherProvider.default }
    factory<TimeProvider> { TimeProvider.default }
    factory<SecureRandom> { secureRandom() }
    factory<UUIDGenerator> { UUIDGenerator.default }
    factory<TimeSource.WithComparableMarks> { TimeSource.Monotonic }
}
