plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("com.vaadin") version "25.3.5"
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }
dependencies {
    implementation(platform("com.vaadin:vaadin-bom:25.3.5"))
    implementation("com.vaadin:vaadin-spring-boot-starter")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
}
vaadin {
    productionMode = true
    forceProductionBuild = true
    optimizeBundle = true
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
