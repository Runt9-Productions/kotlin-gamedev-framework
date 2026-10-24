import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    id("org.jetbrains.kotlinx.kover")
    // io.github, not com.github: the plugin moved namespace and the old id is deprecated.
    id("io.github.ben-manes.versions")
}

java {
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()

    // Incubating since 8.8.
    reports.junitXml.includeSystemOutLog.set(false)
    reports.junitXml.includeSystemErrLog.set(false)

    // Gradle prints counts only on failure, so a green run reads the same as one that executed
    // nothing. addTestListener, not the afterSuite every example shows -- that is deprecated and
    // goes away in Gradle 10. Keep `label` out of the listener or it captures the task.
    val label = path
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit
        override fun beforeTest(test: TestDescriptor) = Unit
        override fun afterTest(test: TestDescriptor, result: TestResult) = Unit

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null) return // root suite only; it carries the task totals
            println(
                "TEST-SUMMARY $label ${result.resultType}" +
                    " total=${result.testCount}" +
                    " passed=${result.successfulTestCount}" +
                    " failed=${result.failedTestCount}" +
                    " skipped=${result.skippedTestCount}"
            )
        }
    })
}

kover {
    // useJacoco inside a kover block is not a contradiction: Kover keeps the DSL and reporting, and only the
    // instrumentation engine changes. JetBrains discontinued their own agent (kotlinx-kover#720) in favor of
    // JaCoCo (#746).
    //
    // Precompiled script plugins get no generated `libs` accessor, hence the explicit lookup. `.get()` is
    // deliberate: a missing `jacoco` entry must fail the build rather than silently default.
    val jacocoVersion = the<VersionCatalogsExtension>().named("libs").findVersion("jacoco").get().requiredVersion
    useJacoco(jacocoVersion)
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Runt9-Productions/kotlin-gamedev-framework")

            // gpr.key must be a CLASSIC token. The Maven registry rejects fine-grained ones with a 401, which
            // reads as a wrong password rather than as an unsupported token type. In Actions the built-in
            // GITHUB_TOKEN works instead, but only once the consuming repo is granted read access to the package.
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["java"])
        }
    }
}

tasks.withType<KotlinCompile> {
    val optIns = listOf(
        "ktx.reflect.Reflection",
        "kotlinx.serialization.ExperimentalSerializationApi",
        "kotlinx.coroutines.ExperimentalCoroutinesApi"
    )
    compilerOptions.freeCompilerArgs.add("-opt-in=${optIns.joinToString(",")}")
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
