plugins {
    id("java-library")
    id("maven-publish")
    id("com.diffplug.spotless") version "6.12.1"
    id("pmd")
    id("io.freefair.lombok") version "6.6.1"
}

group = "eu.darkbot.verifier"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly("eu.darkbot:DarkBot:1.131")
    compileOnly("org.jetbrains:annotations:24.1.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.jar {
    manifest {
        attributes["Implementation-Title"] = "Verifier"
    }
}

tasks.register<Exec>("signJar") {
    dependsOn(tasks.jar)

    val jarFile = tasks.jar.get().archiveFile.get().asFile

    commandLine("jarsigner",
        "-keystore", "mykeystore.jks",
        "-storepass", "808a1a23",
        "-keypass", "808a1a23",
        jarFile.absolutePath,
        "mykey"
    )
}