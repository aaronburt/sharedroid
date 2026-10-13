plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    // MinIO integration tests run only when S3_IT_ENDPOINT is set (CI starts a MinIO container).
    environment("S3_IT_ENDPOINT", System.getenv("S3_IT_ENDPOINT") ?: "")
}
