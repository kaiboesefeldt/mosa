import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlin-wrappers/maven")
}

kotlin {
    js {
        compilerOptions {
            sourceMap = true
            sourceMapEmbedSources = org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
        }
        browser {
            binaries.executable()
            webpackTask {
                mainOutputFileName = "mosa-client.js"
                devtool = "source-map"
            }
            commonWebpackConfig {
                devtool = "source-map"
            }
            testTask {
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(project.projectDir.resolve("karma.config.d"))
                }
            }
        }
    }

    sourceSets {
        val koinVersion = "4.1.1"
        val kotlinxCoroutinesVersion = "1.10.2"
        val kotlinxSerializationJsonVersion = "1.9.0"
        val ktorVersion = "3.2.0"
        val kotlinWrappersVersion = "2025.7.4-19.1.0"
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("io.insert-koin:koin-core:$koinVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation(project(":shared"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:$kotlinWrappersVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:$kotlinWrappersVersion")
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-js:$kotlinxCoroutinesVersion")
                implementation(npm("karma-source-map-support", "1.4.0"))
                implementation(npm("@testing-library/react", "16.3.0"))
                implementation(npm("@testing-library/user-event", "14.6.1"))
                implementation(npm("@testing-library/dom", "10.4.1"))
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}
