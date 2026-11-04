package ch.threema.app.license

import ch.threema.domain.models.LicenseCredentials
import ch.threema.domain.models.SerialCredentials
import ch.threema.domain.models.UserCredentials

interface Parameters {

    /**
     * If true it is safe to check the parameters. For onprem builds, it may not be ok to send the credentials to an url provided by a phishing link.
     */
    val canBeChecked: Boolean
        get() = true

    val credentials: LicenseCredentials

    data class PrivateParameters(
        val serialParameter: SerialParameter,
    ) : Parameters {
        override val credentials = SerialCredentials(
            licenseKey = serialParameter.value,
        )
    }

    data class WorkParameters(
        val username: UsernameParameter,
        val password: PasswordParameter,
    ) : Parameters {
        override val credentials = UserCredentials(
            username = username.value,
            password = password.value,
        )
    }

    data class OnPremParameters(
        val username: UsernameParameter,
        val password: PasswordParameter,
        val serverUrl: ServerUrlParameter,
    ) : Parameters {
        override val canBeChecked: Boolean = when (serverUrl.source) {
            // It is safe to check the credentials if the server url originates from the user, mdm, or preconfiguration
            ParameterSource.MDM, ParameterSource.USER, ParameterSource.PRECONFIGURED -> true
            // It is safe to check if both username and password are not provided via mdm
            ParameterSource.ACTIVATION_LINK -> username.source != ParameterSource.MDM && password.source != ParameterSource.MDM
        }

        override val credentials = UserCredentials(
            username = username.value,
            password = password.value,
        )
    }
}
