package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Version 11: the Schedule toolbar's Calendar button starts hidden, on an update as on a fresh install.
 *
 * Releases up to v26.11.161 wrote schema 9, where `hiddenScheduleButtons` defaulted to empty and no
 * Calendar button existed. That empty list is explicit in the file, so the new default alone cannot
 * reach it: without the migration every updated install would open with a button nobody asked for.
 *
 * The other half matters as much: the step must add and never replace, and must not run again on a
 * current file — an operator who turned the button on has an empty list that is a choice.
 */
class CalendarButtonMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-calendar-button-migration-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    private fun documentWith(version: Int, hidden: String) = """
        {
          "settingsVersion": $version,
          "hiddenScheduleButtons": $hidden
        }
    """.trimIndent()

    @Test
    fun `a schema 9 file upgrades through schema 26 with Calendar visible and sync off`() {
        val settings = decode(documentWith(version = 9, hidden = "[]"))
        assertEquals(emptySet(), settings.hiddenScheduleButtons)
        // Nothing in the upgrade may switch phone sync on or pair with the relay on the user's behalf.
        assertFalse(settings.calendarSync.enabled)
        assertFalse(settings.calendarSync.isPaired)
    }

    @Test
    fun `a file with no version at all is migrated cleanly`() {
        val settings = decode("""{"hiddenScheduleButtons": []}""")
        assertFalse(CALENDAR in settings.hiddenScheduleButtons)
    }

    @Test
    fun `buttons the operator had already hidden stay hidden while Calendar becomes visible`() {
        val settings = decode(documentWith(version = 9, hidden = """["UNDO","REDO"]"""))
        assertEquals(setOf("UNDO", "REDO"), settings.hiddenScheduleButtons)
    }

    @Test
    fun `a schema 25 file with Calendar hidden reveals it once on upgrade to schema 26`() {
        val settings = decode(documentWith(version = 25, hidden = """["CALENDAR","ZOOM"]"""))
        assertEquals(setOf("ZOOM"), settings.hiddenScheduleButtons)
    }

    @Test
    fun `a current file where the user chose to hide Calendar is left alone`() {
        val settings = decode(
            documentWith(version = AppSettings.CURRENT_SETTINGS_VERSION, hidden = """["CALENDAR"]"""),
        )
        assertTrue(CALENDAR in settings.hiddenScheduleButtons)
    }

    @Test
    fun `a fresh install starts with the button visible and phone sync off`() {
        val settings = AppSettings()
        assertFalse(CALENDAR in settings.hiddenScheduleButtons)
        assertFalse(settings.calendarSync.enabled)
    }

    private companion object {
        const val CALENDAR = "CALENDAR"
    }
}
