plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.gch.miroir"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutinesCore)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}