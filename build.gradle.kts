plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.2.20"
}

android {
    namespace = "com.infiniwave"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.infiniwave"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.merges += "META-INF/services/io.github.gretchycat.infinistream.CapabilityProvider" }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":editor-core"))
    implementation("io.github.gretchycat.libinfinisynth:synth-android:0.1.0")
    implementation("io.github.gretchycat.libinfiniselect:select-android:0.1.0")
    implementation("io.github.gretchycat.libinfinisound:audio-core:0.2.1")
    testImplementation("junit:junit:4.13.2")
}
