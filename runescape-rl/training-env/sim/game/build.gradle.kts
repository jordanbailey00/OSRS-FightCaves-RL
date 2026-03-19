import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.Test

plugins {
    id("tasks.metadata")
    id("shared")
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":cache"))
    implementation(project(":network"))
    implementation(project(":types"))
    implementation(project(":config"))
    implementation(libs.fastutil)
    implementation(libs.kasechange)
    implementation(libs.rsmod.pathfinder)

    implementation(kotlin("script-runtime"))
    implementation(libs.bundles.kotlinx)

    implementation(libs.koin)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
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

application {
    mainClass.set("Main")
    tasks.run.get().workingDir = rootProject.projectDir
}

private val headlessDataBundleFiles: List<String> by lazy {
    val allowlist = rootProject.file("config/headless_data_allowlist.toml")
    if (!allowlist.isFile) {
        emptyList()
    } else {
        Regex("\"(data/[^\"]+\\.toml)\"")
            .findAll(allowlist.readText())
            .map { match -> match.groupValues[1] }
            .toSet()
            .sorted()
    }
}

tasks {
    processResources {
        dependsOn("scriptMetadata")
    }

    named("build") {
        dependsOn("scriptMetadata")
    }

    named("classes") {
        dependsOn("scriptMetadata")
    }

    register("scriptMetadata", ScriptMetadataTask::class.java) {
        val main = sourceSets.getByName("main")
        val resources = main.resources.srcDirs.first { it.name == "resources" }
        inputDirectory.set(layout.projectDirectory.dir("content"))
        scriptsFile = resources.resolve("scripts.txt")
        resourceDirectory = resources
    }

    named<ShadowJar>("shadowJar") {
        dependsOn("scriptMetadata")
        from(layout.buildDirectory.file("scripts.txt"))
        minimize {
            exclude("sim/engine/log/**")
            exclude(dependency("ch.qos.logback:logback-classic:.*"))
        }
        archiveBaseName.set("void-server-$version")
        archiveClassifier.set("")
        archiveVersion.set("")
        // Replace logback file as the custom colour classes can't be individually excluded from minimization
        // https://github.com/GradleUp/shadow/issues/638
        exclude("logback.xml")
        val resourcesDir = layout.projectDirectory.dir("resources")
        val logback =
            resourcesDir
                .file("logback.xml")
                .asFile
                .readText()
                .replace("%colour", "%highlight")
                .replace("%message(%msg){}", "%msg")
        val replacement =
            layout.buildDirectory
                .file("logback-test.xml")
                .get()
                .asFile
        replacement.parentFile.mkdirs()
        replacement.writeText(logback)
        from(replacement)
    }

    register<ShadowJar>("headlessShadowJar") {
        dependsOn("scriptMetadata")
        from(sourceSets["main"].output)
        from(layout.buildDirectory.file("scripts.txt"))
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("fight-caves-headless")
        archiveClassifier.set("")
        archiveVersion.set("")
        manifest {
            attributes["Main-Class"] = "HeadlessMain"
        }
        mergeServiceFiles()
    }

    register<JavaExec>("generateHeadlessDeletionCandidates") {
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("HeadlessDeletionCandidates")
        workingDir = rootProject.projectDir
    }

    register("packageHeadless") {
        dependsOn("generateHeadlessDeletionCandidates", "headlessDistZip")
    }

    register("headlessDist") {
        dependsOn("headlessDistZip")
    }

    register<JavaExec>("headlessPerformanceReport") {
        description = "Runs the standalone headless performance harness and writes repo-owned benchmark artifacts."
        group = "verification"
        dependsOn("testClasses", "scriptMetadata")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("HeadlessPerformanceReportGenerator")
        workingDir = project.projectDir
        systemProperty("java.awt.headless", "true")
        val outputLog = rootProject.file("history/performance_benchmark.log")
        val outputJson = rootProject.file("history/performance_benchmark.json")
        outputs.files(outputLog, outputJson)
        args(
            "--output-log",
            outputLog.absolutePath,
            "--output-json",
            outputJson.absolutePath,
        )
    }

    register<JavaExec>("headlessPerformanceProfile") {
        description = "Runs the standalone headless performance harness under JFR without the JUnit/Gradle test worker noise."
        group = "verification"
        dependsOn("testClasses", "scriptMetadata")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("HeadlessPerformanceReportGenerator")
        workingDir = project.projectDir
        systemProperty("java.awt.headless", "true")

        val reportsDir = layout.buildDirectory.dir("reports/headless-performance")
        val outputLog = reportsDir.map { it.file("headless_performance_profile.log").asFile }
        val outputJson = reportsDir.map { it.file("headless_performance_profile.json").asFile }
        val jfrOutput =
            providers.gradleProperty("headlessJfrOutput").orElse(
                reportsDir.map { it.file("headless_performance_profile.jfr").asFile.absolutePath }
            )
        inputs.property("headlessJfrOutput", jfrOutput)
        outputs.files(outputLog, outputJson)

        doFirst {
            reportsDir.get().asFile.mkdirs()
        }

        jvmArgs(
            "-XX:StartFlightRecording=filename=${jfrOutput.get()},settings=profile,dumponexit=true",
            "-XX:FlightRecorderOptions=stackdepth=256",
        )
        args(
            "--output-log",
            outputLog.get().absolutePath,
            "--output-json",
            outputJson.get().absolutePath,
        )
    }

    register<Test>("e2eTest") {
        description = "Runs Fight Caves headless/oracle end-to-end release gate suites."
        group = "verification"
        useJUnitPlatform()
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        jvmArgs("-XX:-OmitStackTraceInFastThrow")
        maxHeapSize = "2048m"
        maxParallelForks = 1
        forkEvery = 1
        systemProperty("java.awt.headless", "true")
        testLogging {
            events("passed", "skipped", "failed")
        }
        filter {
            includeTestsMatching("HeadlessBootWithoutNetworkTest")
            includeTestsMatching("HeadlessTickPipelineOrderTest")
            includeTestsMatching("DeterministicReplaySameSeedSameTraceTest")
            includeTestsMatching("DeterministicReplayDifferentSeedDivergesTest")
            includeTestsMatching("RngCounterMonotonicityTest")
            includeTestsMatching("ParityHarnessSingleWaveTraceTest")
            includeTestsMatching("ParityHarnessFullRunTraceTest")
            includeTestsMatching("ParityHarnessJadHealerScenarioTest")
            includeTestsMatching("ParityHarnessTzKekSplitScenarioTest")
            includeTestsMatching("ActionWalkToUsesPathfinderTest")
            includeTestsMatching("ActionEatDrinkLockoutRejectionTest")
            includeTestsMatching("ActionPrayerToggleParityTest")
            includeTestsMatching("EpisodeInitSetsFixedStatsTest")
            includeTestsMatching("EpisodeInitSetsLoadoutAndConsumablesTest")
            includeTestsMatching("EpisodeInitResetsWaveStateTest")
            includeTestsMatching("EpisodeInitUsesProvidedSeedTest")
            includeTestsMatching("HeadlessPackageStartsWithoutExcludedSystemsTest")
            includeTestsMatching("HeadlessScriptRegistryContainsFightCaveHandlersTest")
            includeTestsMatching("HeadlessScriptRegistryExcludesUnrelatedSystemsTest")
            includeTestsMatching("HeadlessStepRateBenchmarkTest")
            includeTestsMatching("HeadlessLongRunStabilityTest")
            includeTestsMatching("HeadlessBatchSteppingParityTest")
            includeTestsMatching("HeadlessPerformanceReportGenerationTest")
            includeTestsMatching("ProjectTreeMatchesApprovedManifestTest")
            includeTestsMatching("ForbiddenPathsAbsentTest")
        }
    }
    register("printVersion") {
        doLast {
            println(project.version)
        }
    }

    register("printCacheVersion") {
        doLast {
            println(libs.versions.cacheVersion.get())
        }
    }

    test {
        jvmArgs("-XX:-OmitStackTraceInFastThrow")
        maxHeapSize = "2048m"
        maxParallelForks = 1
        forkEvery = 1
    }
}

