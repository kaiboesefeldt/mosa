import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    id("base")
    kotlin("jvm") version "2.3.10" apply false
    kotlin("multiplatform") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" apply false
    id("org.jmailen.kotlinter") version "5.4.2" apply false
    id("com.gradleup.shadow") version "9.4.0" apply false
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
    repositories { mavenCentral() }
}
