import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    id("base")
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.shadow) apply false
}

val firefoxWorkspace = layout.projectDirectory.dir(".fx")
val firefoxProfileDir = firefoxWorkspace.dir("profile")
val firefoxCacheDir = firefoxWorkspace.dir("cache")
val firefoxRuntimeDir = firefoxWorkspace.dir("runtime")
val firefoxRuntimeDconfDir = firefoxRuntimeDir.dir("dconf")
val firefoxTmpDir = firefoxWorkspace.dir("tmp")
val firefoxEnvironment = mapOf(
    "XDG_CACHE_HOME" to firefoxCacheDir.asFile.absolutePath,
    "XDG_RUNTIME_DIR" to firefoxRuntimeDir.asFile.absolutePath,
    "TMPDIR" to firefoxTmpDir.asFile.absolutePath,
    "GSETTINGS_BACKEND" to "memory",
    "MOZ_DISABLE_CONTENT_SANDBOX" to "1"
)

extra["firefoxEnvironment"] = firefoxEnvironment

val prepareFirefoxEnvironment by tasks.registering {
    doLast {
        listOf(
            firefoxWorkspace,
            firefoxProfileDir,
            firefoxCacheDir,
            firefoxRuntimeDir,
            firefoxRuntimeDconfDir,
            firefoxTmpDir
        ).forEach { it.asFile.mkdirs() }
        firefoxTmpDir.asFile.listFiles()?.forEach { entry ->
            if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
        }
        try {
            Files.setPosixFilePermissions(
                firefoxRuntimeDir.asFile.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                )
            )
        } catch (_: Exception) {
        }
    }
}

tasks.named("build") {
    dependsOn(prepareFirefoxEnvironment)
}

allprojects {
    repositories {
        val nodeJsDistribution = ivy {
            name = "nodeJsDistribution"
            url = uri("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepositories(nodeJsDistribution)
            filter { includeModule("org.nodejs", "node") }
        }
        mavenCentral()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        project.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().apply {
            version = providers.gradleProperty("mosaNodeJsVersion").get()
            downloadBaseUrl = null
        }
    }
}
