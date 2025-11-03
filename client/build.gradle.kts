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
        val ktorVersion = "3.3.1"
        val kotlinWrappersVersion = "2025.11.1-19.2.0"
        val diceBearVersion = "9.2.4"
        val fontAwesomeVersion = "7.1.0"

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
                implementation(npm("@dicebear/core", diceBearVersion))
                implementation(npm("@dicebear/adventurer-neutral", diceBearVersion))
                implementation(npm("@fortawesome/fontawesome-free", fontAwesomeVersion))
            }
        }

        val karmaSourceMapSupportVersion = "1.4.0"
        val testingLibReactVersion = "16.3.0"
        val testingLibUserEventVersion = "14.6.1"
        val testingLibDomVersion = "10.4.1"
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-js:$kotlinxCoroutinesVersion")
                implementation(npm("karma-source-map-support", karmaSourceMapSupportVersion))
                implementation(npm("@testing-library/react", testingLibReactVersion))
                implementation(npm("@testing-library/user-event", testingLibUserEventVersion))
                implementation(npm("@testing-library/dom", testingLibDomVersion))
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}
