package iondrive.nop

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SettingsTest {
    private val originalRoot: Path = Settings.configRoot

    @AfterEach
    fun restoreRoot() {
        Settings.configRoot = originalRoot
    }

    @Test
    fun `loadOpenProjects returns empty when no state file exists`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        assertTrue(Settings.loadOpenProjects().isEmpty())
    }

    @Test
    fun `save then load round-trips a single open project`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        val project = tmp.resolve("some/project").also { Files.createDirectories(it) }
        Settings.saveOpenProjects(listOf(project))

        val loaded = Settings.loadOpenProjects()
        assertEquals(listOf(project.toAbsolutePath().normalize()), loaded)
    }

    @Test
    fun `save then load preserves order across multiple open projects`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        val a = tmp.resolve("a").also { Files.createDirectories(it) }
        val b = tmp.resolve("b").also { Files.createDirectories(it) }
        val c = tmp.resolve("c").also { Files.createDirectories(it) }
        Settings.saveOpenProjects(listOf(a, b, c))

        assertEquals(
            listOf(a, b, c).map { it.toAbsolutePath().normalize() },
            Settings.loadOpenProjects(),
        )
    }

    @Test
    fun `loadOpenProjects tolerates a blank state file`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        val state = tmp.resolve("nop/state").also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "\n   \n")
        }
        assertTrue(Settings.loadOpenProjects().isEmpty(), "blank state should produce empty list — got file at $state")
    }

    @Test
    fun `legacy single-line state still resolves the project`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        val state = tmp.resolve("nop/state").also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "/home/u/old-format-project\n")
        }
        assertEquals(listOf(Paths.get("/home/u/old-format-project")), Settings.loadOpenProjects())
    }

    @Test
    fun `legacy project= key is migrated into the open list`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        val state = tmp.resolve("nop/state").also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "project=/home/u/proj\ntheme=light\n")
        }
        assertEquals(listOf(Paths.get("/home/u/proj")), Settings.loadOpenProjects())

        // Saving a fresh list should drop the legacy `project=` key.
        val replacement = tmp.resolve("replacement").also { Files.createDirectories(it) }
        Settings.saveOpenProjects(listOf(replacement))
        val raw = Files.readString(state)
        val keys = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringBefore('=') }
        assertTrue("project" !in keys, "legacy project= key should be dropped after a save, got:\n$raw")
        assertTrue(raw.contains("open.0="), "should write open.0= entry, got:\n$raw")
    }

    @Test
    fun `window geometry round-trips alongside the open projects`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        val project = tmp.resolve("project").also { Files.createDirectories(it) }
        Settings.saveOpenProjects(listOf(project))
        Settings.saveWindowGeometry(WindowGeometry(width = 1024, height = 768, x = 50, y = 100))

        val w = Settings.loadWindowGeometry()
        assertEquals(WindowGeometry(1024, 768, 50, 100), w)

        // Saving geometry must not clobber the open-projects list.
        assertEquals(listOf(project.toAbsolutePath().normalize()), Settings.loadOpenProjects())
    }

    @Test
    fun `loadWindowGeometry returns null when size missing`(@TempDir tmp: Path) {
        Settings.configRoot = tmp
        Settings.saveOpenProjects(listOf(tmp.resolve("project").also { Files.createDirectories(it) }))
        assertNull(Settings.loadWindowGeometry())
    }
}
