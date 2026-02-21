package com.monkopedia.sdbus

import com.github.ajalt.clikt.core.main
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Xml2KotlinOptionsTest {
    @Test
    fun generatesOnlyInterfaceByDefault() = withGeneratedOutput { outputDir ->
        assertEquals(setOf("org/foo/Background.kt"), generatedFileNames(outputDir))
    }

    @Test
    fun generatesAdaptorWhenFlagProvided() = withGeneratedOutput("--adaptor") { outputDir ->
        assertEquals(
            setOf("org/foo/Background.kt", "org/foo/BackgroundAdaptor.kt"),
            generatedFileNames(outputDir)
        )
    }

    @Test
    fun generatesProxyWhenFlagProvided() = withGeneratedOutput("--proxy") { outputDir ->
        assertEquals(
            setOf("org/foo/Background.kt", "org/foo/BackgroundProxy.kt"),
            generatedFileNames(outputDir)
        )
    }

    @Test
    fun generatesAdaptorAndProxyWhenBothFlagsProvided() = withGeneratedOutput(
        "--adaptor",
        "--proxy"
    ) { outputDir ->
        assertEquals(
            setOf(
                "org/foo/Background.kt",
                "org/foo/BackgroundAdaptor.kt",
                "org/foo/BackgroundProxy.kt"
            ),
            generatedFileNames(outputDir)
        )
    }

    @Test
    fun outputPackageOverrideRewritesGeneratedPackagePath() = withGeneratedOutput(
        "--output-package",
        "com.example.generated"
    ) { outputDir ->
        val generated = File(outputDir, "com/example/generated/Background.kt")
        assertTrue(generated.exists())
        assertFalse(File(outputDir, "org/foo/Background.kt").exists())
        assertTrue(generated.readText().contains("package com.example.generated"))
    }

    @Test
    fun keepFlagPreservesPreExistingOutputContent() {
        val markerName = "preexisting.keep"
        withGeneratedOutput("--keep", prepopulate = { outputDir ->
            File(outputDir, markerName).writeText("keep me")
        }) { outputDir ->
            assertTrue(File(outputDir, markerName).exists())
            assertTrue(File(outputDir, "org/foo/Background.kt").exists())
        }
    }

    @Test
    fun outputIsClearedWithoutKeepFlag() {
        val markerName = "preexisting.delete"
        withGeneratedOutput(prepopulate = { outputDir ->
            File(outputDir, markerName).writeText("delete me")
        }) { outputDir ->
            assertFalse(File(outputDir, markerName).exists())
            assertTrue(File(outputDir, "org/foo/Background.kt").exists())
        }
    }

    private fun withGeneratedOutput(
        vararg flags: String,
        prepopulate: (File) -> Unit = {},
        block: (File) -> Unit
    ) {
        val root = Files.createTempDirectory("kdbus-codegen-options").toFile()
        val input = File(root, "input.xml")
        val output = File(root, "out")
        output.mkdirs()
        prepopulate(output)
        input.writeText(SIMPLE_XML)

        try {
            Xml2Kotlin().main(
                arrayOf(
                    input.absolutePath,
                    "-o",
                    output.absolutePath
                ) + flags
            )
            block(output)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun generatedFileNames(outputDir: File): Set<String> = outputDir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(outputDir).invariantSeparatorsPath }
        .toSet()

    private companion object {
        private val SIMPLE_XML = """
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
