package config

import com.android.build.api.dsl.AndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer

val buildFlavorsWithoutRelease = setOf(
    "green",
    "sandbox_work",
)

private enum class ServicesType(val path: String) {
    GOOGLE_SERVICES("google_services_based"),
    HMS_SERVICES("hms_services_based"),
    FOSS_BASED_SERVICES("foss_based"),
}

private enum class ThreemaAppType(val paths: Array<String>) {
    PRIVATE(arrayOf("only_private", "private_and_work")),
    WORK(arrayOf("only_work", "private_and_work", "work_and_onprem")),
    ON_PREM(arrayOf("only_onprem", "work_and_onprem")),
}

interface Scope {
    fun withGoogleServices()

    fun withHmsServices()

    fun withFossBasedServices()

    fun threemaPrivate()

    fun threemaWork()

    fun threemaOnPrem()

    fun withSelfUpdating()

    fun withDevSupport()

    fun extra(action: AndroidSourceSet.() -> Unit)
}

private fun AndroidSourceSet.addSourceSet(path: String) {
    java.srcDir("src/$path/java")
    assets.srcDir("src/$path/assets")
    res.srcDir("src/$path/res")
}

fun NamedDomainObjectContainer<out AndroidSourceSet>.configureFlavor(name: String, block: Scope.() -> Unit) {
    var selectedServiceType: ServicesType? = null
    var selectedThreemaAppType: ThreemaAppType? = null
    var selectedDevSupport = false
    var selectedSelfUpdating = false

    val scope = object : Scope {
        override fun withGoogleServices() {
            selectedServiceType = ServicesType.GOOGLE_SERVICES
        }

        override fun withHmsServices() {
            selectedServiceType = ServicesType.HMS_SERVICES
        }

        override fun withFossBasedServices() {
            selectedServiceType = ServicesType.FOSS_BASED_SERVICES
        }

        override fun threemaPrivate() {
            selectedThreemaAppType = ThreemaAppType.PRIVATE
        }

        override fun threemaWork() {
            selectedThreemaAppType = ThreemaAppType.WORK
        }

        override fun threemaOnPrem() {
            selectedThreemaAppType = ThreemaAppType.ON_PREM
        }

        override fun withSelfUpdating() {
            selectedSelfUpdating = true
        }

        override fun withDevSupport() {
            selectedDevSupport = true
        }

        override fun extra(action: AndroidSourceSet.() -> Unit) {
            getByName(name) {
                action()
            }
        }
    }
    block(scope)

    getByName(name) {
        val serviceTypePath = selectedServiceType?.path ?: error("No service type selected for $name")
        addSourceSet("service_type_specific/$serviceTypePath")

        val threemaAppTypePaths = selectedThreemaAppType?.paths ?: error("Unspecified Threema app type for $name")
        threemaAppTypePaths.forEach { path ->
            addSourceSet("app_type_specific/$path")
        }

        addSourceSet("update_capability/${if (selectedSelfUpdating) "with_self_updating" else "without_self_updating"}")
    }

    create(name + "Debug") {
        addSourceSet("dev_support_specific/with_dev_support")
        manifest.srcFile("src/dev_support_specific/with_dev_support/AndroidManifest.xml")
    }
    if (name !in buildFlavorsWithoutRelease) {
        create(name + "Release") {
            addSourceSet("dev_support_specific/${if (selectedDevSupport) "with_dev_support" else "without_dev_support"}")
            if (selectedDevSupport) {
                manifest.srcFile("src/dev_support_specific/with_dev_support/AndroidManifest.xml")
            }
        }
    }
}
