plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ftpembed"
    compileSdk = 34


    buildFeatures {
        // make sure BuildConfig enabled
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    defaultConfig {
        applicationId = "com.ah.ddns"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("int", "DEFAULT_FTP_PORT", "2121")
        buildConfigField("String", "DEFAULT_ROOT_RELATIVE", "\"Pictures/ftptest\"")
        buildConfigField("String", "DDNS_API_BASE", "\"https://mvp.api.alphahalf.cc\"")
        buildConfigField("String", "OIDC_ISSUER", "\"https://mvp.auth.alphahalf.cc/auth/v1/\"")
        buildConfigField("String", "OIDC_CLIENT_ID", "\"ah-mobile\"")
        buildConfigField("String", "OIDC_REDIRECT_SCHEME", "\"com.ah.ddns\"")
        buildConfigField("String", "OIDC_REDIRECT_HOST", "\"oauth2redirect\"")
        buildConfigField("String", "OIDC_REDIRECT_URI", "\"com.ah.ddns:/oauth2redirect\"")
        buildConfigField(
            "String",
            "OIDC_SCOPE",
            "\"openid profile email offline_access dns:records:read dns:records:write\"",
        )

        manifestPlaceholders["appAuthRedirectScheme"] = "com.ah.ddns"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // OIDC auth
    implementation("net.openid:appauth:0.11.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Apache FTP — 1.2.1 fixes FTPSERVER-499 (FtpResponseEncoder thread-safety)
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
    // ftpserver 1.2.1 pulls mina 2.2.x; pin 2.1.3 for minSdk 24 compatibility (DIRMINA-1123)
    implementation("org.apache.mina:mina-core") {
        version { strictly("2.1.3") }
    }

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // HTTP / JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
