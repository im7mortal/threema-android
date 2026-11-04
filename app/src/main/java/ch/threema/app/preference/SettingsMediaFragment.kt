package ch.threema.app.preference

import android.icu.text.ListFormatter
import android.os.Build
import android.text.format.Formatter
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.MessageServiceImpl
import ch.threema.app.storagemanagement.StorageManagementActivity
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LocaleProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsMediaFragment")

class SettingsMediaFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private var saveMediaPreference: Preference? = null

    private val preferenceService: PreferenceService by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val localeProvider: LocaleProvider by inject()

    override fun initializePreferences() {
        initAutoDownloadPref()
        initMediaPref()
        initSaveMediaPref()
        initDownloadPref()
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_media_title

    override fun getPreferenceResource(): Int = R.xml.preference_media

    private fun initAutoDownloadPref() {
        val autoDownloadExplainSizePreference: Preference =
            getPref(R.string.preferences__auto_download_explain)
        autoDownloadExplainSizePreference.summary = getString(
            R.string.auto_download_limit_explain,
            Formatter.formatShortFileSize(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_SI
                } else {
                    MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO
                },
            ),
        )
    }

    private fun initMediaPref() {
        getPref<Preference>(R.string.preferences__storage_management).onClick {
            startActivity(StorageManagementActivity.createIntent(requireActivity()))
        }
    }

    private fun initSaveMediaPref() {
        saveMediaPreference = getPref(R.string.preferences__save_media)
        saveMediaPreference?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                ConfigUtils.requestWriteStoragePermissions(
                    requireActivity(),
                    this@SettingsMediaFragment,
                    PERMISSION_REQUEST_SAVE_MEDIA,
                )
            } else {
                true
            }
        }

        if (appRestrictions.isSaveToGalleryDisabledOrNull() != null) {
            saveMediaPreference?.isEnabled = false
            saveMediaPreference?.isSelectable = false
        }
    }

    private fun initDownloadPref() {
        val autoDownloadOptionsMap = listOf(
            PreferenceService.AutoDownloadType.IMAGE to getString(R.string.media_gallery_pictures),
            PreferenceService.AutoDownloadType.VOICE_MESSAGE to getString(R.string.media_gallery_audio),
            PreferenceService.AutoDownloadType.VIDEO to getString(R.string.media_gallery_videos),
            PreferenceService.AutoDownloadType.FILE to getString(R.string.media_files),
        )

        val wifiDownloadPreference: MultiSelectListPreference = getPref(R.string.preferences__auto_download_wifi)
        wifiDownloadPreference.init(
            options = autoDownloadOptionsMap,
            selectedOptions = preferenceService.getWifiAutoDownload(),
            keyToString = PreferenceService.AutoDownloadType::serialized,
        )
        wifiDownloadPreference.onChange<Set<String>> { selectedSerializedOptions ->
            val selectedOptions = selectedSerializedOptions.mapNotNull(PreferenceService.AutoDownloadType::deserialize).toSet()
            preferenceService.setWifiAutoDownload(selectedOptions)
            wifiDownloadPreference.summary = getAutoDownloadSummary(selectedOptions, autoDownloadOptionsMap)
        }
        wifiDownloadPreference.summary = getAutoDownloadSummary(preferenceService.getWifiAutoDownload(), autoDownloadOptionsMap)

        val mobileDownloadPreference: MultiSelectListPreference = getPref(R.string.preferences__auto_download_mobile)
        mobileDownloadPreference.init(
            options = autoDownloadOptionsMap,
            selectedOptions = preferenceService.getMobileAutoDownload(),
            keyToString = PreferenceService.AutoDownloadType::serialized,
        )
        mobileDownloadPreference.onChange<Set<String>> { selectedSerializedOptions ->
            val selectedOptions = selectedSerializedOptions.mapNotNull(PreferenceService.AutoDownloadType::deserialize).toSet()
            preferenceService.setMobileAutoDownload(selectedOptions)
            mobileDownloadPreference.summary = getAutoDownloadSummary(selectedOptions, autoDownloadOptionsMap)
        }
        mobileDownloadPreference.summary = getAutoDownloadSummary(preferenceService.getMobileAutoDownload(), autoDownloadOptionsMap)
    }

    private fun getAutoDownloadSummary(
        selectedOptions: Set<PreferenceService.AutoDownloadType>,
        options: List<Pair<PreferenceService.AutoDownloadType, String>>,
    ): String {
        if (selectedOptions.isEmpty()) {
            return resources.getString(R.string.never)
        }
        val labels = options.filter { (key, _) -> key in selectedOptions }.map { (_, label) -> label }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ListFormatter.getInstance(localeProvider.getCurrentLocale()).format(labels)
        } else {
            labels.joinToString(separator = ", ")
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_SAVE_MEDIA = 1
    }
}
