package org.qstuff.qplayer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruCacheTest {

    @Test
    fun `evicts the least recently used entry`() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")          // "b" is now the least recently used
        cache.put("c", 3)

        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `put replaces an existing entry without growing`() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("a", 10)
        cache.put("c", 3)       // evicts "b", not the refreshed "a"

        assertEquals(10, cache.get("a"))
        assertNull(cache.get("b"))
    }
}
