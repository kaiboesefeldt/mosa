import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    val ktorVersion = "3.2.0"
    val koinVersion = "4.1.0"
    val exposedVersion = "1.0.0-beta-2"
    val sqliteVersion = "3.46.0.0"
    val jacksonVersion = "2.17.2"
    val kotlinLoggingVersion = "7.0.7"
    val logbackVersion = "1.5.18"
    val hnswlibVersion = "1.2.1"
    val onnxruntimeVersion = "1.22.0"
    val mockkVersion = "1.14.4"

    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")

    // Ktor Auth
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")

    // Koin Dependency Injection
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // Exposed ORM + SQLite
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    // HNSW4J for in-process vector indexing
    implementation("com.github.jelmerk:hnswlib-core-jdk17:${hnswlibVersion}")

    // ONNX Runtime for local embeddings
    implementation("com.microsoft.onnxruntime:onnxruntime:$onnxruntimeVersion")

    // JSON & HTTP Client (for Embedding APIs etc.)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Logging
    implementation("io.github.oshai:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")

    // Test
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("straightway.mosa.MainKt")
}

kotlin {
    jvmToolchain(19)
}

kotlinter {
    reporters = arrayOf("html", "plain")
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}
