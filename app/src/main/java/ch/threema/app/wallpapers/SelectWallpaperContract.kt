package ch.threema.app.wallpapers

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import ch.threema.data.datatypes.ConversationId

class SelectWallpaperContract : ActivityResultContract<SelectWallpaperContract.Target, Boolean>() {
    override fun createIntent(context: Context, input: Target) =
        when (input) {
            is Target.ConversationWallpaper -> {
                SelectWallpaperActivity.createIntentForConversationWallpaper(context, input.conversationId)
            }
            Target.GlobalWallpaper -> {
                SelectWallpaperActivity.createIntentForGlobalWallpaper(context)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK

    sealed interface Target {
        data object GlobalWallpaper : Target
        data class ConversationWallpaper(val conversationId: ConversationId) : Target
    }
}
