package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

object BuildFlavorConstants {
    const val DISPLAY_NAME = "Threema Shop"
    const val IS_SANDBOX = false
    val licenseType = BuildFlavor.LicenseType.SERIAL
    val desktopClientFlavor = DesktopClientFlavor.Consumer
}
