pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val localMirror = file(".local/maven")
        if (localMirror.isDirectory) maven { url = uri(localMirror) }
        google(); mavenCentral()
    }
}
rootProject.name = "XingSiLu"
include(":app")
