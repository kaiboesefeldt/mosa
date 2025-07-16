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
    val djlVersion = "0.31.1"
    val luceneVersion = "9.9.0"
    val mockkVersion = "1.14.4"
    val auth0JwtVersion = "4.5.0"
    val bouncyCastleVersion = "1.81"

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

    // Lucene as Vector Store for KNN search
    implementation("org.apache.lucene:lucene-core:$luceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$luceneVersion")

    // ONNX and djl Runtime for local embeddings
    implementation("ai.djl:api:$djlVersion")
    implementation("ai.djl:model-zoo:${djlVersion}")
    implementation("ai.djl.onnxruntime:onnxruntime-engine:$djlVersion")
    implementation("ai.djl.huggingface:tokenizers:$djlVersion")

    // JSON & HTTP Client (for Embedding APIs etc.)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")
    implementation("com.auth0:java-jwt:${auth0JwtVersion}")

    // Logging
    implementation("io.github.oshai:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")

    // Test
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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

tasks.test {
    useJUnitPlatform()
}

