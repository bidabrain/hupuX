import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val appVersion: String by project

val generateDesktopBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/kotlin")
    outputs.dir(outputDir)
    inputs.property("appVersion", appVersion)
    doFirst {
        val dir = outputDir.get().asFile.resolve("com/hupux/desktop")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            "package com.hupux.desktop\n\nobject BuildConfig {\n    const val VERSION_NAME = \"$appVersion\"\n}\n"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateDesktopBuildConfig)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        val jvmMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.coil3.compose)
                implementation(libs.coil3.network.okhttp)
                implementation(libs.koin.core)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.okhttp)
                implementation(libs.jsoup)
                implementation(libs.gson)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.hupux.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "HupuX"
            packageVersion = appVersion
            macOS {
                dockName = "HupuX"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
            modules("java.sql", "java.naming", "jdk.unsupported")
        }
    }
}
