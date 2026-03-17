import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    id("org.jmailen.kotlinter")
    id("com.gradleup.shadow")
    application
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "3.4.1"
    val koinVersion = "4.1.1"
    val exposedVersion = "1.0.0-beta-2"
    val sqliteVersion = "3.51.3.0"
    val jacksonVersion = "2.21.1"
    val kotlinLoggingVersion = "8.0.01"
    val logbackVersion = "1.5.32"
    val djlVersion = "0.36.0"
    val luceneVersion = "9.12.3"
    val mockkVersion = "1.14.9"
    val auth0JwtVersion = "4.5.1"
    val bouncyCastleVersion = "1.83"
    val kotlinxCliVersion = "0.3.6"
    val mordantVersion = "3.0.2"
    val kotlinxCoroutinesVersion = "1.10.2"
    val micrometerVersion = "1.16.4"
    val jnaVersion = "5.18.1"
    val commonsValidatorVersion = "1.10.1"

    implementation(project(":shared"))

    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")

    // Ktor Auth
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")

    // Koin Dependency Injection
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:$kotlinxCliVersion")
    implementation("com.github.ajalt.mordant:mordant:$mordantVersion")
    implementation("com.github.ajalt.mordant:mordant-markdown:$mordantVersion")

    // Exposed ORM + SQLite
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    // Lucene as Vector Store for KNN search
    implementation("org.apache.lucene:lucene-core:$luceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    implementation("org.apache.lucene:lucene-backward-codecs:$luceneVersion")

    // ONNX and djl Runtime for local embeddings
    implementation(platform("ai.djl:bom:$djlVersion"))
    implementation("ai.djl:api")
    implementation("ai.djl:model-zoo")
    implementation("ai.djl.onnxruntime:onnxruntime-engine")
    implementation("ai.djl.huggingface:tokenizers")

    // JSON & HTTP Client (for Embedding APIs etc.)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$kotlinxCoroutinesVersion")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("net.java.dev.jna:jna:$jnaVersion")
    implementation("net.java.dev.jna:jna-platform:$jnaVersion")
    implementation("commons-validator:commons-validator:$commonsValidatorVersion")

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

java {
    sourceCompatibility = JavaVersion.toVersion(providers.gradleProperty("mosaJvmBytecodeTarget").get())
    targetCompatibility = JavaVersion.toVersion(providers.gradleProperty("mosaJvmBytecodeTarget").get())
}

kotlin {
    jvmToolchain(providers.gradleProperty("mosaJvmToolchainVersion").get().toInt())
}

kotlinter {
    reporters = arrayOf("html", "plain")
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(
        JvmTarget.fromTarget(providers.gradleProperty("mosaJvmBytecodeTarget").get())
    )
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val copyClientFiles = tasks.register<Copy>("copyClientFiles") {
    group = "other"
    description = "Pull the client bundle into server static resources"
    dependsOn(":client:jsBrowserDistribution")
    val clientLayout = project(":client").layout
    val sourceDirectories = listOf(
        clientLayout.buildDirectory.dir("distributions"),
        clientLayout.buildDirectory.dir("kotlin-webpack/js/productionExecutable"),
        clientLayout.buildDirectory.dir("processedResources/js/main"),
    )
    sourceDirectories.forEach { from(it) }
    into(layout.buildDirectory.dir("resources/main/static").get().asFile)
    includeEmptyDirs = false
}

tasks.named("processResources") {
    dependsOn(copyClientFiles)
}

tasks.named("shadowJar") {
    dependsOn(copyClientFiles)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("mosa-server")
    archiveClassifier.set("all")
    mergeServiceFiles()
}
