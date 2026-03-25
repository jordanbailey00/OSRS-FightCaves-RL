import org.gradle.api.tasks.testing.Test

plugins {
    id("shared")
    application
}

dependencies {
    implementation(project(":game"))
    implementation(project(":engine"))
    implementation(project(":cache"))
    implementation(project(":network"))
    implementation(project(":types"))
    implementation(project(":config"))

    testImplementation(libs.bundles.testing)
}

application {
    mainClass.set("DemoLiteMain")
    tasks.run.get().workingDir = rootProject.projectDir
    tasks.run.get().systemProperty("demoLiteModuleDir", projectDir.absolutePath)
    tasks.run.get().systemProperty(
        "demoLiteSessionLogRoot",
        layout.projectDirectory.dir("session-logs").asFile.absolutePath,
    )
}

sourceSets {
    named("main") {
        resources.srcDir(layout.projectDirectory.dir("assets"))
    }
}

tasks {
    named<Test>("test") {
        workingDir = rootProject.projectDir
        jvmArgs("-XX:-OmitStackTraceInFastThrow")
        maxHeapSize = "2048m"
        maxParallelForks = 1
        forkEvery = 1
        systemProperty("java.awt.headless", "true")
        systemProperty(
            "demoLiteReportsDir",
            layout.buildDirectory.dir("reports/demo-lite").get().asFile.absolutePath,
        )
        systemProperty("demoLiteModuleDir", projectDir.absolutePath)
        systemProperty(
            "demoLiteSessionLogRoot",
            layout.buildDirectory.dir("reports/demo-lite/sessions").get().asFile.absolutePath,
        )
    }

    register("runDemoLite") {
        group = "application"
        dependsOn("run")
    }
}
