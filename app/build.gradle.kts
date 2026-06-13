import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

    val appVersionName = "1.1.96"

android {
    namespace = "com.ella.music"
    compileSdk = 37
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?: listOf(file("release.jks"), rootProject.file("release.jks"))
            .firstOrNull { it.exists() }
        ?: file("release.jks")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "kidn0x1"
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "kidn0x1"
    val hasReleaseSigning = releaseStoreFile.exists() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()

    defaultConfig {
        applicationId = "com.ella.music"
        minSdk = 29
        targetSdk = 37
        versionCode = 26
        versionName = appVersionName

        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    splits {
        abi {
            val abiIncludes = providers.gradleProperty("ellaAbi")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a")

            isEnable = true
            reset()
            include(*abiIncludes.toTypedArray())
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!hasReleaseSigning) {
                throw GradleException(
                    "Release signing is not configured. Put release.jks in app/ or project root, " +
                    "or set RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD."
                )
            }
            signingConfig = signingConfigs.getByName("release")
        }

    create("fastRelease") {
        initWith(getByName("release"))
        isMinifyEnabled = false
        isShrinkResources = false
        matchingFallbacks += listOf("release")
        signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val variantNameCapitalized = variantName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }

        val apkDirProvider = variant.artifacts.get(SingleArtifact.APK)
        val outputDirProvider = layout.buildDirectory.dir("outputs/renamed-apk/$variantName")

        val renameTask = tasks.register("copy${variantNameCapitalized}RenamedApks") {
            inputs.dir(apkDirProvider)
            outputs.dir(outputDirProvider)

            doLast {
                val apkDir = apkDirProvider.get().asFile
                val outputDir = outputDirProvider.get().asFile
                outputDir.mkdirs()

                val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

                apkDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                    .forEach { apk ->
                        val lowerName = apk.name.lowercase(Locale.US)
                        val abi = knownAbis.firstOrNull { lowerName.contains(it.lowercase(Locale.US)) }
                            ?: "universal"

                        val target = outputDir.resolve(
                            "Halcyon-$appVersionName-$abi-$variantName.APK"
                        )

                        apk.copyTo(target, overwrite = true)
                        logger.lifecycle("Renamed APK copied to: ${target.absolutePath}")
                    }
            }
        }

        tasks.matching { it.name == "assemble$variantNameCapitalized" }
            .configureEach {
                finalizedBy(renameTask)
            }
        }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Installs the bundled baseline profile (src/main/baseline-prof.txt) so ART AOT-compiles
    // the startup/library paths at install time instead of JIT-compiling them on first launch.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Material 3 Expressive shapes (cookie / scallop / clover) for the daily-mix cover thumbnails.
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lyricon.provider)
    implementation(libs.lyric.getter.api)
    implementation("com.github.HChenX:SuperLyricApi:3.4")
    implementation(libs.backdrop)
    implementation(libs.reorderable)
    implementation(project(":lyrico-audiotag"))
    implementation("wang.harlon.quickjs:wrapper-android:2.4.0")
    implementation(project(":ffmpeg-decoder"))

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation("androidx.webkit:webkit:1.12.1")

    // MCP Server
    implementation(libs.mcp.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation("junit:junit:4.13.2")
}
