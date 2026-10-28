plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}
val localModel = providers.gradleProperty("localModel").orNull != "false"
val phoneCpuArch = providers.gradleProperty("phoneCpuArch").orNull ?: "armv8-a"
require(phoneCpuArch in setOf("armv8-a", "armv8.2-a+dotprod+fp16+i8mm"))
fun targetPackage(name: String): String {
    val value = providers.gradleProperty("${name}Package").orNull ?: mapOf("kimi" to "com.moonshot.kimichat", "doubao" to "com.larus.nova", "deepseek" to "com.deepseek.chat").getValue(name)
    require(value.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")))
    return value
}
android {
    namespace = "org.phoneai.teamwork"
    compileSdk = 36
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "org.phoneai.teamwork"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0-poc"
        for (name in listOf("kimi", "doubao", "deepseek")) {
            val pkg = targetPackage(name)
            manifestPlaceholders["${name}Package"] = pkg
            buildConfigField("String", "${name.uppercase()}_PACKAGE", "\"$pkg\"")
        }
        buildConfigField("boolean", "LOCAL_MODEL", localModel.toString())
        buildConfigField("String", "CPU_ARCH", "\"$phoneCpuArch\"")
        ndk { abiFilters += "arm64-v8a" }
        if (localModel) externalNativeBuild { cmake { cppFlags += "-std=c++17"; arguments += listOf("-DANDROID_STL=c++_shared", "-DGGML_CPU_ARM_ARCH=$phoneCpuArch") } }
    }
    buildFeatures { buildConfig = true }
    if (localModel) externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.room:room-runtime:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}
// Build verification only: fail if a dependency reintroduces network or broad package access.
tasks.register("verifyReleasePermissions") {
    dependsOn("processReleaseMainManifest")
    doLast {
        val manifests = fileTree(layout.buildDirectory.dir("intermediates/merged_manifest/release")) { include("**/AndroidManifest.xml") }.files
        check(manifests.isNotEmpty()) { "No merged release manifest" }
        manifests.forEach {
            val xml = it.readText()
            check(!Regex("<uses-permission[^>]+android.permission.(INTERNET|QUERY_ALL_PACKAGES)").containsMatchIn(xml)) { "Forbidden permission in ${it.path}" }
        }
    }
}
