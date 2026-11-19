plugins {
    alias(libs.plugins.android.library)

}

android {
    namespace = "com.daffa.hihu.music.local"
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
    implementation(libs.coroutines.android)
}
