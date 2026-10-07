package com.app.ralaunch.core.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.writeText

class FileUtilsTest {

    @Test
    fun `deleteDirectoryRecursivelyWithinRoot deletes strict child only`() {
        val root = createTempDirectory("fileutils-root-")
        val child = root.resolve("child").createDirectories()
        val outside = createTempDirectory("fileutils-outside-")

        try {
            child.resolve("payload.txt").createFile().writeText("ok")
            outside.resolve("outside.txt").createFile().writeText("keep")

            assertTrue(FileUtils.deleteDirectoryRecursivelyWithinRoot(child, root))
            assertTrue(root.exists())
            assertFalse(child.exists())
            assertTrue(outside.resolve("outside.txt").exists())
        } finally {
            FileUtils.deleteDirectoryRecursively(root)
            FileUtils.deleteDirectoryRecursively(outside)
        }
    }

    @Test
    fun `deleteDirectoryRecursivelyWithinRoot refuses deleting root`() {
        val root = createTempDirectory("fileutils-root-")

        try {
            root.resolve("child").createDirectories()

            assertFalse(FileUtils.deleteDirectoryRecursivelyWithinRoot(root, root))
            assertTrue(root.exists())
        } finally {
            FileUtils.deleteDirectoryRecursively(root)
        }
    }

    @Test
    fun `deleteFileWithinRoot refuses deleting file outside root`() {
        val root = createTempDirectory("fileutils-root-")
        val outside = createTempDirectory("fileutils-outside-")

        try {
            val outsideFile = outside.resolve("outside.txt").createFile().apply {
                writeText("keep")
            }

            assertFalse(FileUtils.deleteFileWithinRoot(outsideFile, root))
            assertTrue(outsideFile.exists())
        } finally {
            FileUtils.deleteDirectoryRecursively(root)
            FileUtils.deleteDirectoryRecursively(outside)
        }
    }

    @Test
    fun `deleteDirectoryRecursively does not follow symlinked directory`() {
        val root = createTempDirectory("fileutils-root-")
        val outside = createTempDirectory("fileutils-outside-")

        try {
            val protectedFile = outside.resolve("protected.txt").createFile().apply {
                writeText("keep")
            }
            val symlink = root.resolve("link-dir")
            createDirectorySymlinkOrSkip(symlink, outside)

            assertTrue(FileUtils.deleteDirectoryRecursively(symlink))
            assertTrue(protectedFile.exists())
            assertTrue(outside.exists())
            assertTrue(symlink.notExists())
        } finally {
            FileUtils.deleteDirectoryRecursively(root)
            FileUtils.deleteDirectoryRecursively(outside)
        }
    }

    @Test
    fun `clearDirectory removes children and keeps root`() {
        val root = createTempDirectory("fileutils-root-")

        try {
            root.resolve("a.txt").createFile().writeText("a")
            root.resolve("nested").createDirectories()
                .resolve("b.txt")
                .createFile()
                .writeText("b")

            assertTrue(FileUtils.clearDirectory(root.toFile()))
            assertTrue(root.exists())
            assertTrue(Files.list(root).use { stream -> stream.findAny().isEmpty })
        } finally {
            FileUtils.deleteDirectoryRecursively(root)
        }
    }

    private fun createDirectorySymlinkOrSkip(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        } catch (e: SecurityException) {
            assumeNoException(e)
        } catch (e: Exception) {
            assumeNoException(e)
        }
    }
}
