import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        val jvmMain by getting {
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
            packageVersion = "1.1.0"
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
