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
        }
    }
    
    sourceSets {
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
    }
}
