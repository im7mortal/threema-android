package ch.threema.app.preference

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ch.threema.app.R
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConnectionIndicatorUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.ServerConnection
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlin.getValue
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ThreemaPreferenceFragment")

/**
 * This fragment provides some toolbar functionality and manages loading the resources.
 */
abstract class ThreemaPreferenceFragment : PreferenceFragmentCompat() {
    private var colorTransparent = 0
    private var initialized = false

    private var settingsScrollView: NestedScrollView? = null
    private var appBar: AppBarLayout? = null
    var toolbar: MaterialToolbar? = null
    private var toolbarTitle: TextView? = null
    var title: TextView? = null
    private var connectionIndicator: View? = null

    private val serverConnection: ServerConnection by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        initialized = true

        setPreferencesFromResource(getPreferenceResource(), rootKey)

        initializePreferences()
    }

    override fun onResume() {
        super.onResume()

        activity.apply {
            if (this is SettingsActivity) {
                setActionBarTitle(if (ConfigUtils.isTabletLayout()) R.string.menu_settings else getPreferenceTitleResource())
            }
        }
    }

    /**
     * This method must be overridden to provide the action bar title of the preference category.
     */
    @StringRes
    protected abstract fun getPreferenceTitleResource(): Int

    /**
     * This method must be overridden to provide the xml definition of the preferences.
     */
    @XmlRes
    abstract fun getPreferenceResource(): Int

    /**
     * This method is called in [onCreatePreferences] and can be used by subclasses to initialize the preferences.
     */
    protected open fun initializePreferences() {
        // No need to do something here. Just a placeholder method that can be overridden by subclasses.
    }

    /**
     * Get the preference with the given key. Returns null if there is no such preference.
     */
    protected fun <T : Preference> getPrefOrNull(@StringRes stringRes: Int): T? =
        getPrefOrNull(getString(stringRes))

    /**
     * Get the preference with the given key. Returns null if there is no such preference.
     */
    protected fun <T : Preference> getPrefOrNull(key: String): T? {
        return try {
            getPref(key)
        } catch (e: Exception) {
            logger.warn("Preference '$key' not found")
            null
        }
    }

    /**
     * Get the preference with the given key. Throws an [IllegalArgumentException] if there is no such preference.
     */
    protected fun <T : Preference> getPref(@StringRes stringRes: Int) =
        getPref<T>(getString(stringRes))

    /**
     * Get the preference with the given key. Throws an [IllegalArgumentException] if there is no such preference.
     */
    protected fun <T : Preference> getPref(string: String): T =
        findPreference(string) ?: preferenceNotFound(string)

    private fun preferenceNotFound(pref: String): Nothing {
        throw IllegalArgumentException("No preference '$pref' found")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsScrollView = view.findViewById(R.id.settings_contents_view)
        toolbar = view.findViewById(R.id.toolbar)

        toolbar?.applyDeviceInsetsAsMargin(
            insetSides = InsetSides.ltr(),
        )

        toolbarTitle = view.findViewById(R.id.toolbar_title)
        title = view.findViewById(R.id.title_text_view)
        connectionIndicator = view.findViewById(R.id.connection_indicator)
        appBar = view.findViewById(R.id.appbar)
        appBar?.setLiftable(true)

        setTitle(getPreferenceTitleResource())

        colorTransparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)

        (activity as AppCompatActivity?)!!.setSupportActionBar(toolbar)
        val ab: ActionBar? = (activity as AppCompatActivity?)!!.supportActionBar

        if (ab != null) {
            if (!ConfigUtils.isTabletLayout() || this is SettingsSummaryFragment) {
                ab.setDisplayHomeAsUpEnabled(true)
                toolbar?.setNavigationOnClickListener {
                    if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                        requireActivity().supportFragmentManager.popBackStack()
                    } else {
                        requireActivity().finish()
                    }
                }
            } else {
                toolbar?.visibility = View.INVISIBLE
            }
        }

        settingsScrollView?.let { nestedScrollView ->

            nestedScrollView.applyDeviceInsetsAsPadding(
                insetSides = InsetSides.lbr(),
            )

            if (initialized) {
                nestedScrollView.post {
                    nestedScrollView.scrollTo(0, 0)
                }
            }
            initialized = false
            toolbarTitle?.alpha = 0f
            if (!ConfigUtils.isTabletLayout()) {
                nestedScrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                    setToolbarColor()
                }
            }
        }

        listView?.apply {
            clipToPadding = false
            updatePadding(
                bottom = resources.getDimensionPixelSize(R.dimen.grid_unit_x2),
            )
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                serverConnection.watchConnectionState().collect { connectionState ->
                    ConnectionIndicatorUtil.getInstance()
                        .updateConnectionIndicator(connectionIndicator, connectionState)
                }
            }
        }
    }

    private fun setToolbarColor() {
        val titleLocation = IntArray(2)
        title?.let {
            it.getLocationInWindow(titleLocation)
            val toolbarLocation = IntArray(2)
            toolbar?.let { materialToolbar ->
                materialToolbar.getLocationInWindow(toolbarLocation)
                val currentTitleTop = titleLocation[1] + it.paddingTop

                val titleFadeOutStart =
                    toolbarLocation[1] + materialToolbar.height + (it.paddingTop / 2)
                val titleFadeOutEnd =
                    toolbarLocation[1] + materialToolbar.height - it.height + it.paddingTop + it.paddingBottom + (it.paddingTop / 4)

                val toolbarFadeInStart = titleFadeOutEnd
                val toolbarFadeInEnd = toolbarFadeInStart - (materialToolbar.height / 2)

                val titleAlpha =
                    1F - ((titleFadeOutStart - currentTitleTop).toFloat() / (titleFadeOutStart - titleFadeOutEnd).toFloat())
                val toolbarAlpha =
                    (toolbarFadeInStart - currentTitleTop).toFloat() / (toolbarFadeInStart - toolbarFadeInEnd).toFloat()
                it.alpha = titleAlpha
                toolbarTitle?.alpha = toolbarAlpha

                appBar?.isLifted = titleLocation[1] <= materialToolbar.height
            }
        }
    }

    open fun setTitle(title: CharSequence?) {
        this.title?.text = title
        toolbarTitle?.text = title
    }

    open fun setTitle(stringRes: Int) {
        title?.setText(stringRes)
        toolbarTitle?.setText(stringRes)
    }

    /**
     * Hack to style MultiSelectPreferences as Material Dialogs
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is MultiSelectListPreference) {
            val dialogFragment: DialogFragment = MaterialMultiSelectListPreference()
            val bundle = Bundle(1)
            bundle.putString("key", preference.getKey())
            dialogFragment.arguments = bundle
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                parentFragmentManager,
                "androidx.preference.PreferenceFragment.DIALOG",
            )
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onDestroyView() {
        appBar = null
        settingsScrollView = null

        super.onDestroyView()
    }
}
