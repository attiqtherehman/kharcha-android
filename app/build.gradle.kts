plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing comes from the environment so that no key material lives in the repository:
//   KHARCHA_KEYSTORE           path to the .jks / .p12 keystore
//   KHARCHA_KEYSTORE_PASSWORD  keystore password
//   KHARCHA_KEY_ALIAS          key alias inside the keystore
//   KHARCHA_KEY_PASSWORD       key password (same as the store password for PKCS12 keystores)
// When KHARCHA_KEYSTORE is unset, assembleRelease still works and yields app-release-unsigned.apk.
val releaseKeystorePath: String? =
    providers.environmentVariable("KHARCHA_KEYSTORE").orNull?.takeIf { it.isNotBlank() }
val releaseSigningAvailable = releaseKeystorePath != null

if (releaseSigningAvailable) {
    val missing = listOf("KHARCHA_KEYSTORE_PASSWORD", "KHARCHA_KEY_ALIAS", "KHARCHA_KEY_PASSWORD")
        .filter { providers.environmentVariable(it).orNull.isNullOrBlank() }
    check(missing.isEmpty()) {
        "KHARCHA_KEYSTORE is set but ${missing.joinToString()} ${if (missing.size == 1) "is" else "are"} not. " +
            "Set all four KHARCHA_* variables, or unset KHARCHA_KEYSTORE to build an unsigned release."
    }
    check(file(releaseKeystorePath!!).isFile) {
        "KHARCHA_KEYSTORE points to '$releaseKeystorePath', which is not a file."
    }
} else if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    logger.lifecycle("Release signing is not configured (KHARCHA_KEYSTORE is unset): the release APK will be unsigned.")
}

android {
    namespace = "com.attiqtherehman.kharcha"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.attiqtherehman.kharcha"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.attiqtherehman.kharcha.HiltTestRunner"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = providers.environmentVariable("KHARCHA_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KHARCHA_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KHARCHA_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimise and obfuscate; the keep rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigningAvailable) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    testImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.biometric)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.fastexcel)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// Robolectric (SDK 36 image) reaches into JDK internals that Java 17+ seals by default.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    )
    maxHeapSize = "2g"
}
