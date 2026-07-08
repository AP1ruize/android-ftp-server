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

    // Apache FTP
    implementation("org.apache.ftpserver:ftpserver-core:1.1.1")
    implementation("org.apache.mina:mina-core:2.0.16")
}
