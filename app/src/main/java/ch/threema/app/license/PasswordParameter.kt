package ch.threema.app.license

class PasswordParameter private constructor(
    override val value: String,
    override val source: ParameterSource,
) : Parameter {
    companion object {
        @JvmStatic
        fun create(password: String, parameterSource: ParameterSource): PasswordParameter? {
            if (password.isEmpty()) {
                return null
            }

            return PasswordParameter(
                value = password,
                source = parameterSource,
            )
        }
    }
}
