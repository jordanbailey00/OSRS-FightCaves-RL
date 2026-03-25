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

dependencies {
    implementation(project(":buffer"))
    implementation(project(":types"))
    implementation(libs.displee.cache)
    implementation(libs.koin)

    implementation(libs.lzma)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.fastutil)

    implementation(libs.bundles.logging)

    testImplementation(libs.mockk)
}
