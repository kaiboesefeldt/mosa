import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinter)
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
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
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

tasks.withType<KotlinJsTest>().configureEach {
    dependsOn(prepareFirefoxEnvironment)
    firefoxEnv.forEach { (key, value) -> environment(key, value) }
    outputs.upToDateWhen { false }
}
