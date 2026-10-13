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
    // Shared path tokens and the test result type.
    implementation(project(":http"))
    implementation(libs.commons.net)
    // Android provides org.json itself, so it is only needed here to compile and to run the JVM tests.
    compileOnly(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockftpserver)
    testImplementation(libs.org.json)
}
