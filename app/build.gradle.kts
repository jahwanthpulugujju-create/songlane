import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.songlane"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.songlane"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("exportSbom") {
    group = "reporting"
    description = "Export resolved debug runtime artifact identities, hashes and license evidence."
    doLast {
        val artifacts = configurations.getByName("debugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val components = artifacts.map { artifact ->
            val id = artifact.moduleVersion.id
            val cacheDir = gradle.gradleUserHomeDir.resolve("caches/modules-2/files-2.1/${id.group}/${id.name}/${id.version}")
            val pom = cacheDir.walkTopDown().firstOrNull { it.extension == "pom" }?.readText().orEmpty()
            val section = Regex("<licenses>([\\s\\S]*?)</licenses>").find(pom)?.groupValues?.get(1).orEmpty()
            val names = Regex("<name>(.*?)</name>").findAll(section).map { it.groupValues[1] }.toList()
            val hash = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes()).joinToString("") { "%02x".format(it) }
            mutableMapOf<String, Any>("type" to "library", "group" to id.group, "name" to id.name, "version" to id.version,
                "purl" to "pkg:maven/${id.group}/${id.name}@${id.version}", "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to hash)),
                "properties" to listOf(mapOf("name" to "songlane:license-evidence", "value" to if(names.isEmpty()) "Unverified: no direct license field in cached POM" else "Cached Maven POM")))
                .apply { if(names.isNotEmpty()) put("licenses", names.map { mapOf("license" to mapOf("name" to it)) }) }
        }.distinctBy { "${it["group"]}:${it["name"]}:${it["version"]}" }.sortedBy { "${it["group"]}:${it["name"]}" }
        val result = mapOf("bomFormat" to "CycloneDX", "specVersion" to "1.5", "version" to 1, "metadata" to mapOf("component" to mapOf("type" to "application", "name" to "songlane", "version" to "0.1.0")), "components" to components)
        rootProject.file("docs/sbom.cdx.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(result)))
        println("Wrote ${components.size} resolved runtime components to docs/sbom.cdx.json")
    }
}
