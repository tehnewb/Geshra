plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.35.0"
}

apply(from = "gradle/android-export.gradle.kts")

group = providers.gradleProperty("libraryGroup").getOrElse("io.github.albertbeaupre")
version = providers.gradleProperty("releaseVersion").getOrElse("1.0.0-SNAPSHOT")

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

dependencies {
    api("org.springframework.boot:spring-boot-starter:3.4.3")
    api("io.netty:netty-codec-http:4.1.108.Final")
    api("com.helger:ph-css:7.0.4")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.4.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Javadoc>().configureEach { options.encoding = "UTF-8" }

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    from("LICENSE") { into("META-INF") }
    from("THIRD_PARTY_NOTICES.md") { into("META-INF") }
}
tasks.test {
    useJUnitPlatform()
    // SourceComplianceTest also reads maintained Android sources outside this Java source set.
    inputs.files(fileTree("android/app/src") { include("**/*.java") })
}

// Benchmark dependencies never enter the published library or its consumer classpath.
val jmh by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[jmh.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[jmh.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
dependencies {
    add(jmh.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
    add(jmh.annotationProcessorConfigurationName, "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs warmed JVM benchmarks with allocation profiling and JSON results."
    classpath = jmh.runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    val smoke = providers.gradleProperty("benchmarkSmoke").isPresent
    val resultPath = providers.gradleProperty("benchmarkOutput").getOrElse("build/reports/benchmarks/jvm.json")
    val include = providers.gradleProperty("benchmarkInclude").getOrElse("geshra.benchmark.*")
    args(include, "-wi", if (smoke) "1" else "3", "-i", if (smoke) "1" else "5",
        "-w", if (smoke) "100ms" else "500ms", "-r", if (smoke) "100ms" else "500ms",
        "-f", if (smoke) "1" else "2", "-foe", "true", "-prof", "gc", "-rf", "json", "-rff", resultPath)
    doFirst { file(resultPath).parentFile?.mkdirs() }
}

val releaseSigning = providers.gradleProperty("releaseSigning").map(String::toBoolean).getOrElse(false)

mavenPublishing {
    coordinates(group.toString(), "geshra", version.toString())
    publishToMavenCentral(automaticRelease = false)
    if (releaseSigning) { signAllPublications() }
    pom {
        name.set("Geshra")
        description.set("Spring Boot library for server-driven web UIs and Netty HTTP/WebSocket networking.")
        inceptionYear.set("2025")
        url.set("https://github.com/tehnewb/Geshra")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("tehnewb")
                name.set("Albert Beaupre")
                url.set("https://github.com/tehnewb")
            }
        }
        scm {
            url.set("https://github.com/tehnewb/Geshra")
            connection.set("scm:git:https://github.com/tehnewb/Geshra.git")
            developerConnection.set("scm:git:ssh://git@github.com/tehnewb/Geshra.git")
        }
    }
}

// Local publication is credential-free; Central uploads require signing.
tasks.matching { it.name == "publishToMavenCentral" || it.name == "publishAndReleaseToMavenCentral" ||
    it.name.startsWith("publish") && it.name.endsWith("ToMavenCentralRepository") }.configureEach {
    doFirst {
        check(releaseSigning) { "Central publishing requires -PreleaseSigning=true and a configured GPG signing key." }
    }
}

publishing {
    repositories {
        maven {
            name = "BuildRepository"
            url = uri(layout.buildDirectory.dir("repository"))
        }
    }
}
