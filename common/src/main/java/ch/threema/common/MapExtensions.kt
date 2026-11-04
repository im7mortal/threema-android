package ch.threema.common

fun <K : Any, V> Map<K?, V>.filterNotNullKeys(): Map<K, V> = buildMap {
    for ((key, value) in this@filterNotNullKeys) {
        if (key != null) put(key, value)
    }
}
