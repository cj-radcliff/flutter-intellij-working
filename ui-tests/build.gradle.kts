plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

dependencies {
    implementation(project(":"))
    testImplementation("junit:junit:4.13.2")
}
