// webApp 模块 build.gradle.kts —— 纯 JS / Wasm 目标，不含 androidTarget()，不需要 AGP
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            // 直接以字符串坐标写依赖（新版本已弃用 compose.runtime 等便捷变量）
            implementation("org.jetbrains.compose.runtime:runtime:1.8.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.8.2")
            implementation("org.jetbrains.compose.material3:material3:1.8.2")
            implementation("org.jetbrains.compose.ui:ui:1.8.2")
            implementation("org.jetbrains.compose.components:components-resources:1.8.2")
        }
    }
}
