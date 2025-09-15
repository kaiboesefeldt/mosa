plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("multiplatform") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0" apply false
    id("org.jmailen.kotlinter") version "5.1.1" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    repositories { mavenCentral() }
}
