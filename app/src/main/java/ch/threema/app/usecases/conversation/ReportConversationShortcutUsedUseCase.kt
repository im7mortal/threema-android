package ch.threema.app.usecases.conversation

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ReportConversationShortcutUsedUseCase")

class ReportConversationShortcutUsedUseCase(
    private val appContext: Context,
    private val preferenceService: PreferenceService,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun call(conversationId: ConversationId) = withContext(dispatcherProvider.worker) {
        if (!preferenceService.isDirectShare()) {
            // If direct-share is disabled, no shortcut needs to be reported as used
            return@withContext
        }
        val shortcutId: String = conversationId.obfuscated.value
        try {
            ShortcutManagerCompat.reportShortcutUsed(appContext, shortcutId)
        } catch (e: IllegalStateException) {
            logger.warn("Failed to report shortcut as used", e)
        }
    }
}
