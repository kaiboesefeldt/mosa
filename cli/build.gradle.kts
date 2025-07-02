plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jmailen.kotlinter") version "5.1.1"
    application
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val kotlinxCliVersion = "0.3.6"
    val ktorVersion = "3.2.0"
    implementation(project(":"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("straightway.mosa.cli.CliMainKt")
}

tasks.test {
    useJUnitPlatform()
}