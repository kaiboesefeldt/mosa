import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.shadow)
    application
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.auth)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)
    implementation(libs.kotlinx.cli)
    implementation(libs.mordant)
    implementation(libs.mordant.markdown)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.lucene.core)
    implementation(libs.lucene.analysis.common)
    implementation(platform(libs.djl.bom))
    implementation(libs.djl.api)
    implementation(libs.djl.model.zoo)
    implementation(libs.djl.onnxruntime.engine)
    implementation(libs.djl.tokenizers)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    implementation(libs.auth0.jwt)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(libs.micrometer.core)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.commons.validator)
    testImplementation(libs.ktor.server.test.host) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
    }
    testImplementation(libs.mockk)
    testImplementation(libs.byte.buddy)
    testImplementation(libs.byte.buddy.agent)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
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
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}
