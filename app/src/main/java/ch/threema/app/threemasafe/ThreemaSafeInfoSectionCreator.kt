package ch.threema.app.threemasafe

class ThreemaSafeInfoSectionCreator(
    private val appVersion: String,
    private val locale: String,
) {
    fun create(): ThreemaSafeInfoSection = ThreemaSafeInfoSection.create(
        version = PROTOCOL_VERSION,
        device = appVersion + "A/" + locale,
    )

    companion object {
        private const val PROTOCOL_VERSION = 1
    }
}
