import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

fun requestedAssembleRelease(): Boolean =
    gradle.startParameter.taskNames.any { task ->
        task.equals("assembleRelease", ignoreCase = true) ||
            task.equals(":app:assembleRelease", ignoreCase = true) ||
            task.endsWith(":assembleRelease", ignoreCase = true)
    }

fun requiredReleaseEnv(name: String): String {
    val value = System.getenv(name)
    if (value.isNullOrBlank()) {
        throw GradleException(
            "Release signing requires $name. Set ANDROID_RELEASE_KEYSTORE_BASE64, " +
                "ANDROID_RELEASE_STORE_PASSWORD, ANDROID_RELEASE_KEY_ALIAS, and " +
                "ANDROID_RELEASE_KEY_PASSWORD (see apps/android/README.md). " +
                "Debug builds do not need release signing secrets.",
        )
    }
    return value
}

fun resolveReleaseKeystoreFile(): File {
    val base64 = requiredReleaseEnv("ANDROID_RELEASE_KEYSTORE_BASE64")
    val decoded =
        try {
            Base64.getDecoder().decode(base64.replace("\\s".toRegex(), ""))
        } catch (_: IllegalArgumentException) {
            throw GradleException("ANDROID_RELEASE_KEYSTORE_BASE64 is not valid Base64.")
        }
    if (decoded.isEmpty()) {
        throw GradleException("ANDROID_RELEASE_KEYSTORE_BASE64 decoded to an empty keystore.")
    }
    val parent =
        System.getenv("RUNNER_TEMP")?.let { File(it) }
            ?: File(System.getProperty("java.io.tmpdir"))
    if (!parent.isDirectory) {
        throw GradleException(
            "Cannot write release keystore: temporary directory missing (${parent.absolutePath}).",
        )
    }
    val keystore = File.createTempFile("zerona-android-release-", ".keystore", parent)
    keystore.writeBytes(decoded)
    keystore.deleteOnExit()
    return keystore
}

val zeronCoreDirPath =
    (findProperty("zeronCoreDir") as String?) ?: "${rootProject.projectDir}/../../target/android-core"
val zeronCoreDir = file(zeronCoreDirPath)
if (!zeronCoreDir.isDirectory) {
    throw GradleException(
        "Rust core output not found at ${zeronCoreDir.absolutePath}. " +
            "Run scripts/android/build-core.sh or pass -PzeronCoreDir=...",
    )
}

val zeronAbis =
    (findProperty("zeronAbis") as String?)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: listOf("arm64-v8a")

val copyGeistFonts =
    tasks.register<Copy>("copyGeistFonts") {
        from("${rootProject.projectDir}/../../crates/ui/assets/fonts") {
            include("Geist*.ttf")
        }
        into("${projectDir}/src/main/assets/fonts")
    }

tasks.named("preBuild") {
    dependsOn(copyGeistFonts)
}

android {
    namespace = "sh.zeron.zerona"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "sh.zeron.zerona"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m1"

        ndk {
            abiFilters += zeronAbis
        }
    }

    signingConfigs {
        create("release") {
            if (requestedAssembleRelease()) {
                storeFile = resolveReleaseKeystoreFile()
                storePassword = requiredReleaseEnv("ANDROID_RELEASE_STORE_PASSWORD")
                keyAlias = requiredReleaseEnv("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = requiredReleaseEnv("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            if (requestedAssembleRelease()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.directories += zeronCoreDir.resolve("kotlin").absolutePath
            jniLibs.srcDirs(zeronCoreDir.resolve("jniLibs"))
        }
    }
}

dependencies {
    // Compose 1.12.0 aligns with material3 1.5.0-beta02 POM; BOM does not yet ship that Material3 line.
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("net.java.dev.jna:jna:5.15.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation("junit:junit:4.13.2")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn("testDebugUnitTest")
}
