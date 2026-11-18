plugins {
    java
    id("org.springframework.boot") version "3.4.3"
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories {
    maven { url = uri("../../build/repository") }
    mavenCentral()
}
dependencies { implementation("io.github.albertbeaupre:geshra:1.0.0-SNAPSHOT") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
