package ch.threema.app.preference

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LocaleUtil.mapLocaleToPredefinedLocales
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.wallpapers.SelectWallpaperContract
import ch.threema.app.wallpapers.WallpaperService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactNameFormat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import java.util.Locale
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsAppearanceFragment")

@Suppress("unused")
class SettingsAppearanceFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val avatarCacheService: AvatarCacheService by inject()
    private val appRestrictions: AppRestrictions by inject()

    private val wallpaperService: WallpaperService by inject()

    private var showBadge: CheckBoxPreference? = null
    private var showBadgeChecked = false

    private val selectWallpaperLauncher = registerForActivityResult(SelectWallpaperContract()) {
        // nothing to do here
    }

    override fun initializePreferences() {
        this.showBadge = getPrefOrNull(R.string.preferences__show_unread_badge)
        this.showBadgeChecked = this.showBadge?.isChecked ?: false

        initDynamicColorPref()

        initDefaultColoredAvatarPref()

        initShowProfilePicPref()

        initBiggerSingleEmojisPref()

        initThemePref()

        initEmojiStylePref()

        initLanguageArrayPref()

        initWallpaperPref()

        initSortingPref()

        initFormatPref()

        initShowInactiveContactsPref()
    }

    override fun onDetach() {
        super.onDetach()
        if (this.showBadge?.isChecked != this.showBadgeChecked) {
            ConfigUtils.recreateActivity(activity)
        }
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_header_appearance

    override fun getPreferenceResource(): Int = R.xml.preference_appearance

    private fun initDynamicColorPref() {
        if (DynamicColors.isDynamicColorAvailable()) {
            getPrefOrNull<CheckBoxPreference>(R.string.preferences__dynamic_color)?.apply {
                onChange<Boolean> { enabled ->
                    val dynamicColorsOptions = DynamicColorsOptions.Builder()
                        .setPrecondition { _, _ -> enabled }
                        .build()

                    DynamicColors.applyToActivitiesIfAvailable(
                        requireActivity().application,
                        dynamicColorsOptions,
                    )

                    // we need to set the new preference synchronously here because we exit the app before returning the result of this listener
                    sharedPreferences?.edit(commit = true) {
                        putBoolean(getString(R.string.preferences__dynamic_color), enabled)
                    }

                    ConfigUtils.recreateActivity(requireActivity())
                    Runtime.getRuntime().exit(0)
                }
            }
        } else {
            val preferenceCategory = getPref<PreferenceCategory>("pref_key_appearance_cat")
            preferenceCategory.removePreference(getPref(R.string.preferences__dynamic_color))
        }
    }

    private fun initDefaultColoredAvatarPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__default_contact_picture_colored)?.onChange<Boolean> { isColored ->
            avatarCacheService.clear()
            ListenerManager.contactSettingsListeners.handle { listener ->
                listener.onIsDefaultContactPictureColoredChanged(isColored)
            }
        }
    }

    private fun initShowProfilePicPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__receive_profilepics)?.onChange<Boolean> { shouldShow ->
            avatarCacheService.clear()
            ListenerManager.contactSettingsListeners.handle { listener ->
                listener.onShowContactDefinedAvatarsChanged(shouldShow)
            }
        }
    }

    private fun initBiggerSingleEmojisPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__bigger_single_emojis)?.onChange<Boolean> { enabled ->
            preferenceService.setBiggerSingleEmojis(enabled)
        }
    }

    private fun initThemePref() {
        val themePreference = getPref<DropDownPreference>(R.string.preferences__theme)
        val options = listOf(
            PreferenceService.Theme.LIGHT to getString(R.string.list_theme_light),
            PreferenceService.Theme.DARK to getString(R.string.list_theme_dark),
            PreferenceService.Theme.FOLLOW_SYSTEM to getString(R.string.system_default),
        )
        themePreference.entries = options.map { it.second }.toTypedArray()
        themePreference.entryValues = options.map { it.first.serialized }.toTypedArray()
        var currentTheme = preferenceService.getTheme()
        themePreference.value = currentTheme.serialized
        themePreference.summary = (options.find { it.first == currentTheme } ?: options[0]).second
        themePreference.onChange<String> { newThemeString ->
            val newTheme = PreferenceService.Theme.deserialize(newThemeString)
                ?: PreferenceService.Theme.FOLLOW_SYSTEM
            if (newTheme != currentTheme) {
                currentTheme = newTheme
                preferenceService.setTheme(newTheme)
                AppCompatDelegate.setDefaultNightMode(newTheme.mode)
                themePreference.summary = (options.find { it.first == newTheme } ?: options[0]).second
                avatarCacheService.clear()
                val isDefaultContactPictureColored = preferenceService.isDefaultContactPictureColored()
                ListenerManager.contactSettingsListeners.handle { listener ->
                    listener.onIsDefaultContactPictureColoredChanged(isColored = isDefaultContactPictureColored)
                }
                activity?.recreate()
            }
        }
    }

    private fun initEmojiStylePref() {
        val emojiPreference = getPref<DropDownPreference>(R.string.preferences__emoji_style)
        val options = listOf(
            EmojiStyle.DEFAULT to getString(R.string.prefs_default_emojis),
            EmojiStyle.ANDROID to getString(R.string.prefs_android_emojis),
        )
        emojiPreference.entries = options.map { it.second }.toTypedArray()
        emojiPreference.entryValues = options.map { it.first.serialized }.toTypedArray()
        var currentStyle = preferenceService.getEmojiStyle()
        fun updatePreference(newStyle: EmojiStyle) {
            emojiPreference.summary = (options.first { it.first == newStyle }).second
            emojiPreference.setValueIndex(options.indexOfFirst { it.first == newStyle })
            if (newStyle != currentStyle) {
                preferenceService.setEmojiStyle(newStyle)
                ConfigUtils.recreateActivity(activity)
                currentStyle = newStyle
            }
        }
        updatePreference(currentStyle)
        emojiPreference.onChange<String> { newEmojiStyleString ->
            val newEmojiStyle = EmojiStyle.deserialize(newEmojiStyleString) ?: EmojiStyle.DEFAULT
            if (newEmojiStyle != currentStyle) {
                when (newEmojiStyle) {
                    EmojiStyle.DEFAULT -> {
                        updatePreference(newEmojiStyle)
                    }
                    EmojiStyle.ANDROID -> {
                        val dialog = GenericAlertDialog.newInstance(
                            R.string.prefs_android_emojis,
                            R.string.android_emojis_warning,
                            R.string.ok,
                            R.string.cancel,
                        )
                        dialog.setData(newEmojiStyle)
                        dialog.setCallback(
                            object : GenericAlertDialog.DialogClickListener {
                                override fun onYes(tag: String?, data: Any?) {
                                    updatePreference(EmojiStyle.ANDROID)
                                }

                                override fun onNo(tag: String?, data: Any?) {
                                    updatePreference(EmojiStyle.DEFAULT)
                                }
                            },
                        )
                        dialog.show(parentFragmentManager, "android_emojis")
                    }
                }
            }
        }
    }

    private fun initLanguageArrayPref() {
        // We always determine the current language based on the application locales. This is due to
        // the possibility that the user chooses an app language in the system settings.
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        val actualLocale = if (applicationLocales.isEmpty) {
            null
        } else {
            applicationLocales.get(0)
        }

        // Then we get a list of our supported languages. Then we map the current language selection
        // to the corresponding language array value we have defined for our supported languages.
        // Note that the user may choose a region of a language that we do not explicitly support.
        // Therefore we need #mapLocaleToPredefinedLocales.
        val languageArray = resources.getStringArray(R.array.list_app_languages)
        val languageArrayValues = resources.getStringArray(R.array.list_app_languages_values)
        val languagePreference = getPref<DropDownPreference>(R.string.preferences__app_language)
        val actualLocaleValue = mapLocaleToPredefinedLocales(actualLocale, languageArrayValues)
        languagePreference.value = actualLocaleValue

        logger.info("Mapping language '{}' to '{}'", actualLocale, actualLocaleValue)

        // We set the current language selection as preference. This is only needed to show the
        // correct language setting in the preferences.
        try {
            languagePreference.summary =
                languageArray[languagePreference.findIndexOfValue(actualLocaleValue)]
        } catch (e: Exception) {
            logger.error("Could not set language $actualLocale ($actualLocaleValue)", e)
        }
        languagePreference.onChange<String> { newLocale ->
            val newLocaleList = if (newLocale.isNotEmpty()) {
                LocaleListCompat.create(
                    // Note that for zh-CN, there needs to be a 'hans' in the language tag and for
                    // zh-TW, we need the 'hant' tag in the language tag to set the script properly.
                    Locale.forLanguageTag(newLocale),
                )
            } else {
                // We need to create an empty locale list. Otherwise the setting is not applied
                // correctly with app compat version 1.6.1 or lower.
                LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(newLocaleList)
        }
    }

    private fun initWallpaperPref() {
        getPref<Preference>(R.string.preferences__wallpaper).onClick {
            selectWallpaperLauncher.launch(SelectWallpaperContract.Target.GlobalWallpaper)
        }
    }

    private fun initSortingPref() {
        getPrefOrNull<DropDownPreference>(R.string.preferences__contact_sorting)?.onChange {
            ListenerManager.contactSettingsListeners.handle { listener -> listener.onSortingChanged() }
        }
    }

    private fun initFormatPref() {
        getPref<DropDownPreference>(R.string.preferences__contact_format).onChange { nameFormatValue: String ->
            val newNameFormat = ContactNameFormat.fromValue(
                value = nameFormatValue,
                context = requireContext(),
            )
            if (newNameFormat != null) {
                ListenerManager.contactSettingsListeners.handle { listener ->
                    listener.onNameFormatChanged(newNameFormat)
                }
            } else {
                logger.warn("Could not change name format")
            }
        }
    }

    private fun initShowInactiveContactsPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__show_inactive_contacts)?.apply {
            onChange {
                ListenerManager.contactSettingsListeners.handle { listener -> listener.onInactiveContactsSettingChanged() }
            }
            if (appRestrictions.isHideInactiveIdsOrNull() != null) {
                isEnabled = false
                isSelectable = false
            }
        }
    }
}
