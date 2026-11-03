import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "straightway.mosa"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlin-wrappers/maven")
}
@Suppress("UNCHECKED_CAST")
val firefoxEnv = rootProject.extra["firefoxEnvironment"] as Map<String, String>
val prepareFirefoxEnvironment = rootProject.tasks.named("prepareFirefoxEnvironment")

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
                cssSupport {
                    enabled = true
                }
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
        jsMain {
            resources.srcDir(project(":shared").file("src/commonMain/resources"))
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation.js)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(project(":shared"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlin.react)
                implementation(libs.kotlin.react.dom)
                implementation(libs.kotlin.browser)
                implementation(npm("@dicebear/core", libs.versions.dicebear.get()))
                implementation(npm("@dicebear/adventurer-neutral", libs.versions.dicebear.get()))
                implementation(npm("@fortawesome/fontawesome-free", libs.versions.fontawesome.get()))
                implementation(npm("html2canvas", libs.versions.html2canvas.get()))
                implementation(npm("@cantoo/pdf-lib", libs.versions.cantoo.pdf.lib.get()))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test.js)
                implementation(npm("karma-source-map-support", libs.versions.karma.source.map.support.get()))
                implementation(npm("@testing-library/react", libs.versions.testing.library.react.get()))
                implementation(npm("@testing-library/user-event", libs.versions.testing.library.user.event.get()))
                implementation(npm("@testing-library/dom", libs.versions.testing.library.dom.get()))
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    dependsOn("formatKotlin")
}

tasks.withType<KotlinJsTest>().configureEach {
    dependsOn(prepareFirefoxEnvironment)
    firefoxEnv.forEach { (key, value) -> environment(key, value) }
    outputs.upToDateWhen { false }
}