distributions {
    create("bundle") {
        distributionBaseName = "void"
        contents {
            from(tasks["shadowJar"])

            val emptyDirs = setOf("cache", "saves")
            val configs =
                parent!!
                    .rootDir
                    .resolve("data")
                    .list()!!
                    .toMutableList()
            configs.removeAll(emptyDirs)
            for (config in configs) {
                from("../data/$config/") {
                    into("data/$config")
                }
            }
            for (dir in emptyDirs) {
                val file =
                    layout.buildDirectory
                        .get()
                        .dir("tmp/empty/$dir/")
                        .asFile
                file.mkdirs()
            }
            from(layout.buildDirectory.dir("tmp/empty/")) {
                into("data")
            }
            val tempDir =
                layout.buildDirectory
                    .dir("tmp/scripts")
                    .get()
                    .asFile
            tempDir.mkdirs()
            val resourcesDir = layout.projectDirectory.dir("resources")
            from(resourcesDir.file("game.properties"))
            val bat = resourcesDir.file("run-server.bat").asFile
            val tempBat = File(tempDir, "run-server.bat")
            tempBat.writeText(bat.readText().replace("-dev.jar", "-$version.jar"))
            from(tempBat)
            val shell = resourcesDir.file("run-server.sh").asFile
            val tempShell = File(tempDir, "run-server.sh")
            tempShell.writeText(shell.readText().replace("-dev.jar", "-$version.jar"))
            println("Bundling $tempShell")
            from(tempShell)
        }
    }

    create("headless") {
        distributionBaseName = "fight-caves-headless"
        contents {
            from(tasks["headlessShadowJar"])
            from(rootProject.file("config/headless_data_allowlist.toml"))
            from(rootProject.file("config/headless_manifest.toml"))
            from(rootProject.file("config/headless_scripts.txt"))

            val resourcesDir = layout.projectDirectory.dir("resources")
            from(resourcesDir.file("game.properties"))

            for (relativePath in headlessDataBundleFiles) {
                val file = rootProject.file(relativePath)
                if (!file.isFile) {
                    continue
                }
                from(file) {
                    into(relativePath.substringBeforeLast('/'))
                }
            }

            val emptyDirs = setOf("cache", "saves")
            for (dir in emptyDirs) {
                val file =
                    layout.buildDirectory
                        .get()
                        .dir("tmp/empty-headless/$dir/")
                        .asFile
                file.mkdirs()
            }
            from(layout.buildDirectory.dir("tmp/empty-headless/")) {
                into("data")
            }

            val tempDir =
                layout.buildDirectory
                    .dir("tmp/headless/scripts")
                    .get()
                    .asFile
            tempDir.mkdirs()

            val shell = File(tempDir, "run-headless.sh")
            shell.writeText(
                """
                |#!/usr/bin/env bash
                |title="Fight Caves Headless"
                |echo -e '\033]2;'${'$'}title'\007'
                |java -jar fight-caves-headless.jar
                |if [ $? -ne 0 ]; then
                |  echo "Error: The Java application exited with a non-zero status."
                |  read -p "Press enter to continue..."
                |fi
                |""".trimMargin(),
            )
            shell.setExecutable(true)
            from(shell)

            val bat = File(tempDir, "run-headless.bat")
            bat.writeText(
                """
                |@echo off
                |title Fight Caves Headless
                |java -jar fight-caves-headless.jar
                |pause
                |""".trimMargin(),
            )
            from(bat)
        }
    }
}

dependencies {
    allprojects.filter { it.name != "tools" }.forEach {
        jacocoAggregation(it)
    }
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("temp/", "**/build/**", "**/out/**")
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "intellij_idea",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_package-name" to "disabled",
                ),
            )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    flexmark {
        target("**/*.md")
        flexmark()
    }
}






