package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

object BuildFlavorConstants {
    const val DISPLAY_NAME = "Sandbox Work"
    const val IS_SANDBOX = true
    val licenseType = BuildFlavor.LicenseType.WORK
    val desktopClientFlavor = DesktopClientFlavor.Blue
}
