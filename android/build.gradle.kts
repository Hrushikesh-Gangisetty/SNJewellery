// Root build file. Plugins are declared here without being applied, so
// every module resolves the same version from the catalogue.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
