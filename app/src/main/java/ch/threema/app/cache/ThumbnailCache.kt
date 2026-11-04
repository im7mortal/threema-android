package ch.threema.app.cache

import android.graphics.Bitmap
import android.util.LruCache
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("ThumbnailCache")

class ThumbnailCache {
    private val lock = Any()
    private val thumbnails: LruCache<Int, Bitmap>

    init {
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

        val cacheSize = (maxMemory / 16).coerceAtMost(16 * 1024) // 16 MB max
        logger.debug("init size = {}", cacheSize)

        thumbnails = object : LruCache<Int, Bitmap>(cacheSize) {
            // The cache size is measured in kilobytes rather than number of items.
            override fun sizeOf(key: Int, bitmap: Bitmap): Int =
                bitmap.getByteCount() / 1024

            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: Bitmap?,
                newValue: Bitmap?,
            ) {
                // We should not recycle bitmaps here as they might still be referenced by an image view
                // but the cache is too small to hold it. Instead, we rely on the garbage collector.
            }
        }
    }

    fun get(index: Int?): Bitmap? = synchronized(lock) {
        thumbnails.get(index)
    }

    fun set(index: Int?, bitmap: Bitmap?) {
        if (index != null && bitmap != null) {
            synchronized(lock) {
                thumbnails.put(index, bitmap)
            }
        }
    }

    fun flush() {
        synchronized(lock) {
            logger.debug("evictAll")
            thumbnails.evictAll()
        }
    }
}
