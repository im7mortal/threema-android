import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import utils.LocalProperties

LocalProperties.init(project.rootProject)

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle)
        classpath(libs.android.gradle)

        // Huawei agconnect plugin
        classpath("com.huawei.agconnect:agcp-1.9.1.303")
        classpath("com.huawei.agconnect:agconnect-crash-symbol-lib-1.9.1.301")
        classpath("com.huawei.agconnect:agconnect-apms-plugin-1.6.2.300")
        classpath("com.huawei.agconnect:agconnect-core-1.9.1.301@aar")
    }
}

plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "ch.threema"
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    ktlint {
        outputToConsole = true
        android = true

        filter {
            exclude { entry ->
                entry.file.path.contains("/build/")
            }
        }
    }
}
