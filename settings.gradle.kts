rootProject.name = "mosa"

include("server")
include("client")
include("shared")

plugins {
    id("org.jetbrains.kotlinx.kover.aggregation") version "0.9.3"
}

kover {
    enableCoverage()

    reports {
        includedProjects.add(":server")
        includedProjects.add(":shared")

        excludedProjects.add(":client")

        excludesAnnotatedBy.add("*.Generated*")
    }
}
