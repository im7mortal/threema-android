package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

object BuildFlavorConstants {
    const val DISPLAY_NAME = "None"
    const val IS_SANDBOX = false
    val licenseType = BuildFlavor.LicenseType.NONE
    val desktopClientFlavor = DesktopClientFlavor.Consumer
}
