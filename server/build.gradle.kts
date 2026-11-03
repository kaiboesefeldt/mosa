import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.file.DuplicatesStrategy
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.w3c.dom.Element

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

val runtimeClasspath = configurations.named("runtimeClasspath")
val serverThirdPartyNotices = layout.buildDirectory.file("generated/third-party-notices/SERVER-THIRD-PARTY-NOTICES.txt")
val generateServerThirdPartyNotices = tasks.register("generateServerThirdPartyNotices") {
    inputs.files(runtimeClasspath, fileTree("src/main/resources/licenses"))
    outputs.file(serverThirdPartyNotices)

    doLast {
        val output = serverThirdPartyNotices.get().asFile
        output.parentFile.mkdirs()
        val artifacts = runtimeClasspath.get().incoming.artifacts.artifacts
            .sortedBy { it.id.componentIdentifier.displayName }
        val licenseTexts = linkedMapOf<String, Pair<String, MutableSet<String>>>()
        val componentRows = mutableListOf<Triple<String, String, String>>()
        val cacheRoot = gradle.gradleUserHomeDir.toPath().resolve("caches/modules-2/files-2.1")
        val knownLicenseMetadata = mapOf(
            "co.touchlab:stately-concurrency-jvm:2.1.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "co.touchlab:stately-concurrent-collections-jvm:2.1.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "co.touchlab:stately-strict-jvm:2.1.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "com.google.code.gson:gson:2.13.1" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "com.google.errorprone:error_prone_annotations:2.38.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "com.microsoft.onnxruntime:onnxruntime:1.21.1" to "MIT — https://github.com/microsoft/onnxruntime/blob/main/LICENSE",
            "commons-beanutils:commons-beanutils:1.11.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "commons-codec:commons-codec:1.17.1" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "commons-collections:commons-collections:3.2.2" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "commons-digester:commons-digester:2.1" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "commons-io:commons-io:2.16.1" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "commons-validator:commons-validator:1.11.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "io.netty:netty-codec-native-quic:4.2.17.Final" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "org.apache.commons:commons-compress:1.27.1" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "org.eclipse.jetty.alpn:alpn-api:1.1.3.v20160715" to "Apache-2.0 or EPL-1.0 (Apache-2.0 selected) — https://www.apache.org/licenses/LICENSE-2.0",
            "org.hdrhistogram:HdrHistogram:2.2.2" to "CC0-1.0 — https://creativecommons.org/publicdomain/zero/1.0/",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.6" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "org.jetbrains:annotations:23.0.0" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "org.yaml:snakeyaml:2.5" to "Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
            "project ':shared'" to "MOSA project code, Apache-2.0 — https://www.apache.org/licenses/LICENSE-2.0",
        )

        fun findCachedPom(group: String, module: String, version: String): java.io.File? {
            val directory = cacheRoot.resolve(group).resolve(module).resolve(version)
            if (!Files.exists(directory)) return null
            return Files.walk(directory).use { paths ->
                paths.filter { path -> path.fileName.toString() == "$module-$version.pom" }
                    .findFirst().orElse(null)?.toFile()
            }
        }

        fun readPomLicenses(
            pomFile: java.io.File,
            seen: Set<String> = emptySet(),
        ): List<String> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val document = factory.newDocumentBuilder().parse(pomFile)
            val licenses = (0 until document.getElementsByTagName("license").length).map { index ->
                val license = document.getElementsByTagName("license").item(index) as Element
                fun childText(name: String) =
                    (0 until license.childNodes.length)
                        .map { license.childNodes.item(it) }
                        .firstOrNull { it.nodeName == name }
                        ?.textContent?.trim().orEmpty()
                listOf(childText("name"), childText("url")).filter(String::isNotBlank).joinToString(" — ")
            }.filter(String::isNotBlank)
            if (licenses.isNotEmpty()) return licenses

            val parentNodes = document.getElementsByTagName("parent")
            if (parentNodes.length == 0) return emptyList()
            val parent = parentNodes.item(0) as Element
            fun parentText(name: String) =
                (0 until parent.childNodes.length)
                    .map { parent.childNodes.item(it) }
                    .firstOrNull { it.nodeName == name }
                    ?.textContent?.trim().orEmpty()
            val group = parentText("groupId")
            val module = parentText("artifactId")
            val version = parentText("version")
            val parentId = "$group:$module:$version"
            if (group.isBlank() || module.isBlank() || version.isBlank() || parentId in seen) return emptyList()
            val parentPom = findCachedPom(group, module, version) ?: return emptyList()
            return readPomLicenses(parentPom, seen + parentId)
        }

        artifacts.forEach { artifact ->
            val componentId = artifact.id.componentIdentifier
            val component = componentId.displayName
            val pomFile =
                (componentId as? ModuleComponentIdentifier)?.let { module ->
                    findCachedPom(module.group, module.module, module.version)
                }
            val pomLicenses = pomFile?.let(::readPomLicenses).orEmpty()

            val textFiles =
                if (artifact.file.isFile && artifact.file.extension.equals("jar", ignoreCase = true)) {
                    ZipFile(artifact.file).use { zip ->
                        zip.entries().asSequence()
                            .filterNot { it.isDirectory }
                            .filter { entry ->
                                val name = entry.name.substringAfterLast('/').lowercase()
                                val isTextFile = name.endsWith(".txt") || name.endsWith(".md") || !name.contains('.')
                                isTextFile && (name.contains("license") || name.contains("notice") || name.contains("copying"))
                            }
                            .sortedBy { it.name }
                            .map { entry -> entry.name to zip.getInputStream(entry).bufferedReader().use { it.readText() } }
                            .toList()
                    }
                } else {
                    emptyList()
                }
            val entries = textFiles.map { (path, text) ->
                val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                    .joinToString("") { byte: Byte -> "%02x".format(byte) }
                val label = "$component — $path"
                val current = licenseTexts[digest]
                if (current == null) {
                    licenseTexts[digest] = text to mutableSetOf(label)
                } else {
                    current.second += label
                }
                path
            }
            componentRows += Triple(
                component,
                pomLicenses.ifEmpty { listOf(knownLicenseMetadata[component] ?: "No license declared in cached Maven POM") }.joinToString("; "),
                entries.ifEmpty { listOf("No embedded license or notice text") }.joinToString("; "),
            )
        }

        val noticeText = buildString {
            appendLine("Third-party runtime dependency notices")
            appendLine("Generated from the resolved server runtimeClasspath. Maven license names and URLs come from each resolved component's POM; embedded license and notice texts are included below.")
            appendLine()
            appendLine("## Component inventory")
            appendLine()
            appendLine("| Component | Maven license metadata | Embedded files |")
            appendLine("| --- | --- | --- |")
            componentRows.forEach { (component, licenses, entries) ->
                appendLine("| $component | ${licenses.replace("|", "\\|")} | ${entries.replace("|", "\\|")} |")
            }
            appendLine()
            appendLine("## Embedded license and notice texts")
            appendLine()
            licenseTexts.toSortedMap().forEach { (digest, pair) ->
                appendLine("### SHA-256 `$digest`")
                appendLine()
                appendLine("Found in:")
                pair.second.sorted().forEach { appendLine("- `$it`") }
                appendLine()
                appendLine("```text")
                appendLine(pair.first.trimEnd())
                appendLine("```")
                appendLine()
            }
            appendLine("## Additional license texts")
            appendLine()
            appendLine("The following full license texts are included with the server distribution under `licenses/`:")
            appendLine("- Logback: `licenses/EPL-2.0.html` and `licenses/LGPL-2.1.txt` (the component offers either license).")
            appendLine("- Auth0 java-jwt 4.6.2 MIT license: `licenses/java-jwt-4.6.2-MIT.txt`.")
            appendLine("- colormath 3.7.0 MIT license: `licenses/colormath-3.7.0-MIT.txt`.")
            appendLine("- Apache License 2.0: `MOSA-LICENSE.txt` at the archive root; applies to components marked Apache-2.0 in the inventory.")
        }
        output.writeText(noticeText)
    }
}

tasks.named("processResources") {
    dependsOn(copyClientFiles)
}

copyClientFiles.configure {
    dependsOn(generateServerThirdPartyNotices)
    from(serverThirdPartyNotices)
    from("src/main/resources/licenses") {
        into("licenses")
    }
}

tasks.named("shadowJar") {
    dependsOn(copyClientFiles)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    dependsOn(generateServerThirdPartyNotices)
    from(rootProject.file("LICENSE")) {
        rename { "MOSA-LICENSE.txt" }
    }
    from(serverThirdPartyNotices)
    archiveBaseName.set("mosa-server")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}
