package ch.threema.app.conversation.wallpaper

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.wallpapers.WallpaperService
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import kotlinx.coroutines.withContext

class ConversationWallpaperViewModel(
    private val wallpaperService: WallpaperService,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    suspend fun provideWallpaper(
        conversationId: ConversationId,
        isDarkTheme: Boolean,
    ): ImmutableBitmap? = withContext(dispatcherProvider.io) {
        val wallpaperBitmap: Bitmap? = wallpaperService.getWallpaperBitmap(
            conversationId = conversationId,
            landscape = false,
            isDarkTheme = isDarkTheme,
        )
        wallpaperBitmap?.prepareToDraw()
        wallpaperBitmap?.toImmutableBitmap()
    }
}
