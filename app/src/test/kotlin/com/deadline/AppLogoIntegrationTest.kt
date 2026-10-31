package com.deadline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLogoIntegrationTest {

    private fun getResFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/res", relativePath),
            File("app/src/main/res", relativePath),
            File("/storage/emulated/0/AndroidIDEProjects/deadline/app/src/main/res", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/")),
            File("/storage/emulated/0/AndroidIDEProjects/deadline", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testLauncherIconAssets_existAcrossAllDensities() {
        val densities = listOf(
            "mipmap-mdpi",
            "mipmap-hdpi",
            "mipmap-xhdpi",
            "mipmap-xxhdpi",
            "mipmap-xxxhdpi"
        )

        for (density in densities) {
            val launcherPng = getResFile("$density/ic_launcher.png")
            val launcherRoundPng = getResFile("$density/ic_launcher_round.png")
            val foregroundPng = getResFile("$density/ic_launcher_foreground.png")

            assertTrue("$density must contain ic_launcher.png", launcherPng.exists())
            assertTrue("$density ic_launcher.png must be non-empty", launcherPng.length() > 0)

            assertTrue("$density must contain ic_launcher_round.png", launcherRoundPng.exists())
            assertTrue("$density ic_launcher_round.png must be non-empty", launcherRoundPng.length() > 0)

            assertTrue("$density must contain ic_launcher_foreground.png", foregroundPng.exists())
            assertTrue("$density ic_launcher_foreground.png must be non-empty", foregroundPng.length() > 0)
        }
    }

    @Test
    fun testAdaptiveIconXml_configuredCorrectly() {
        val icLauncherXml = getResFile("mipmap-anydpi-v26/ic_launcher.xml")
        val icLauncherRoundXml = getResFile("mipmap-anydpi-v26/ic_launcher_round.xml")
        val backgroundXml = getResFile("drawable/ic_launcher_background.xml")

        assertTrue("ic_launcher.xml must exist", icLauncherXml.exists())
        assertTrue("ic_launcher_round.xml must exist", icLauncherRoundXml.exists())
        assertTrue("ic_launcher_background.xml must exist", backgroundXml.exists())

        val launcherContent = icLauncherXml.readText()
        assertTrue("ic_launcher.xml must reference background drawable", launcherContent.contains("@drawable/ic_launcher_background"))
        assertTrue("ic_launcher.xml must reference foreground mipmap", launcherContent.contains("@mipmap/ic_launcher_foreground"))

        val roundContent = icLauncherRoundXml.readText()
        assertTrue("ic_launcher_round.xml must reference background drawable", roundContent.contains("@drawable/ic_launcher_background"))
        assertTrue("ic_launcher_round.xml must reference foreground mipmap", roundContent.contains("@mipmap/ic_launcher_foreground"))
    }

    @Test
    fun testManifest_configuredCleanlyWithoutMascotAliases() {
        val manifestFile = getSourceFile("app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()

        assertTrue("Manifest must configure MainActivity with MAIN action", content.contains("android.intent.action.MAIN"))
        assertTrue("Manifest must configure MainActivity with LAUNCHER category", content.contains("android.intent.category.LAUNCHER"))
        assertTrue("Application must use @mipmap/ic_launcher", content.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue("Application must use @mipmap/ic_launcher_round", content.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))

        // Guarantee mascot and aliases are removed
        assertFalse("Must not declare LauncherIconNormal alias", content.contains("LauncherIconNormal"))
        assertFalse("Must not declare LauncherIconUrgent alias", content.contains("LauncherIconUrgent"))
        assertFalse("Must not declare LauncherIconAngry alias", content.contains("LauncherIconAngry"))
        assertFalse("Must not declare LauncherIconCrying alias", content.contains("LauncherIconCrying"))
        assertFalse("Must not declare MascotAlarmActivity", content.contains("MascotAlarmActivity"))
    }

    @Test
    fun testMascotDrawables_removed() {
        val mascotCutout = getResFile("drawable/mascot_cutout.png")
        val mascotPng = getResFile("drawable/mascot.png")
        val mascotSilhouette = getResFile("drawable/ic_mascot_silhouette.xml")

        assertFalse("mascot_cutout.png must be deleted", mascotCutout.exists())
        assertFalse("mascot.png must be deleted", mascotPng.exists())
        assertFalse("ic_mascot_silhouette.xml must be deleted", mascotSilhouette.exists())
    }
}
