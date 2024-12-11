import at.asitplus.gradle.napier
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "3.2.10"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
}

group = "at.asitplus"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.g0dkar:qrcode-kotlin:4.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    implementation(napier())
    implementation("at.asitplus.wallet:vck-openid:5.1.0")
    implementation("at.asitplus.wallet:idacredential:3.9.1")
    implementation("at.asitplus.wallet:eupidcredential:2.2.1")
    implementation("at.asitplus.wallet:mobiledrivinglicence:1.1.1")
    implementation("at.asitplus.wallet:powerofrepresentation:1.1.0")
    implementation("at.asitplus.wallet:certificateofresidence:2.1.0")
    implementation("at.asitplus.wallet:eprescription:1.1.0")
}

springBoot {
    buildInfo()
}

tasks.getByName<BootJar>("bootJar") {
    this.launchScript()
}

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}