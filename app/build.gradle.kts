plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：CI 通过环境变量注入，缺省回落到仓库内固定密钥，保证每次发布签名一致
// 注意：环境变量存在但为空串时也要回落，避免 CI 未配置 Secret 时把口令覆盖成空
val releaseStoreFile = file(
    System.getenv("SIGN_STORE_FILE")?.takeIf { it.isNotBlank() }
        ?: "${rootProject.projectDir}/keystore/zhiwei-release.jks"
)
val releaseStorePassword = System.getenv("SIGN_STORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "zhiwei2026"
val releaseKeyAlias = System.getenv("SIGN_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "zhiwei"
val releaseKeyPassword = System.getenv("SIGN_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "zhiwei2026"

android {
    namespace = "com.zhiwei.store"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhiwei.store"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile.exists()) {
                // 仓库内固定密钥由 tools/make_keystore.py 生成，格式为 PKCS12
                storeType = "PKCS12"
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 仅在本机 ARM64（proot/Ubuntu 调试环境）强制使用 arm64 版 AAPT2；
// CI 的 x86_64 runner 必须用官方二进制，否则会 exec format error。
val isArm64Host = System.getProperty("os.arch")?.lowercase()
    ?.let { it.contains("aarch64") || it.contains("arm64") } == true
if (isArm64Host) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
