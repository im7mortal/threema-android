package ch.threema.app.dev.preference

import androidx.annotation.UiThread
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import ch.threema.android.showToast
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.dev.androidcontactsync.AndroidContactDebugActivity
import ch.threema.app.dev.patternlibrary.PatternLibraryActivity
import ch.threema.app.errorreporting.RecentErrorTypeIdStore
import ch.threema.app.preference.ThreemaPreferenceFragment
import ch.threema.app.preference.onClick
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.problemsolving.Problem
import ch.threema.app.usecases.OverrideOneTimeHintsUseCase
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.logging.logAndReportError
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsDeveloperFragment")

@Suppress("unused")
class SettingsDeveloperFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val contentCreator: ContentCreator by inject()
    private val overrideOneTimeHintsUseCase: OverrideOneTimeHintsUseCase by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val recentErrorTypeIdStore: RecentErrorTypeIdStore by inject()

    override fun initializePreferences() {
        getPref<Preference>(R.string.preferences__dev_reset_one_time_hints).onClick {
            resetOneTimeHintsShown()
        }

        // Reset dismissed problems
        getPref<Preference>(R.string.preferences__dev_reset_dismissed_problems).onClick {
            resetDismissedProblems()
        }

        // Generate text messages
        getPref<Preference>(R.string.preferences__dev_create_text_messages).onClick {
            generateTextMessages()
        }

        // Generate messages with reactions
        getPref<Preference>(R.string.preferences__dev_create_messages_with_reactions).onClick {
            generateReactionMessages()
        }

        // Generate nonces
        getPref<Preference>(R.string.preferences__dev_create_nonces).onClick {
            generateNonces()
        }

        // Theming
        getPref<Preference>(R.string.preferences__open_pattern_library).onClick {
            startActivity(PatternLibraryActivity.createIntent(requireContext()))
        }

        // Android Contact Sync Debugging
        getPref<Preference>(R.string.preferences__open_android_contact_sync_debug_activity).onClick {
            startActivity(AndroidContactDebugActivity.createIntent(requireContext()))
        }

        // Crash the app on purpose
        getPref<Preference>(R.string.preferences__cause_crash).onClick {
            try {
                try {
                    throw IllegalArgumentException("This is the inner cause!")
                } catch (e: Exception) {
                    throw IllegalStateException("This is the outer cause", e)
                }
            } catch (e: Exception) {
                throw RuntimeException("Crash test!", e)
            }
        }

        // Create an error record without crashing the app
        getPref<Preference>(R.string.preferences__record_unexpected_error).apply {
            isEnabled = BuildConfig.ERROR_REPORTING_SUPPORTED &&
                preferenceService.getErrorReportingState() != PreferenceService.ErrorReportingState.NEVER_SEND
            onClick {
                logger.logAndReportError("This is a test, foo = {}", Random.nextInt(from = 0, until = 10))
                showToast("Error record created")
            }
        }

        // Reset the recent error type store
        getPref<Preference>(R.string.preferences__clear_recent_error_type_ids).onClick {
            lifecycleScope.launch {
                withContext(dispatcherProvider.io) {
                    recentErrorTypeIdStore.clear()
                }
                showToast("Recent error type ids cleared")
            }
        }

        // Remove developer menu
        getPref<Preference>(R.string.preferences__remove_menu).onClick {
            hideDeveloperMenu()
        }
    }

    private fun resetOneTimeHintsShown() {
        logger.info("Reset one-time hints")
        overrideOneTimeHintsUseCase.call(dismiss = false)
        showToast("One-time hints reset")
    }

    private fun resetDismissedProblems() {
        Problem.entries.forEach { problem ->
            if (problem.dismissKey != null) {
                preferenceService.setProblemDismissed(problem.dismissKey, null)
            }
        }
        showToast("Problems reset")
    }

    private fun generateTextMessages() {
        lifecycleScope.launch {
            contentCreator.createTextMessageSpam(parentFragmentManager)
        }
    }

    private fun generateReactionMessages() {
        lifecycleScope.launch {
            contentCreator.createReactionSpam(parentFragmentManager)
        }
    }

    private fun generateNonces() {
        lifecycleScope.launch {
            contentCreator.createNonces(parentFragmentManager)
        }
    }

    @UiThread
    private fun hideDeveloperMenu() {
        preferenceService.setShowDeveloperMenu(false)
        showToast("Developer settings hidden")
        activity?.finish()
    }

    override fun getPreferenceTitleResource() = R.string.prefs_developers

    override fun getPreferenceResource() = R.xml.preference_developers
}
