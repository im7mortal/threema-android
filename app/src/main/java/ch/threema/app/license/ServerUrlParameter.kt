package ch.threema.app.license

class ServerUrlParameter private constructor(
    override val value: String,
    override val source: ParameterSource,
) : Parameter {
    fun getUrlToOppf(): String {
        var url = "$HTTPS_PREFIX${value.trim()}"

        if (!url.endsWith(".oppf")) {
            url += DEFAULT_OPPF_PATH
        }

        return url
    }

    companion object {
        @JvmStatic
        fun create(serverUrl: String, parameterSource: ParameterSource): ServerUrlParameter? {
            if (serverUrl.isBlank()) {
                return null
            }

            val trimmedServerUrl = serverUrl.trim()

            val withoutScheme = when {
                trimmedServerUrl.startsWith(HTTPS_PREFIX, ignoreCase = true) -> trimmedServerUrl.substring(HTTPS_PREFIX.length)
                trimmedServerUrl.startsWith(HTTP_PREFIX, ignoreCase = true) -> trimmedServerUrl.substring(HTTP_PREFIX.length)
                else -> trimmedServerUrl
            }

            val baseServerUrl = withoutScheme.removeSuffix(DEFAULT_OPPF_PATH)

            return ServerUrlParameter(
                value = baseServerUrl,
                source = parameterSource,
            )
        }

        private const val HTTP_PREFIX = "http://"
        private const val HTTPS_PREFIX = "https://"
        private const val DEFAULT_OPPF_PATH = "/prov/config.oppf"
    }
}
