plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
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
    compileSdk = 35

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

    buildTypes {
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
            java.srcDir(zeronCoreDir.resolve("kotlin"))
            jniLibs.srcDirs(zeronCoreDir.resolve("jniLibs"))
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
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
