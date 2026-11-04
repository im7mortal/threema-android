package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

@Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
object BuildFlavor {

    @JvmStatic
    val licenseType: LicenseType =
        BuildFlavorConstants.licenseType

    @JvmStatic
    val buildEnvironment: BuildEnvironment = when {
        IS_ON_PREM -> BuildEnvironment.ON_PREM
        IS_SANDBOX -> BuildEnvironment.SANDBOX
        else -> BuildEnvironment.LIVE
    }

    @JvmStatic
    val fullDisplayName: String =
        BuildFlavorConstants.DISPLAY_NAME + if (BuildConfig.DEBUG) " (DEBUG)" else ""

    /**
     * Return whether the self-updater is supported or not.
     */
    const val MAY_SELF_UPDATE: Boolean =
        UpdateCapabilityConstants.MAY_SELF_UPDATE

    /**
     * Return whether this build flavor always uses Threema Push.
     */
    const val FORCE_THREEMA_PUSH: Boolean =
        ServiceTypeConstants.FORCE_THREEMA_PUSH

    /**
     * Return whether this build flavor uses the sandbox build environment.
     */
    const val IS_SANDBOX: Boolean =
        BuildFlavorConstants.IS_SANDBOX

    const val IS_WORK: Boolean =
        AppTypeConstants.IS_WORK

    const val IS_ON_PREM: Boolean =
        AppTypeConstants.IS_ON_PREM

    const val IS_LIBRE: Boolean =
        BuildConfig.FLAVOR == "libre"

    const val IS_HMS: Boolean =
        BuildConfig.FLAVOR == "hms" || BuildConfig.FLAVOR == "hms_work"

    val desktopClientFlavor: DesktopClientFlavor =
        BuildFlavorConstants.desktopClientFlavor

    enum class LicenseType {
        NONE,
        GOOGLE,
        SERIAL,
        HMS,
        WORK,
    }

    enum class BuildEnvironment {
        LIVE,
        SANDBOX,
        ON_PREM,
    }
}
