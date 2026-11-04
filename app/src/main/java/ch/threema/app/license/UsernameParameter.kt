package ch.threema.app.license

class UsernameParameter private constructor(
    override val value: String,
    override val source: ParameterSource,
) : Parameter {
    companion object {
        @JvmStatic
        fun create(username: String, parameterSource: ParameterSource): UsernameParameter? {
            if (username.isEmpty()) {
                return null
            }

            return UsernameParameter(
                value = username,
                source = parameterSource,
            )
        }
    }
}
