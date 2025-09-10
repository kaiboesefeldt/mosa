plugins {
    kotlin("multiplatform")
    id("org.jmailen.kotlinter")
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    // Skeleton shared module; we'll add DTOs later
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    sourceSets {
        val commonMain by getting
        val commonTest by getting
    }
}

kotlinter {
    reporters = arrayOf("html", "plain")
}
