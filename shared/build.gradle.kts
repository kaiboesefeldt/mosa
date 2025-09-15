plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jmailen.kotlinter")
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val kotlinxSerializationVersion = "1.9.0"
    jvm() 
    js(IR) {
        browser()
        nodejs()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }
        val commonTest by getting
    }
    jvmToolchain(19)
}

kotlinter {
    reporters = arrayOf("html", "plain")
}
