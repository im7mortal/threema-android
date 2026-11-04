package ch.threema.app.wallpapers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.threema.android.transform
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.files.WallpaperFileHandleProvider
import ch.threema.app.utils.BitmapUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.copyTo
import ch.threema.common.files.FileHandle
import ch.threema.data.datatypes.ConversationId
import java.io.IOException
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("WallpaperServiceImpl")

class WallpaperServiceImpl(
    private val appContext: Context,
    private val wallpaperFileHandleProvider: WallpaperFileHandleProvider,
    private val dispatcherProvider: DispatcherProvider,
) : WallpaperService {

    override fun isDefaultWallpaperSupported(): Boolean =
        !BuildFlavor.IS_WORK

    override fun getConversationWallpaper(conversationId: ConversationId): WallpaperService.Wallpaper =
        getWallpaper(wallpaperFileHandleProvider.get(conversationId))

    override fun getGlobalWallpaper(): WallpaperService.Wallpaper {
        val wallpaper = getWallpaper(wallpaperFileHandleProvider.getGlobal())
        if (wallpaper is WallpaperService.Wallpaper.Default && !isDefaultWallpaperSupported()) {
            return WallpaperService.Wallpaper.Empty
        }
        return wallpaper
    }

    private fun getWallpaper(fileHandle: FileHandle): WallpaperService.Wallpaper =
        if (fileHandle.exists()) {
            if (fileHandle.isEmpty()) {
                WallpaperService.Wallpaper.Empty
            } else {
                WallpaperService.Wallpaper.Custom(null)
            }
        } else {
            WallpaperService.Wallpaper.Default
        }

    @Throws(IOException::class)
    override suspend fun setConversationWallpaper(
        conversationId: ConversationId,
        wallpaper: WallpaperService.Wallpaper,
    ): Unit = withContext(dispatcherProvider.io) {
        setWallpaper(wallpaperFileHandleProvider.get(conversationId), wallpaper)
    }

    @Throws(IOException::class)
    override suspend fun setGlobalWallpaper(wallpaper: WallpaperService.Wallpaper): Unit = withContext(dispatcherProvider.io) {
        setWallpaper(wallpaperFileHandleProvider.getGlobal(), wallpaper)
    }

    private fun setWallpaper(fileHandle: FileHandle, wallpaper: WallpaperService.Wallpaper) {
        fileHandle.delete()
        when (wallpaper) {
            is WallpaperService.Wallpaper.Custom -> {
                wallpaper.file?.let { uri ->
                    appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.copyTo(fileHandle)
                    }
                }
            }
            WallpaperService.Wallpaper.Default -> Unit
            WallpaperService.Wallpaper.Empty -> {
                fileHandle.create()
            }
        }
    }

    @Throws(IOException::class)
    override suspend fun deleteAll() = withContext(dispatcherProvider.io) {
        wallpaperFileHandleProvider.deleteAll()
    }

    override suspend fun getWallpaperBitmap(
        conversationId: ConversationId,
        landscape: Boolean,
        isDarkTheme: Boolean,
    ): Bitmap? = withContext(dispatcherProvider.io) {
        val fileHandleCustomWallpaper = wallpaperFileHandleProvider.get(conversationId)
        if (fileHandleCustomWallpaper.exists()) {
            if (fileHandleCustomWallpaper.isEmpty()) {
                return@withContext null
            }
            return@withContext loadBitmap(fileHandleCustomWallpaper)
        }

        val fileHandleGlobalWallpaper = wallpaperFileHandleProvider.getGlobal()
        if (fileHandleGlobalWallpaper.exists()) {
            if (fileHandleGlobalWallpaper.isEmpty()) {
                return@withContext null
            }
            return@withContext loadBitmap(fileHandleGlobalWallpaper)
        }

        return@withContext getDefaultWallpaperBitmap(
            landscape = landscape,
            isDarkTheme = isDarkTheme,
        )
    }

    private fun loadBitmap(fileHandle: FileHandle): Bitmap? {
        val options = BitmapFactory.Options()
        options.inSampleSize = 1
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return try {
            fileHandle.read().use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            logger.error("Failed to load bitmap", e)
            null
        }
    }

    private fun getDefaultWallpaperBitmap(
        landscape: Boolean,
        isDarkTheme: Boolean,
    ): Bitmap? {
        if (!isDefaultWallpaperSupported()) {
            return null
        }

        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ALPHA_8
        options.inSampleSize = 1

        val resource = if (isDarkTheme) R.drawable.wallpaper_dark else R.drawable.wallpaper_light
        val bitmap = try {
            BitmapFactory.decodeResource(appContext.resources, resource, options)
        } catch (e: Exception) {
            logger.error("Failed to load default wallpaper", e)
            null
        }

        if (bitmap != null && landscape) {
            return bitmap.transform { bitmap ->
                BitmapUtil.rotateBitmap(bitmap, 90f)
            }
        }
        return bitmap
    }
}
