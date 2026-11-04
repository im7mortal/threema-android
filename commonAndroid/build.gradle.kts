plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ch.threema.android"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(21)
    }

    buildTypes {
        debug {}
        release {}
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.android)
    implementation(libs.androidx.documentfile)
    compileOnly(libs.compose.annotation)

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
