plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.client.cio)
    runtimeOnly(libs.slf4j.nop)
}

application {
    mainClass.set("dev.ringalarmwidget.spike.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
