buildscript {
    repositories {
        mavenCentral()
    }
}
repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka")
    id("maven-publish")
    kotlin("plugin.serialization")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    api(Kotlin.stdlib.jdk8)
    // use -jvm dependencies here because otherwise kts fails to fetch
    api("com.jillesvangurp:search-client-jvm:_")
    api("io.github.microutils:kotlin-logging:_")
    api("ch.qos.logback:logback-classic:_")



    testImplementation(Testing.junit.jupiter.api)
    testImplementation(Testing.junit.jupiter.engine)
    testImplementation(Testing.kotest.assertions.core)
    // bring your own logging, but we need some in tests
    testImplementation("org.slf4j:slf4j-api:_")
    testImplementation("org.slf4j:jcl-over-slf4j:_")
    testImplementation("org.slf4j:log4j-over-slf4j:_")
    testImplementation("org.slf4j:jul-to-slf4j:_")

}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    testLogging.events = setOf(
        org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
    )
}

val artifactName = "kt-search-kts"
val artifactGroup = "com.github.jillesvangurp"

val sourceJar = task("sourceJar", Jar::class) {
    dependsOn(tasks["classes"])
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar = task("javadocJar", Jar::class) {
    from(tasks["dokkaJavadoc"])
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = artifactGroup
            artifactId = artifactName
            pom {
                description.set("Kts extensions for kt-search. Easily script operations for Elasticsearch and Opensearch with .main.kts scripts")
                name.set(artifactId)
                url.set("https://github.com/jillesvangurp/kt-search-kts")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/jillesvangurp/kt-search-kts/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("jillesvangurp")
                        name.set("Jilles van Gurp")
                    }
                }
                scm {
                    url.set("https://github.com/jillesvangurp/kt-search-kts/LICENSE")
                }
            }

            from(components["java"])
            artifact(sourceJar)
            artifact(javadocJar)
        }
    }
}
