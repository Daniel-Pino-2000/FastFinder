package org.example.fastfinder

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the fix for a real regression: launching from an IDE or `gradlew run` goes through
 * java.exe/javaw.exe with a classpath that's sometimes a temporary file the IDE deletes once
 * this process exits, so relaunching that faithfully as Administrator isn't reliable - a
 * relaunch that spawns and immediately dies looks identical to success from here, and the
 * original process would already have exited, leaving no window open at all. Elevated relaunch
 * is only attempted for the packaged native launcher, which this check identifies by name.
 */
class ElevationTest {

    @Test
    fun `java and javaw are recognized regardless of path or case`() {
        assertTrue(isJavaLauncherExecutable("""C:\Program Files\Java\jdk-21\bin\java.exe"""))
        assertTrue(isJavaLauncherExecutable("""C:\Program Files\Java\jdk-21\bin\javaw.exe"""))
        assertTrue(isJavaLauncherExecutable("""C:\jdk\bin\JAVA.EXE"""))
    }

    @Test
    fun `the packaged native launcher is not mistaken for a java launcher`() {
        assertFalse(isJavaLauncherExecutable("""C:\Program Files\FastFinder\FastFinder.exe"""))
    }
}
