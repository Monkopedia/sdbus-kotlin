package com.monkopedia.sdbus.plugin

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder

/**
 * The task action used to invoke the generator through Clikt's `main`, which reports a
 * `CliktError` by printing usage and calling `exitProcess` — killing the build JVM rather
 * than failing the task. These tests drive the action directly, so a regression shows up as
 * the test JVM dying ("Gradle Test Executor N finished with non-zero exit value") rather
 * than as an assertion failure.
 */
class SdbusGenerationTaskTest {

    @Test
    fun rejectedGeneratorArgumentsFailTheTaskNamingTheXmlFile() = withTempDir { dir ->
        val xml = File(dir, "sample.xml").apply { writeText(VALID_XML) }
        val outputDir = File(dir, "out").apply { mkdirs() }
        // A regular file where the per-XML output directory belongs: Clikt's `--output`
        // is declared `canBeFile = false`, so parsing the generator's own arguments fails.
        File(outputDir, "sampleOut").writeText("not a directory")

        val error = assertFailsWith<Exception> { task(dir, xml, outputDir).execute() }

        assertTrue(
            error.message.orEmpty().contains(xml.absolutePath),
            "Expected the failure to name the XML being processed; was: ${error.message}"
        )
    }

    @Test
    fun malformedXmlFailsTheTaskNamingTheXmlFile() = withTempDir { dir ->
        val xml = File(dir, "sample.xml").apply { writeText("this is not xml") }
        val outputDir = File(dir, "out").apply { mkdirs() }

        val error = assertFailsWith<Exception> { task(dir, xml, outputDir).execute() }

        assertTrue(
            error.message.orEmpty().contains(xml.absolutePath),
            "Expected the failure to name the XML being processed; was: ${error.message}"
        )
    }

    @Test
    fun validXmlStillGenerates() = withTempDir { dir ->
        val xml = File(dir, "sample.xml").apply { writeText(VALID_XML) }
        val outputDir = File(dir, "out").apply { mkdirs() }

        task(dir, xml, outputDir).execute()

        assertTrue(File(outputDir, "sampleOut/org/foo/Background.kt").exists())
    }

    private fun task(projectDir: File, xml: File, outputDir: File): SdbusGenerationTask =
        ProjectBuilder.builder().withProjectDir(projectDir).build().tasks
            .register("generateSdbusWrappersSample", SdbusGenerationTask::class.java) {
                it.inputXmlFile = xml
                it.outputDir = outputDir
            }.get()

    private fun withTempDir(block: (File) -> Unit) {
        val root = Files.createTempDirectory("sdbus-generation-task-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val VALID_XML = """
            <node>
              <interface name="org.foo.Background">
                <method name="currentBackground">
                  <arg type="s" direction="out"/>
                </method>
              </interface>
            </node>
        """.trimIndent()
    }
}
