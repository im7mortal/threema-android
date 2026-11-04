plugins {
    alias(libs.plugins.java.library)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Standard libraries
    api(libs.kotlinx.coroutines.core)

    // Dependency injection
    api(project.dependencies.platform(libs.koin.bom))
    api(libs.koin.core)

    // HTTP
    api(platform(libs.okhttp3.bom))
    api(libs.okhttp3)
    api(libs.okhttp3.coroutines)
    api(libs.okhttp3.loggingInterceptor)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // Annotations
    compileOnly(libs.compose.annotation)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.turbine)
    testImplementation(project(":test-helpers"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
