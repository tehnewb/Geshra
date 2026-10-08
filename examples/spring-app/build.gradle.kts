plugins {
    java
    id("org.springframework.boot") version "3.4.3"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

repositories {
    if (providers.gradleProperty("useMavenLocal").getOrElse("false").toBoolean()) mavenLocal()
    if (providers.gradleProperty("useBuildRepository").getOrElse("false").toBoolean()) {
        maven { url = uri("../../build/repository") }
    }
    mavenCentral()
}

dependencies {
    val libraryGroup = providers.gradleProperty("libraryGroup").getOrElse("io.github.albertbeaupre")
    val libraryVersion = providers.gradleProperty("releaseVersion").getOrElse("1.0.0-SNAPSHOT")
    implementation("$libraryGroup:geshra:$libraryVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test { useJUnitPlatform() }
