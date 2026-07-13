package config

import com.android.build.api.dsl.VariantDimension
import utils.booleanBuildConfigField
import utils.intBuildConfigField
import utils.stringBuildConfigField

enum class SentryConfig(
    val projectId: Int,
    val publicApiKey: String,
    val host: String = "bugs.threema.ch",
) {
    SANDBOX(
        projectId = 33,
        publicApiKey = "b3e20afbf356a8748bb62ac165aa780c",
    ),
    PRODUCTION(
        projectId = 34,
        publicApiKey = "615af77cb3d980c41b3b04b07417cc7d",
    ),
}

fun VariantDimension.setSentryConfig(config: SentryConfig?) {
    setSentryConfig(
        projectId = config?.projectId ?: 0,
        publicApikey = config?.publicApiKey ?: "",
        host = config?.host ?: "",
    )
}

private fun VariantDimension.setSentryConfig(
    projectId: Int,
    publicApikey: String,
    host: String,
) {
    intBuildConfigField("SENTRY_PROJECT_ID", projectId)
    stringBuildConfigField("SENTRY_PUBLIC_API_KEY", publicApikey)
    stringBuildConfigField("SENTRY_HOST", host)
    booleanBuildConfigField("ERROR_REPORTING_SUPPORTED", projectId != 0)
}
