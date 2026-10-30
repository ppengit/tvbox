import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val desktopVersion = "1.3.13"

val generateDesktopVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/desktopVersion")
    inputs.property("version", desktopVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("desktop-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$desktopVersion\n")
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            resources.srcDir(generateDesktopVersion)
            dependencies {
                implementation(project(":core"))

                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.coil.compose.multiplatform)
                implementation(libs.coil.network.okhttp)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.mediamp.all)
                implementation(libs.okio)

                runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
                runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.tvbox.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "XiaoJi"
            packageVersion = desktopVersion
            description = "XiaoJi desktop client"
            vendor = "XiaoJi"
            windows {
                menuGroup = "XiaoJi"
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("src/desktopMain/resources/brand/app.ico"))
            }
        }
    }
}
