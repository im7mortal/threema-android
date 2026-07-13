package ch.threema.app.conversation.wallpaper

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.services.WallpaperService
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import kotlinx.coroutines.withContext

class ConversationWallpaperViewModel(
    private val dispatcherProvider: DispatcherProvider,
    private val wallpaperService: WallpaperService,
) : ViewModel() {

    suspend fun provideWallpaper(
        conversationId: ConversationId,
        isDarkTheme: Boolean,
    ): ImmutableBitmap? {
        return withContext(dispatcherProvider.io) {
            val wallpaperBitmap: Bitmap? = wallpaperService.getWallpaper(
                /* conversationId = */
                conversationId,
                /* landscape = */
                false,
                /* isTheDarkside = */
                isDarkTheme,
            ).get()
            wallpaperBitmap?.prepareToDraw()
            wallpaperBitmap?.toImmutableBitmap()
        }
    }
}
