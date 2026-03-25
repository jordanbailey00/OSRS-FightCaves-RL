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
    implementation(project(":cache"))

    implementation(libs.ktor)
    implementation(libs.ktor.network)
    implementation(libs.jbcrypt)
    implementation(libs.inline.logging)
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.bundles.testing)
}
