// AGP 9 bundles Kotlin support (built-in Kotlin). The classpath pins below raise the
// Kotlin Gradle plugin and KSP versions that AGP would otherwise select, as documented
// in the AGP 9.0 release notes.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
