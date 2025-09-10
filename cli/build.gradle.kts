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
    val ktorVersion = "3.2.0"
    val kotlinLoggingVersion = "7.0.7"
    val logbackVersion = "1.5.18"
    val auth0JwtVersion = "4.5.0"
    val jacksonVersion = "2.17.2"

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

tasks.test {
    useJUnitPlatform()
}
