plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "com.gch.miroir"
version = "unspecified"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    // JVM fallback
    jvm()

    // Native targets
    // windows
    mingwX64("windows") {
        compilations.getByName("main") {
            cinterops {
                val win32 by creating {
                    defFile(project.file("src/nativeInterop/cinterop/win32.def"))
                    packageName = "win32"
                }
            }
        }
    }

    // linux
    linuxX64()
    // macOS
    macosX64()
    macosArm64()

    // Source sets
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutinesCore)
            }
        }

        val windowsMain by getting {
            dependsOn(commonMain)
        }

        val linuxX64Main by getting {
            dependsOn(commonMain)
        }

        val macosX64Main by getting {
            dependsOn(commonMain)
        }

        val macosArm64Main by getting {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(commonMain)
        }
    }
}