pluginManagement {
    resolutionStrategy.eachPlugin {
        if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:8.9.2")
        if (requested.id.id.startsWith("org.jetbrains.kotlin.")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.omicronapps") } }
    }
}
rootProject.name = "InfiniWave"

// Composite builds resolve only from this standalone checkout's pinned libraries.
val dependencyRoot = file("libraries")
System.setProperty("infini.dependencies", dependencyRoot.canonicalPath)
includeBuild(dependencyRoot.resolve("libinfinistream"))
includeBuild(dependencyRoot.resolve("libinfinisound"))
includeBuild(dependencyRoot.resolve("libinfiniselect"))

includeBuild(dependencyRoot.resolve("libinfinisynth"))
include(":editor-core")
