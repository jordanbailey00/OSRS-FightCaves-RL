plugins {
    id("shared")
}

kotlin {
    sourceSets {
        named("main") {
            kotlin.srcDirs(".")
            kotlin.exclude("test/**", "resources/**", "test-resources/**", "build/**", ".gradle/**")
        }
        named("test") {
            kotlin.srcDirs("test")
        }
    }
}

sourceSets {
    named("main") {
        resources.setSrcDirs(listOf("resources"))
    }
    named("test") {
        resources.setSrcDirs(listOf("test-resources"))
    }
}
