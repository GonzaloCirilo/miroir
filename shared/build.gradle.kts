plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.gch.miroir"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.kotlinx.coroutinesCore)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(18)
}