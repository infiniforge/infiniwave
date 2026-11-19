plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    api("io.github.gretchycat.libinfinisynth:synth-core:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
