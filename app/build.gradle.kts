plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

tasks.withType<Test>().configureEach {
    systemProperty("file.encoding", "UTF-8")
}

val releaseSigningValues = mapOf(
    "ANDROID_RELEASE_KEYSTORE_PATH" to System.getenv("ANDROID_RELEASE_KEYSTORE_PATH"),
    "ANDROID_RELEASE_STORE_PASSWORD" to System.getenv("ANDROID_RELEASE_STORE_PASSWORD"),
    "ANDROID_RELEASE_KEY_ALIAS" to System.getenv("ANDROID_RELEASE_KEY_ALIAS"),
    "ANDROID_RELEASE_KEY_PASSWORD" to System.getenv("ANDROID_RELEASE_KEY_PASSWORD"),
)
val configuredReleaseSigningValues = releaseSigningValues.filterValues { !it.isNullOrBlank() }
val hasReleaseSigning = configuredReleaseSigningValues.size == releaseSigningValues.size

if (configuredReleaseSigningValues.isNotEmpty() && !hasReleaseSigning) {
    val missingNames = releaseSigningValues
        .filterValues { it.isNullOrBlank() }
        .keys
        .sorted()
        .joinToString()
    throw GradleException("Incomplete Android release signing configuration. Missing: $missingNames")
}

android {
    namespace = "com.xuhuangbin.xinghuozhaidu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xuhuangbin.xinghuozhaidu"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField(
            "String",
            "CONTENT_MANIFEST_URL",
            "\"https://github.com/Hi-prof/maoxuan/releases/latest/download/manifest.json\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val keystorePath = checkNotNull(releaseSigningValues["ANDROID_RELEASE_KEYSTORE_PATH"])
                val keystoreFile = file(keystorePath)
                if (!keystoreFile.isFile) {
                    throw GradleException("Android release keystore does not exist: $keystorePath")
                }
                storeFile = keystoreFile
                storePassword = checkNotNull(releaseSigningValues["ANDROID_RELEASE_STORE_PASSWORD"])
                keyAlias = checkNotNull(releaseSigningValues["ANDROID_RELEASE_KEY_ALIAS"])
                keyPassword = checkNotNull(releaseSigningValues["ANDROID_RELEASE_KEY_PASSWORD"])
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("personal") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

val releasePackagingTaskName = Regex("^(assemble|bundle|package|install).*Release$")

gradle.taskGraph.whenReady {
    val releasePackagingRequested = allTasks.any { task ->
        task.project == project && releasePackagingTaskName.matches(task.name)
    }
    if (releasePackagingRequested && !hasReleaseSigning) {
        throw GradleException(
            "Android release packaging requires ANDROID_RELEASE_KEYSTORE_PATH, " +
                "ANDROID_RELEASE_STORE_PASSWORD, ANDROID_RELEASE_KEY_ALIAS, and " +
                "ANDROID_RELEASE_KEY_PASSWORD.",
        )
    }
}

tasks.register("printVersionName") {
    group = "help"
    description = "Prints the configured Android application version name."
    doLast {
        println(android.defaultConfig.versionName)
    }
}

tasks.register("printVersionCode") {
    group = "help"
    description = "Prints the configured Android application version code."
    doLast {
        println(android.defaultConfig.versionCode)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
