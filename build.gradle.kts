import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)

    // Add Lucene dependency with version 7.1.0
    implementation("org.apache.lucene:lucene-core:7.1.0")  // core Lucene library
    implementation("org.apache.lucene:lucene-analyzers-common:7.1.0")  // for standard analyzers
    implementation("org.apache.lucene:lucene-queryparser:7.1.0")  // for query parsing

    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    baseline = file("config/detekt/baseline.xml")
}

tasks.register<JavaExec>("benchmarkIndexing") {
    group = "verification"
    description = "One-off benchmark comparing single-threaded vs fork-join parallel indexing over a synthetic file tree."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.example.fastfinder.index.IndexingBenchmarkKt")
}


compose.desktop {
    application {
        mainClass = "org.example.fastfinder.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "FastFinder"
            packageVersion = "1.0.0"
        }
    }
}
