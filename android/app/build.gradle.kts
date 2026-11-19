import java.net.URI
import java.util.Properties

plugins { id("com.android.application") }

/**
 * Public export configuration; signing credentials never belong in this file.
 */
val exportConfiguration = Properties().apply {
    val configurationFile = rootProject.file("export.properties")
    if (configurationFile.isFile) configurationFile.inputStream().use { load(it) }
}

/**
 * Reads command-line overrides before the exported application defaults.
 */
fun configuration(name: String, fallback: String): String = providers.gradleProperty(name).orNull ?: exportConfiguration.getProperty(name, fallback)

/**
 * Encodes public configuration as a Java literal without allowing generated-source injection.
 */
fun javaLiteral(value: String): String = "\"" + value.flatMap { character ->
    when (character) {
        '\\' -> "\\\\".toList()
        '"' -> "\\\"".toList()
        else -> if (character.code < 32 || character.code > 126) "\\u%04x".format(character.code).toList() else listOf(character)
    }
}.joinToString("") + "\""

/**
 * Preserves literal launcher-label punctuation rather than treating it as Android resource syntax.
 */
fun resourceLiteral(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'") + "\""

/**
 * Requires an unambiguous HTTP origin and excludes credentials and non-network URL schemes.
 */
fun serverAddress(value: String, development: Boolean): String {
    val address = URI(value)
    require(!address.isOpaque && address.host != null && address.rawUserInfo == null && address.rawFragment == null && address.port in -1..65535 && address.port != 0) { "Invalid Android server URL." }
    require(address.scheme.equals("https", ignoreCase = true) || development && address.scheme.equals("http", ignoreCase = true)) { "Release server URLs must use HTTPS." }
    return address.toASCIIString()
}

/**
 * Production endpoint is independent of the optional debug-only development override.
 */
val productionAddress = serverAddress(configuration("androidServerUrl", "https://example.invalid/"), false)
/**
 * Local HTTP is permitted only by the debug manifest and the debug build constant.
 */
val developmentAddress = serverAddress(configuration("androidDebugServerUrl", productionAddress), true)
/**
 * Android package identity does not change the reusable host's Java namespace.
 */
val exportedApplicationId = configuration("androidApplicationId", "geshra.android.example")
/**
 * Display label is supplied as a resource value rather than interpolated XML.
 */
val exportedApplicationName = configuration("androidAppName", "Geshra")

require(exportedApplicationId.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) { "androidApplicationId must be a lowercase dotted Android application ID." }
require(exportedApplicationName.isNotBlank() && exportedApplicationName.length <= 80 && exportedApplicationName.none { it.code < 32 }) { "androidAppName must be 1–80 printable characters." }

android {
    namespace = "geshra.android"
    compileSdk = 36
    defaultConfig {
        applicationId = exportedApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = configuration("androidVersionCode", "1").toInt().also { require(it in 1..2100000000) }
        versionName = configuration("androidVersionName", "1.0.0").also { require(it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._+ -]{0,63}"))) { "androidVersionName must contain 1–64 version-label characters." } }
        resValue("string", "app_name", resourceLiteral(exportedApplicationName))
        manifestPlaceholders["geshraServerHost"] = URI(productionAddress).host
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { buildConfigField("String", "SERVER_URL", javaLiteral(developmentAddress)) }
        release {
            buildConfigField("String", "SERVER_URL", javaLiteral(productionAddress))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    signingConfigs {
        val keyStore = providers.environmentVariable("GESHRA_ANDROID_KEYSTORE").orNull
        val alias = providers.environmentVariable("GESHRA_ANDROID_KEY_ALIAS").orNull
        val storeSecret = providers.environmentVariable("GESHRA_ANDROID_STORE_PASSWORD").orNull
        val keySecret = providers.environmentVariable("GESHRA_ANDROID_KEY_PASSWORD").orNull
        require(listOf(keyStore, alias, storeSecret, keySecret).all { it == null } || listOf(keyStore, alias, storeSecret, keySecret).all { !it.isNullOrEmpty() }) { "Set all four GESHRA_ANDROID signing variables or leave all unset." }
        if (keyStore != null && alias != null && storeSecret != null && keySecret != null) {
            create("exportRelease") {
                storeFile = file(keyStore)
                storePassword = storeSecret
                keyAlias = alias
                keyPassword = keySecret
            }
            buildTypes.getByName("release").signingConfig = getByName("exportRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = false }
    lint { abortOnError = true; checkReleaseBuilds = true }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { require(URI(productionAddress).host != "example.invalid") { "Set your public androidServerUrl before building release artifacts." } }
}

dependencies {
    implementation("androidx.activity:activity:1.11.0")
    implementation("androidx.core:core:1.17.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
