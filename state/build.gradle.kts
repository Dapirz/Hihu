plugins {
    alias(libs.plugins.android.library)

}

android {
    namespace = "com.daffa.hihu.state"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":music"))
    implementation(project(":storage"))
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.coroutines.android)
}
