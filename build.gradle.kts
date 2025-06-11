import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
    id("java")
    id("application")
    kotlin("jvm") version "1.9.0"
}

group = "edu.kit.kastel.logic"
version = "1.0-SNAPSHOT"

application {
    // 指定主类全名，使用 Kotlin DSL 的 Property API
    mainClass.set("edu.kit.kastel.vads.compiler.Main")
}
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "edu.kit.kastel.vads.compiler.Main"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
