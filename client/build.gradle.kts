plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    js {
        browser {
            binaries.executable()
            webpackTask {
                mainOutputFileName = "mosa-client.js"
                // Generate source maps to improve stack traces
                this as org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
                devtool = "source-map"
            }
            commonWebpackConfig {
                devtool = "source-map"
            }
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
    }
    
    sourceSets {
        val koinVersion = "4.1.1"
        val kotlinxCoroutinesVersion = "1.10.2"
        val kotlinxSerializationJsonVersion = "1.9.0"
        val ktorVersion = "3.2.0"
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
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-js:$kotlinxCoroutinesVersion")
            }
        }
    }
}
