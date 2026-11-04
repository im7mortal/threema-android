package ch.threema.app.license

enum class ParameterSource {
    /**
     * The parameter is preconfigured in the app (whitelabel).
     */
    PRECONFIGURED,

    /**
     * The parameter was set via MDM parameter.
     */
    MDM,

    /**
     * The parameter was set via activation link (or intent).
     */
    ACTIVATION_LINK,

    /**
     * The parameter was set (or updated) by the user.
     */
    USER,
}
