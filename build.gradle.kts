// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Delegate common Android tasks to the :app module to support running from root
tasks.register("assembleDebug") {
    dependsOn(":app:assembleDebug")
}

tasks.register("assembleRelease") {
    dependsOn(":app:assembleRelease")
}

tasks.register("installDebug") {
    dependsOn(":app:installDebug")
}
