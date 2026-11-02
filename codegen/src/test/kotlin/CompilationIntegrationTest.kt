package com.monkopedia.sdbus

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CompilationIntegrationTest {

    @Test
    fun generatedCodeWithKotlinKeywordParametersCompiles() {
        val xml = """
            <node>
              <interface name="Org.Example.KeywordTest">
                <method name="DoSomething">
                  <arg name="interface" type="s" direction="in"/>
                  <arg name="object" type="s" direction="in"/>
                  <arg name="result" type="b" direction="out"/>
                </method>
                <signal name="StatusChanged">
                  <arg name="interface" type="s"/>
                  <arg name="object" type="s"/>
                </signal>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        assertGeneratedCodeCompiles(root, honorNamingAnnotations = false)
    }

    /*
     * The checked-in goldens of every fixture that pins both sides of the naming-annotation flag,
     * compiled as they sit on disk. :compile_test has to leave the `hinted-` side out of its one
     * native compilation (it redeclares the default side), so this is the check that type-checks
     * those files; each side gets its own compilation for the same reason.
     */
    @ParameterizedTest
    @MethodSource("com.monkopedia.sdbus.NamingHintTest#data")
    fun defaultGoldensCompile(testRoot: File) = assertGoldensCompile(testRoot, hinted = false)

    @ParameterizedTest
    @MethodSource("com.monkopedia.sdbus.NamingHintTest#data")
    fun hintedGoldensCompile(testRoot: File) = assertGoldensCompile(testRoot, hinted = true)

    private fun assertGoldensCompile(testRoot: File, hinted: Boolean) {
        val goldens = GoldenFixture.entries.flatMap { it.goldenFiles(testRoot, hinted) }
        // Without this, a fixture whose goldens all went missing would compile nothing and pass.
        assertTrue(goldens.isNotEmpty(), "No goldens to compile in ${testRoot.name}")
        assertCompiles(goldens)
    }

    private fun assertGeneratedCodeCompiles(root: XmlRootNode, honorNamingAnnotations: Boolean) {
        val interfaceFiles = InterfaceGenerator(
            honorNamingAnnotations = honorNamingAnnotations
        ).transformXmlToFile(root)
        val proxyFiles =
            ProxyGenerator(honorNamingAnnotations = honorNamingAnnotations).transformXmlToFile(root)
        val adaptorFiles = AdaptorGenerator(
            honorNamingAnnotations = honorNamingAnnotations
        ).transformXmlToFile(root)

        val srcDir = Files.createTempDirectory("codegen-compile-test-src").toFile()
        try {
            (interfaceFiles + proxyFiles + adaptorFiles).forEach { it.writeTo(srcDir) }
            assertCompiles(srcDir.walkTopDown().filter { it.extension == "kt" }.toList())
        } finally {
            srcDir.deleteRecursively()
        }
    }

    private fun assertCompiles(sourceFiles: List<File>) {
        val outDir = Files.createTempDirectory("codegen-compile-test-out").toFile()
        try {
            val errors = mutableListOf<String>()
            val messageCollector = object : MessageCollector {
                override fun clear() {}
                override fun hasErrors() = errors.isNotEmpty()
                override fun report(
                    severity: CompilerMessageSeverity,
                    message: String,
                    location: CompilerMessageSourceLocation?
                ) {
                    if (severity.isError) errors.add("$location: $message")
                }
            }

            val jvmTarget = System.getProperty("java.specification.version")
            val args = K2JVMCompilerArguments().apply {
                freeArgs = sourceFiles.map { it.absolutePath }
                classpath = System.getProperty("java.class.path")
                destination = outDir.absolutePath
                this.jvmTarget = jvmTarget
                noStdlib = true
            }

            val exitCode = K2JVMCompiler().exec(messageCollector, Services.EMPTY, args)
            assertEquals(
                ExitCode.OK,
                exitCode,
                "Generated code should compile. Errors:\n${errors.joinToString("\n")}"
            )
        } finally {
            outDir.deleteRecursively()
        }
    }
}
