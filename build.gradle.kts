import com.avast.gradle.dockercompose.ComposeExtension
import java.net.URI

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
    id("com.avast.gradle.docker-compose")
}

configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    useComposeFiles.set(listOf("docker-compose-es-8.yml"))
    listOf("/usr/bin/docker","/usr/local/bin/docker").firstOrNull {
        File(it).exists()
    }?.let { docker ->
        // works around an issue where the docker
        // command is not found
        // falls back to the default, which may work on
        // some platforms
        dockerExecutable.set(docker)
    }
}

configurations.implementation {
    // exclude these common transitive logging dependencies (slf4j replaces those)
    exclude(group = "commons-logging", module = "commons-logging")
}

dependencies {
    api(Kotlin.stdlib.jdk8)
    // use -jvm dependencies here because otherwise kts fails to fetch
    api("com.jillesvangurp:search-client:_")
    api("com.jillesvangurp:kotlinx-serialization-extensions:_")
    api("ch.qos.logback:logback-classic:_")
    api(KotlinX.coroutines.slf4j)
    api("io.github.oshai:kotlin-logging:_")

    api(Ktor.client.logging)
    api(Ktor.client.cio)
    api(Ktor.client.java)
    api(Ktor.client.logging)

    api(Ktor.client.core)
    api(Ktor.client.auth)
    api(Ktor.client.serialization)

    testImplementation(kotlin("test"))
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
    val isUp = try {
        URI("http://localhost:9999").toURL().openConnection().connect()
        true
    } catch (e: Exception) {
        false
    }
    if (!isUp) {
        dependsOn(
            "composeUp"
        )
    }
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    testLogging.events = setOf(
        org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
    )
    if (!isUp) {
        this.finalizedBy("composeDown")
    }
}

val artifactName = rootProject.name
val artifactGroup = "com.jillesvangurp"

val sourceJar = task("sourceJar", Jar::class) {
    dependsOn(tasks["classes"])
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar = task("javadocJar", Jar::class) {
    from(tasks["dokkaJavadoc"])
    archiveClassifier.set("javadoc")
}


tasks.register("versionCheck") {
    doLast {
        if(rootProject.version == "unspecified") {
            error("call with -Pversion=x.y.z to set a version and make sure it lines up with the current tag")
        }
    }
}

tasks.withType<PublishToMavenRepository> {
    dependsOn("versionCheck")
}

publishing {
    repositories {
        maven {
            // GOOGLE_APPLICATION_CREDENTIALS env var must be set for this to work
            // public repository is at https://maven.tryformation.com/releases
            url = uri("gcs://mvn-public-tryformation/releases")
            name = "FormationPublic"
        }
    }

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
