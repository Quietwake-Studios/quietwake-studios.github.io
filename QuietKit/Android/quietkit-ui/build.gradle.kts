// QuietUI for Android: theme by role, components, haptics, motion and fireworks.
// Built from each app's Gradle project (its version catalog), like quietkit-core.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tavoohoh.quiet.ui"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].java.srcDir("src/main/kotlin")
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":quietkit-core"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.core.ktx)
    implementation(libs.play.review.ktx)
    implementation(libs.play.games.v2)
}
