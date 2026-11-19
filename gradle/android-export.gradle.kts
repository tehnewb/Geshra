import java.net.URI
import org.gradle.api.tasks.bundling.Zip

/**
 * Public endpoint configuration is intentionally separate from native application source.
 */
val androidExportConfiguration = layout.buildDirectory.file("android-export/export.properties")

/**
 * Encodes an ISO-8859-1 Java properties value without source/XML/Gradle interpolation.
 */
fun escapeAndroidProperty(value: String): String {
    val escaped = StringBuilder(value.length)
    for (character in value) {
        when (character) {
            '\\' -> escaped.append("\\\\")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            '=', ':', '#', '!', ' ' -> escaped.append('\\').append(character)
            else -> if (character.code < 32 || character.code > 126) escaped.append("\\u%04x".format(character.code)) else escaped.append(character)
        }
    }
    return escaped.toString()
}

/**
 * Exports only the explicit public application settings; no signing/environment credentials.
 */
val androidExportSettings = listOf("androidServerUrl", "androidDebugServerUrl", "androidApplicationId", "androidAppName", "androidVersionCode", "androidVersionName")

/**
 * Deterministically writes user-selected public settings, requiring a real configured HTTPS URL.
 */
val generateAndroidExportConfiguration = tasks.register("generateAndroidExportConfiguration") {
    group = "distribution"
    description = "Validates and writes public Android export configuration."
    androidExportSettings.forEach { key -> inputs.property(key, providers.gradleProperty(key).orElse("")) }
    outputs.file(androidExportConfiguration)
    doLast {
        val endpoint = providers.gradleProperty("androidServerUrl").orNull
            ?: throw GradleException("Set -PandroidServerUrl=https://your-server.example/ before exporting Android.")
        val uri = URI(endpoint)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null && !uri.isOpaque && uri.port in -1..65535 && uri.port != 0) { "androidServerUrl must be an absolute HTTPS URL without credentials or a fragment." }
        providers.gradleProperty("androidDebugServerUrl").orNull?.let { development ->
            val debugUri = URI(development)
            require((debugUri.scheme.equals("https", ignoreCase = true) || debugUri.scheme.equals("http", ignoreCase = true)) && debugUri.host != null && debugUri.rawUserInfo == null && debugUri.rawFragment == null && !debugUri.isOpaque && debugUri.port in -1..65535 && debugUri.port != 0) { "androidDebugServerUrl must be an absolute HTTP/HTTPS URL without credentials or a fragment." }
        }
        providers.gradleProperty("androidApplicationId").orNull?.let { require(it.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) { "Invalid Android application ID." } }
        providers.gradleProperty("androidAppName").orNull?.let { require(it.isNotBlank() && it.length <= 80 && it.none { character -> character.code < 32 }) { "Invalid Android app name." } }
        providers.gradleProperty("androidVersionCode").orNull?.let { require(it.toIntOrNull() in 1..2100000000) { "Invalid Android version code." } }
        providers.gradleProperty("androidVersionName").orNull?.let { require(it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._+ -]{0,63}"))) { "Invalid Android version name." } }
        val propertiesFile = androidExportConfiguration.get().asFile
        propertiesFile.parentFile.mkdirs()
        propertiesFile.writeText(androidExportSettings.mapNotNull { key -> providers.gradleProperty(key).orNull?.let { "$key=${escapeAndroidProperty(it)}\n" } }.joinToString(""), Charsets.US_ASCII)
    }
}

tasks.register<Zip>("exportAndroid") {
    group = "distribution"
    description = "Exports a standalone configurable Android Studio project with APK/AAB builds."
    dependsOn(generateAndroidExportConfiguration)
    archiveFileName.set("geshra-android.zip")
    destinationDirectory.set(layout.buildDirectory.dir("exports"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.projectDirectory.dir("android")) {
        include("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "README.md", "TESTING.md", "app/build.gradle.kts", "app/proguard-rules.pro")
        include("app/src/*/AndroidManifest.xml", "app/src/*/java/**/*.java", "app/src/*/res/**/*.xml")
    }
    from("gradlew") { filePermissions { unix("rwxr-xr-x") } }
    from("gradlew.bat")
    from("gradle/wrapper") { into("gradle/wrapper") }
    from("LICENSE")
    from(androidExportConfiguration)
}
