plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("multiplatform") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0" apply false
    id("org.jmailen.kotlinter") version "5.1.1" apply false
    id("com.gradleup.shadow") version "9.0.0" apply false
}

allprojects {
    repositories { mavenCentral() }
}
