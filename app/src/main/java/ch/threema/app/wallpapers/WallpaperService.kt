package ch.threema.app.wallpapers

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.WorkerThread
import ch.threema.data.datatypes.ConversationId
import java.io.IOException
import kotlinx.coroutines.runBlocking

interface WallpaperService {

    fun isDefaultWallpaperSupported(): Boolean

    fun getConversationWallpaper(conversationId: ConversationId): Wallpaper

    fun getGlobalWallpaper(): Wallpaper

    @Throws(IOException::class)
    suspend fun setGlobalWallpaper(wallpaper: Wallpaper)

    @Throws(IOException::class)
    suspend fun setConversationWallpaper(conversationId: ConversationId, wallpaper: Wallpaper)

    @WorkerThread
    @Throws(IOException::class)
    @Deprecated("Use setConversationWallpaper instead")
    fun setConversationWallpaperSync(conversationId: ConversationId, wallpaper: Wallpaper) = runBlocking {
        setConversationWallpaper(conversationId, wallpaper)
    }

    @Throws(IOException::class)
    suspend fun deleteAll()

    suspend fun getWallpaperBitmap(
        conversationId: ConversationId,
        landscape: Boolean,
        isDarkTheme: Boolean,
    ): Bitmap?

    sealed interface Wallpaper {
        data object Default : Wallpaper
        data object Empty : Wallpaper
        data class Custom(val file: Uri? = null) : Wallpaper
    }
}
