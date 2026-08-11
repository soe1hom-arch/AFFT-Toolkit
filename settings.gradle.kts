pluginManagement {
    repositories {
        // Mirror lokal opsional: dipakai jika direktori AFFT_M2_LOCAL (atau
        // /opt/m2local) ada. Berguna saat koneksi ke Maven Central flaky.
        val localMirror = System.getenv("AFFT_M2_LOCAL") ?: "/opt/m2local"
        if (File(localMirror).isDirectory) {
            maven { url = uri("file://$localMirror") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val localMirror = System.getenv("AFFT_M2_LOCAL") ?: "/opt/m2local"
        if (File(localMirror).isDirectory) {
            maven { url = uri("file://$localMirror") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AFFT"
include(":app")
