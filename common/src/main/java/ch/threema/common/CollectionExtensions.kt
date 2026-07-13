package ch.threema.common

import java.util.Enumeration

fun <E, C : Collection<E>> C.takeUnlessEmpty(): C? =
    this.takeUnless { it.isEmpty() }

fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) {
        minus(item)
    } else {
        plus(item)
    }

fun <T> List<T>.toEnumeration(): Enumeration<T> =
    object : Enumeration<T> {
        private var count = 0

        override fun hasMoreElements() = count < size

        override fun nextElement(): T {
            if (!hasMoreElements()) {
                throw NoSuchElementException()
            }
            return get(count++)
        }
    }

fun <Key, Value : Any> MutableMap<Key, Value>.putIfNotNull(key: Key, value: Value?) {
    if (value != null) {
        put(key, value)
    }
}

fun <T, U> Collection<T>.hasDuplicatesBy(getValue: (T) -> U): Boolean {
    val items = mutableSetOf<U>()
    forEach { item ->
        if (!items.add(getValue(item))) {
            return true
        }
    }
    return false
}
