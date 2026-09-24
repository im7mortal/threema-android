package ch.threema.common

import kotlin.reflect.KClass

inline fun <reified T : Throwable> Throwable.hasInCauseChain(maxDepth: Int = 5): Boolean =
    hasInCauseChain(T::class, maxDepth)

fun <T : Throwable> Throwable.hasInCauseChain(causeType: KClass<T>, maxDepth: Int): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null) {
        if (causeType.isInstance(current)) {
            return true
        }
        depth++
        if (depth > maxDepth || current.cause == current) {
            break
        }
        current = current.cause
    }
    return false
}
