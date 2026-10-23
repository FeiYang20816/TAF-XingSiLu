plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "cn.xingsilu.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.xingsilu.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    val localDebugKey = rootProject.file(".local/debug.keystore")
    if (localDebugKey.isFile) signingConfigs.getByName("debug") { storeFile = localDebugKey }
    val releaseSigningVariables = listOf(
        "XINGSILU_RELEASE_STORE_FILE", "XINGSILU_RELEASE_STORE_PASSWORD",
        "XINGSILU_RELEASE_KEY_ALIAS", "XINGSILU_RELEASE_KEY_PASSWORD"
    )
    val releaseSigningValues = releaseSigningVariables.map { providers.environmentVariable(it).orNull }
    val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
    require(releaseSigningValues.all { it == null } || hasReleaseSigning) {
        "Release signing requires all four XINGSILU_RELEASE_* environment variables."
    }
    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseSigningValues[0]!!)
            storePassword = releaseSigningValues[1]
            keyAlias = releaseSigningValues[2]
            keyPassword = releaseSigningValues[3]
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES") }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24")
    implementation("com.squareup.okio:okio:3.9.0")
    implementation("androidx.tracing:tracing:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    val testTemp = rootProject.file(".local/test-temp").apply { mkdirs() }
    systemProperty("java.io.tmpdir", testTemp.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    val runtimeDirectory = System.getenv("XINGSILU_ROBOLECTRIC_DIR")
    if (runtimeDirectory != null) {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", runtimeDirectory)
    }
    systemProperty("xingsilu.screenshots", rootProject.file(".local/screenshots").absolutePath)
}
