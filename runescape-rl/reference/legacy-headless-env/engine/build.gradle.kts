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
    implementation(project(":network"))
    implementation(project(":types"))
    implementation(project(":config"))

    implementation(libs.fastutil)
    implementation(libs.kasechange)

    implementation(libs.koin)
    implementation(libs.rsmod.pathfinder)

    implementation(libs.bundles.logging)
    implementation(libs.bundles.kotlinx)
    testImplementation(libs.bundles.testing)
}

tasks.withType<Test> {
    jvmArgs("-XX:-OmitStackTraceInFastThrow")
}
