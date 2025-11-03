import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jmailen.kotlinter")
    application
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val kotlinxCliVersion = "0.3.6"
    val ktorVersion = "3.3.1"
    val kotlinLoggingVersion = "7.0.13"
    val logbackVersion = "1.5.20"
    val auth0JwtVersion = "4.5.0"
    val jacksonVersion = "2.20.1"

    implementation(project(":shared"))
    implementation(project(":server"))

    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("com.auth0:java-jwt:${auth0JwtVersion}")

    implementation("io.github.oshai:kotlin-logging:${kotlinLoggingVersion}")

    implementation("ch.qos.logback:logback-classic:${logbackVersion}")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("straightway.mosa.cli.CliMainKt")
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}

kotlin {
    jvmToolchain(19)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

tasks.test {
    useJUnitPlatform()
}
