dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "void"

include("game")
include("cache")
include("engine")
include("buffer")
include("network")
include("types")
include("config")
include("fight-caves-demo-lite")
project(":fight-caves-demo-lite").projectDir = file("../headed-env")
includeBuild("build-logic")
