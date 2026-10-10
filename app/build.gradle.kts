import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val signingProperties = Properties().apply {
    val path = System.getenv("NAGI_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.config/nagi/release-signing.properties"
    val source = file(path)
    if (source.isFile) source.inputStream().use(::load)
}
android {
    namespace = "com.takeruf.nagi"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.takeruf.nagi"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "APK_UPDATES_ENABLED", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "APK_UPDATES_ENABLED", "false")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }
    signingConfigs {
        create("release") {
            storeFile = signingProperties.getProperty("storeFile")?.let(::file)
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.composables:icons-lucide-android:2.2.1")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
