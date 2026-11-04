plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

// Changes to this function must be synced across all duplicates of it (settings.gradle.kts, buildSrc/settings.gradle.kts, buildSrc/build.gradle.kts)
fun RepositoryHandler.dependencyProxy() {
    data class ProxyConfig(
        val apiV4Url: String?,
        val headerName: String,
        val token: String?,
    )

    val ciApiV4Url = providers.environmentVariable("CI_API_V4_URL").orNull
    val ciJobToken = providers.environmentVariable("CI_JOB_TOKEN").orNull
    val localApiV4Url = providers.gradleProperty("apiV4Url").orNull
    val localJobToken = providers.gradleProperty("dependencyProxyPrivateToken").orNull

    val config = if (ciApiV4Url != null && ciJobToken != null) {
        println("Using dependency proxy with CI config")
        ProxyConfig(
            apiV4Url = ciApiV4Url,
            headerName = "Job-Token",
            token = ciJobToken,
        )
    } else if (!localApiV4Url.isNullOrBlank() && !localJobToken.isNullOrBlank()) {
        println("Using dependency proxy with local config")
        ProxyConfig(
            apiV4Url = providers.gradleProperty("apiV4Url").orNull,
            headerName = "Private-Token",
            token = providers.gradleProperty("dependencyProxyPrivateToken").orNull,
        )
    } else {
        return
    }

    val repositoryUri = uri("${config.apiV4Url}/virtual_registries/packages/maven/1")

    maven {
        url = repositoryUri
        name = "GitLab"
        credentials(HttpHeaderCredentials::class) {
            name = config.headerName
            value = config.token
        }
        authentication {
            create<HttpHeaderAuthentication>("header")
        }
    }
}

repositories {
    dependencyProxy()
    google()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    implementation(libs.android.gradle)
}
