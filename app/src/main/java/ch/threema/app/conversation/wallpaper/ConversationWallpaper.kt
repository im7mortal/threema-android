package ch.threema.app.conversation.wallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.imageResource
import ch.threema.app.R
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.data.datatypes.ConversationId
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConversationWallpaper(
    modifier: Modifier = Modifier,
    conversationId: ConversationId,
) {
    if (!LocalInspectionMode.current) {
        ConversationWallpaperContent(
            modifier = modifier,
            conversationId = conversationId,
        )
    } else {
        ConversationWallpaperDummy(
            modifier = modifier,
        )
    }
}

// TODO(PRD-830): React to wallpaper changes by user
@Composable
private fun ConversationWallpaperContent(
    modifier: Modifier = Modifier,
    conversationId: ConversationId,
    viewModel: ConversationWallpaperViewModel = koinViewModel(),
) {
    val isDarkTheme = isSystemInDarkTheme()
    val wallpaperBitmapKeys = arrayOf(conversationId, isDarkTheme)
    var wallpaperBitmap: ImmutableBitmap? by remember(*wallpaperBitmapKeys) {
        mutableStateOf(null)
    }
    LaunchedEffect(*wallpaperBitmapKeys) {
        wallpaperBitmap = viewModel.provideWallpaper(
            conversationId = conversationId,
            isDarkTheme = isDarkTheme,
        )
    }
    wallpaperBitmap?.let { immutableBitmap ->
        Image(
            modifier = modifier,
            bitmap = immutableBitmap.imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ConversationWallpaperDummy(
    modifier: Modifier = Modifier,
) {
    Image(
        modifier = modifier,
        bitmap = ImageBitmap.imageResource(
            id = if (isSystemInDarkTheme()) {
                R.drawable.wallpaper_dark
            } else {
                R.drawable.wallpaper_light
            },
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
}
