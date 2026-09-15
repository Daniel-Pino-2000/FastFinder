import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = "org.example"
version = "1.0.5"

// Pins the JDK actually used to compile/run this build to 21, independent of whatever JDK
// happens to be selected as the IDE's Project SDK - without this, pointing the IDE at a newer
// JDK (e.g. 22+) breaks the build outright ("Kotlin does not yet support 24 JDK target" /
// "Inconsistent JVM-target compatibility detected for tasks 'compileJava' and 'compileKotlin'"),
// which looks like a code regression but is really just an unpinned toolchain. Requires a JDK
// 21 already installed and discoverable by Gradle (matching README's "Requires JDK 21" and CI);
// this doesn't auto-download one.
kotlin {
    jvmToolchain(21)
}

// jvmToolchain above only pins what compiles the code - a JavaExec task (Compose Desktop's own
// `run`, and benchmarkIndexing below) otherwise executes using whichever JVM happens to be
// running the Gradle daemon itself, which is a *different* JVM selection entirely (IntelliJ's
// "Gradle JVM" setting, often left as "Project SDK"). A daemon running under an older JVM (e.g.
// JBR 17) then tries to run these JDK-21-targeted class files and fails with "LinkageError...
// UnsupportedClassVersionError" even though compilation itself succeeded. Forcing every
// JavaExec's launcher through the toolchain service closes that gap: whatever JVM the daemon
// itself runs under, the actual `java` process these tasks launch is always JDK 21.
//
// Wrapped in afterEvaluate and set on `executable` (not `javaLauncher` - conflicts with it:
// "Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
// because the Compose Desktop plugin configures the `run` task's `executable` itself, in its own
// afterEvaluate registered when the plugin is applied (before this script's body runs) - setting
// this eagerly here gets silently clobbered by that later. A script-level afterEvaluate runs
// after ones registered during plugin application, so this one wins instead.
afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        executable = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .get().executablePath.asFile.absolutePath
    }
}

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

    // Win32 interop for reading the NTFS USN Journal (startup catch-up for filesystem
    // changes made while the app was closed) - the JDK has no API for this.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

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
            // Windows-only app (Explorer integration, Windows-specific restricted-directory
            // handling, elevation, the NTFS USN journal) - Dmg/Deb would be dead config at best.
            targetFormats(TargetFormat.Msi)
            packageName = "FastFinder"
            // Reads from the project version above instead of its own literal, so a release
            // only ever needs bumping in one place - the two used to drift by being separate
            // hardcoded strings.
            packageVersion = project.version.toString()
            description = "Instant, filterable full-text search over local files and folders."
            vendor = "Daniel Pino"

            // The bundled installer's runtime is a custom, stripped-down JVM image (via jlink),
            // built from the modules Compose Desktop's dependency scan detects as needed - which
            // missed jdk.unsupported, where com.sun.nio.file.ExtendedWatchEventModifier
            // (DBManager's live filesystem watcher) actually lives. That module's absence only
            // ever breaks the *installed* app (java.lang.NoClassDefFoundError on launch) -
            // `./gradlew run` always uses the full system JDK, which has every module, so this
            // never showed up there. Listed explicitly so jlink always includes it regardless of
            // whether auto-detection catches this particular usage.
            modules("jdk.unsupported")

            windows {
                // Fixed for the life of the app: WiX uses this to recognize a new installer as an
                // upgrade of an existing install rather than a separate side-by-side one. Once a
                // version ships without a pinned value here, it can't be safely added
                // retroactively - each build would otherwise get its own random one.
                upgradeUuid = "27C964BF-314F-4620-AF24-9A1C863CF536"
                iconFile.set(project.file("icons/app.ico"))
                // Off by default in jpackage/Compose Desktop - without these the installer runs
                // to completion having copied the app's files somewhere, but leaves no Start
                // Menu entry, no desktop icon, and nothing that launches it, so it looks like
                // installing did nothing at all.
                menu = true
                menuGroup = "FastFinder"
                shortcut = true
            }
        }
    }
}
