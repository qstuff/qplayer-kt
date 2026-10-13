package org.qstuff.qplayer.util

/**
 * Minimal least-recently-used cache in plain Kotlin (android.util.LruCache isn't multiplatform):
 * [get] marks an entry as used, [put] evicts the least recently used entries beyond [maxSize].
 * Not thread-safe — use it from one thread (the ViewModels use it on the main thread).
 */
class LruCache<K : Any, V : Any>(private val maxSize: Int) {

    // Insertion order = usage order: the first key is the least recently used one.
    private val entries = LinkedHashMap<K, V>()

    fun get(key: K): V? = entries.remove(key)?.also { entries[key] = it }

    fun put(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxSize) entries.remove(entries.keys.first())
    }
}
