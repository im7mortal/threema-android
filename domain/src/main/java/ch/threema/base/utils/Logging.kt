package ch.threema.base.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.measureTimedValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Get a logger instance. Should be used by all Threema code like this:
 *
 * ```java
 * // placed at the top of the class
 * private static final Logger logger = getThreemaLogger("MyClass");
 * ```
 *
 * ```kotlin
 * // placed at the top of the file
 * private val logger = getThreemaLogger("MyClass")
 * ```
 */
fun getThreemaLogger(name: String): Logger =
    LoggerFactory.getLogger("ch.threema.$name")

@OptIn(ExperimentalContracts::class)
inline fun <T> withTimeMeasurement(logger: Logger, label: String, vararg args: Any?, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val (result, time) = measureTimedValue(block)
    logger.info("Measured time: '{}' ({}) took {} ms", label, args.joinToString(), time.inWholeMilliseconds)
    return result
}

@OptIn(ExperimentalContracts::class)
inline fun <T> withTimeMeasurement(logger: Logger, label: String, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val (result, time) = measureTimedValue(block)
    logger.info("Measured time: '{}' took {} ms", label, time.inWholeMilliseconds)
    return result
}
