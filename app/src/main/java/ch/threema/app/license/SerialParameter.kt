package ch.threema.app.license

class SerialParameter private constructor(
    override val value: String,
) : Parameter {
    override val source: ParameterSource? = null

    companion object {
        @JvmStatic
        fun create(serialLicenseKey: String?): SerialParameter? {
            if (serialLicenseKey.isNullOrBlank()) {
                return null
            }

            return SerialParameter(
                value = serialLicenseKey,
            )
        }
    }
}
