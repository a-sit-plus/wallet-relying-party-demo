import at.asitplus.gradle.napier
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "3.4.7"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
}

group = "at.asitplus"
version = "1.4.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.g0dkar:qrcode-kotlin:4.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation(napier())
    implementation("at.asitplus.wallet:vck-openid:5.7.1")
    /** Include supported credentials for VC-K, see https://github.com/a-sit-plus/credentials-collection */
    implementation("at.asitplus.wallet:eupidcredential:3.1.0")
    implementation("at.asitplus.wallet:mobiledrivinglicence:1.2.0")
    implementation("at.asitplus.wallet:powerofrepresentation:1.2.0")
    implementation("at.asitplus.wallet:certificateofresidence:2.1.2")
    implementation("at.asitplus.wallet:healthid:2.0.0")
    implementation("at.asitplus.wallet:company-registration:1.1.0")
    implementation("at.asitplus.wallet:taxid:1.0.1")
}

springBoot {
    buildInfo()
}

tasks.getByName<BootJar>("bootJar") {
    this.launchScript()
}

repositories {
    mavenCentral()
}
