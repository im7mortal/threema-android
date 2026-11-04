package ch.threema.app.wallpapers

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import ch.threema.android.bindExtra
import ch.threema.android.buildActivityIntent
import ch.threema.android.dismissDialog
import ch.threema.android.getParcelableExtraCompat
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.CropImageActivity
import ch.threema.app.activities.CropImageActivity.CropImageParameters
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.dialogs.BottomSheetDialog
import ch.threema.app.dialogs.BottomSheetListDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.ui.BottomSheetItem
import ch.threema.app.utils.FileProviderUtil
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.MimeUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ConversationId
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SelectWallpaperActivity")

class SelectWallpaperActivity : ThreemaActivity() {

    private val wallpaperService: WallpaperService by inject()
    private val appDirectoryProvider: AppDirectoryProvider by inject()

    private val target: SelectWallpaperContract.Target by bindExtra {
        val conversationId = getParcelableExtraCompat<ConversationId>(EXTRA_CONVERSATION_ID)
        if (conversationId != null) {
            SelectWallpaperContract.Target.ConversationWallpaper(conversationId)
        } else {
            SelectWallpaperContract.Target.GlobalWallpaper
        }
    }

    private val imageSelectLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            onImageSelected(result.data?.data ?: return@registerForActivityResult)
        } else {
            finish()
        }
    }
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            onImageCropped()
        } else {
            finish()
        }
    }

    private val tempImageFile: File by lazy {
        File(appDirectoryProvider.shareDirectory, ".wallpaper-temp.png")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showBottomSheet()
        }
        setUpDialogCallbacks()
    }

    private fun showBottomSheet() {
        val items = getBottomSheetItems()
        val selectedIndex = getSelectedBottomSheetItemIndex()
        BottomSheetListDialog.newInstance(
            title = R.string.prefs_title_wallpaper_switch,
            items = items,
            selectedIndex = selectedIndex,
            requestKey = DIALOG_TAG_SELECT_WALLPAPER,
        )
            .show(supportFragmentManager, DIALOG_TAG_SELECT_WALLPAPER)
    }

    private fun setUpDialogCallbacks() {
        supportFragmentManager.setFragmentResultListener(DIALOG_TAG_SELECT_WALLPAPER, this) { _, result ->
            BottomSheetDialog.onItemClicked(result) { tag, _ ->
                when (tag) {
                    SELECTOR_TAG_WALLPAPER_DEFAULT -> {
                        setWallpaper(WallpaperService.Wallpaper.Default)
                    }
                    SELECTOR_TAG_WALLPAPER_CUSTOM_IMAGE -> {
                        launchFilePicker()
                    }
                    SELECTOR_TAG_WALLPAPER_EMPTY -> {
                        setWallpaper(WallpaperService.Wallpaper.Empty)
                    }
                }
            }
            BottomSheetDialog.onCanceled(result) {
                finish()
            }
        }
    }

    private fun setWallpaper(wallpaper: WallpaperService.Wallpaper) {
        lifecycleScope.launch {
            try {
                when (val target = target) {
                    is SelectWallpaperContract.Target.ConversationWallpaper -> {
                        wallpaperService.setConversationWallpaper(target.conversationId, wallpaper)
                    }
                    SelectWallpaperContract.Target.GlobalWallpaper -> {
                        wallpaperService.setGlobalWallpaper(wallpaper)
                    }
                }
            } catch (e: IOException) {
                showToast(R.string.an_error_occurred)
                logger.error("Failed to set wallpaper", e)
                finish()
                return@launch
            }
            finishWithSuccess()
        }
    }

    private fun launchFilePicker() {
        FileUtil.selectFile(this, imageSelectLauncher, arrayOf(MimeUtil.MIME_TYPE_IMAGE), false, 0, null)
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK)
        finish()
    }

    private fun getBottomSheetItems(): List<BottomSheetItem> = buildList {
        if (defaultWallpaperSupported()) {
            when (target) {
                is SelectWallpaperContract.Target.ConversationWallpaper -> {
                    add(
                        BottomSheetItem(
                            R.drawable.ic_check,
                            getString(R.string.wallpaper_default),
                            SELECTOR_TAG_WALLPAPER_DEFAULT,
                        ),
                    )
                }
                SelectWallpaperContract.Target.GlobalWallpaper -> {
                    add(
                        BottomSheetItem(
                            R.drawable.ic_notification_small,
                            getString(R.string.wallpaper_threema, getString(R.string.app_name)),
                            SELECTOR_TAG_WALLPAPER_DEFAULT,
                        ),
                    )
                }
            }
        }
        add(
            BottomSheetItem(
                R.drawable.ic_image_outline,
                getString(R.string.wallpaper_gallery),
                SELECTOR_TAG_WALLPAPER_CUSTOM_IMAGE,
            ),
        )
        add(
            BottomSheetItem(
                R.drawable.ic_delete_outline,
                getString(R.string.wallpaper_none),
                SELECTOR_TAG_WALLPAPER_EMPTY,
            ),
        )
    }

    @Suppress("SimplifyBooleanWithConstants")
    private fun defaultWallpaperSupported() =
        wallpaperService.isDefaultWallpaperSupported() || target is SelectWallpaperContract.Target.ConversationWallpaper

    private fun getSelectedBottomSheetItemIndex(): Int {
        val currentWallpaper = when (val target = target) {
            is SelectWallpaperContract.Target.ConversationWallpaper -> {
                wallpaperService.getConversationWallpaper(target.conversationId)
            }
            SelectWallpaperContract.Target.GlobalWallpaper -> {
                wallpaperService.getGlobalWallpaper()
            }
        }
        return when (currentWallpaper) {
            WallpaperService.Wallpaper.Default -> 0
            is WallpaperService.Wallpaper.Custom -> if (defaultWallpaperSupported()) 1 else 0
            WallpaperService.Wallpaper.Empty -> if (defaultWallpaperSupported()) 2 else 1
        }
    }

    private fun onImageSelected(imageUri: Uri) {
        lifecycleScope.launch {
            val inputStream = contentResolver.openInputStream(imageUri)
                ?: run {
                    finish()
                    return@launch
                }
            withProgressDialog {
                inputStream.use { inputStream ->
                    tempImageFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            val cropImageParameters = getCropImageParameters()
            cropLauncher.launch(CropImageActivity.createIntent(this@SelectWallpaperActivity, cropImageParameters))
        }
    }

    private fun onImageCropped() {
        lifecycleScope.launch {
            val wallpaper = WallpaperService.Wallpaper.Custom(tempImageFile.toUri())
            try {
                withProgressDialog {
                    when (val target = target) {
                        is SelectWallpaperContract.Target.ConversationWallpaper -> {
                            wallpaperService.setConversationWallpaper(target.conversationId, wallpaper)
                        }

                        SelectWallpaperContract.Target.GlobalWallpaper -> {
                            wallpaperService.setGlobalWallpaper(wallpaper)
                        }
                    }
                    tempImageFile.delete()
                }
            } catch (e: IOException) {
                showToast(R.string.an_error_occurred)
                logger.error("Failed to store cropped wallpaper", e)
                finish()
                return@launch
            }

            finishWithSuccess()
        }
    }

    private fun getCropImageParameters(): CropImageParameters {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val rectangle = Rect()
        window.decorView.getWindowVisibleDisplayFrame(rectangle)

        var width = metrics.widthPixels
        var height = metrics.heightPixels

        if (height > width) {
            height -= rectangle.top
        } else {
            width -= rectangle.top
        }

        val y = max(width, height)
        val x = min(width, height)

        val imageUri = FileProviderUtil.getUriForFile(this, tempImageFile)
        return CropImageParameters(
            sourceUri = imageUri,
            saveUri = imageUri,
            aspectX = x,
            aspectY = y,
            maxWidth = x,
            maxHeight = y,
        )
    }

    private suspend fun withProgressDialog(block: suspend () -> Unit) {
        showProgressDialog()
        try {
            block()
        } finally {
            hideProgressDialog()
        }
    }

    private fun showProgressDialog() {
        GenericProgressDialog.newInstance("", getString(R.string.please_wait))
            .show(supportFragmentManager, DIALOG_TAG_LOADING_IMAGE)
    }

    private fun hideProgressDialog() {
        supportFragmentManager.dismissDialog(DIALOG_TAG_LOADING_IMAGE)
    }

    companion object {
        private const val DIALOG_TAG_LOADING_IMAGE = "lit"
        private const val SELECTOR_TAG_WALLPAPER_DEFAULT = "def"
        private const val SELECTOR_TAG_WALLPAPER_CUSTOM_IMAGE = "gal"
        private const val SELECTOR_TAG_WALLPAPER_EMPTY = "none"
        private const val DIALOG_TAG_SELECT_WALLPAPER = "selwal"

        private const val EXTRA_CONVERSATION_ID = "conversation_id"

        fun createIntentForGlobalWallpaper(context: Context) = buildActivityIntent<SelectWallpaperActivity>(context)

        fun createIntentForConversationWallpaper(
            context: Context,
            conversationId: ConversationId,
        ) = buildActivityIntent<SelectWallpaperActivity>(context) {
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
    }
}
