import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("kotlin")
    id("idea")
    id("jacoco")
    id("com.diffplug.spotless")
    id("jacoco-report-aggregation")
}

group = "world.gregs.void"

fun sanitizeBuildVersion(raw: String?): String {
    val candidate = raw ?: return "dev"
    val sanitized =
        candidate
            .map { character ->
                if (character.isLetterOrDigit() || character in "._-") character else '-'
            }.joinToString("")
            .trim('.', '-')
    return sanitized.ifEmpty { "dev" }
}

version = sanitizeBuildVersion(System.getenv("GITHUB_REF_NAME"))

val java21 = JavaLanguageVersion.of(21)
val javaToolchains = extensions.getByType<JavaToolchainService>()

java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = java.sourceCompatibility
java {
    toolchain {
        languageVersion.set(java21)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
//         https://youtrack.jetbrains.com/issue/KT-4779/Generate-default-methods-for-implementations-in-interfaces
        freeCompilerArgs.addAll("-Xinline-classes", "-Xcontext-parameters", "-Xjvm-default=all-compatibility")
    }
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(java21)
        },
    )
}


dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.platform:junit-platform-launcher:1.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.13.4")
}

if (name != "tools") {
    tasks.test {
        maxHeapSize = "5120m"
        useJUnitPlatform()
        failFast = true
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        finalizedBy(tasks.jacocoTestReport)
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required = true
            csv.required = false
        }
    }
}
