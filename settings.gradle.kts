pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Ella"
include(":app")
include(":ffmpeg-decoder")

val miuixIncludedBuildPath = providers.environmentVariable("MIUIX_INCLUDED_BUILD_PATH")
    .orElse("C:/Users/Croilan/Documents/CodeX Spaces/miuix")
    .get()
val miuixIncludedBuildDir = file(miuixIncludedBuildPath)

if (miuixIncludedBuildDir.exists()) {
    includeBuild(miuixIncludedBuildDir) {
        dependencySubstitution {
            substitute(module("top.yukonga.miuix.kmp:miuix-ui")).using(project(":miuix-ui"))
            substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
            substitute(module("top.yukonga.miuix.kmp:miuix-core")).using(project(":miuix-core"))
            substitute(module("top.yukonga.miuix.kmp:miuix-core-android")).using(project(":miuix-core"))
            substitute(module("top.yukonga.miuix.kmp:miuix-icons")).using(project(":miuix-icons"))
            substitute(module("top.yukonga.miuix.kmp:miuix-icons-android")).using(project(":miuix-icons"))
            substitute(module("top.yukonga.miuix.kmp:miuix-shapes")).using(project(":miuix-shapes"))
            substitute(module("top.yukonga.miuix.kmp:miuix-shapes-android")).using(project(":miuix-shapes"))
            substitute(module("top.yukonga.miuix.kmp:miuix-blur")).using(project(":miuix-blur"))
            substitute(module("top.yukonga.miuix.kmp:miuix-blur-android")).using(project(":miuix-blur"))
            substitute(module("top.yukonga.miuix.kmp:miuix-preference")).using(project(":miuix-preference"))
            substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
        }
    }
}
