package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

object BuildFlavorConstants {
    const val DISPLAY_NAME = "Google Play"
    const val IS_SANDBOX = false
    val licenseType = BuildFlavor.LicenseType.GOOGLE
    val desktopClientFlavor = DesktopClientFlavor.Consumer
}
