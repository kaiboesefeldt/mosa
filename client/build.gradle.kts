plugins {
    kotlin("multiplatform")
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
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("io.insert-koin:koin-core:$koinVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
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
