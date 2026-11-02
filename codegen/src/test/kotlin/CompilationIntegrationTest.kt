package com.monkopedia.sdbus

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services

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

    @Test
    fun generatedCodeNamedFromAnnotationsCompiles() {
        val xml = File("src/test/resources/NamingHintsTest/test.xml").readText()

        val root = introspectionXml.decodeFromString<XmlRootNode>(xml)
        assertGeneratedCodeCompiles(root, honorNamingAnnotations = true)
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
        val outDir = Files.createTempDirectory("codegen-compile-test-out").toFile()
        try {
            (interfaceFiles + proxyFiles + adaptorFiles).forEach { it.writeTo(srcDir) }

            val sourceFiles = srcDir.walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.absolutePath }
                .toList()

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
                freeArgs = sourceFiles
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
            srcDir.deleteRecursively()
            outDir.deleteRecursively()
        }
    }
}
