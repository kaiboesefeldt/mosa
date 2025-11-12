import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jmailen.kotlinter")
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

@Suppress("UNCHECKED_CAST")
val firefoxEnv = rootProject.extra["firefoxEnvironment"] as Map<String, String>
val prepareFirefoxEnvironment = rootProject.tasks.named("prepareFirefoxEnvironment")

kotlin {
    val kotlinxSerializationVersion = "1.9.0"
    jvm() 
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(project.projectDir.resolve("karma.config.d"))
                }
            }
        }
        nodejs()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
    jvmToolchain(19)
}

kotlinter {
    reporters = arrayOf("html", "plain")
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}

tasks.withType<KotlinJsTest>().configureEach {
    dependsOn(prepareFirefoxEnvironment)
    firefoxEnv.forEach { (key, value) -> environment(key, value) }
    outputs.upToDateWhen { false }
}
