package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

object BuildFlavorConstants {
    const val DISPLAY_NAME = "OnPrem"
    const val IS_SANDBOX = false
    val licenseType = BuildFlavor.LicenseType.WORK
    val desktopClientFlavor = DesktopClientFlavor.OnPrem
}
