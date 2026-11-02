package com.monkopedia.sdbus.plugin

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

class SdbusPluginTest {
    @Test
    fun generatesInterfaceByDefault() =
        withProject(generateProxies = false, generateAdapters = false) { dir ->
            val result = runTask(dir, "generateSdbusWrappers")
            assertTrue(result.output.contains("generateSdbusWrappersSample"))

            val generatedRoot = File(dir, "build/generated/sdbus/sample/sampleOut/org/foo")
            assertTrue(File(generatedRoot, "Background.kt").exists())
            assertFalse(File(generatedRoot, "BackgroundAdaptor.kt").exists())
            assertFalse(File(generatedRoot, "BackgroundProxy.kt").exists())
        }

    @Test
    fun generatesProxyAndAdaptorWhenEnabled() =
        withProject(generateProxies = true, generateAdapters = true) { dir ->
            runTask(dir, "generateSdbusWrappers")

            val generatedRoot = File(dir, "build/generated/sdbus/sample/sampleOut/org/foo")
            assertTrue(File(generatedRoot, "Background.kt").exists())
            assertTrue(File(generatedRoot, "BackgroundAdaptor.kt").exists())
            assertTrue(File(generatedRoot, "BackgroundProxy.kt").exists())
        }

    @Test
    fun generatedProxyCompilesInConsumerSourceSet() = withProject(
        generateProxies = true,
        generateAdapters = false,
        compileTargetJvm = true,
        includeCompileFixture = true
    ) { dir ->
        val result = runTask(dir, "compileKotlinJvm")
        assertTrue(result.output.contains("compileKotlinJvm"))

        val generatedRoot = File(dir, "build/generated/sdbus/sample/sampleOut/org/foo")
        assertTrue(File(generatedRoot, "Background.kt").exists())
        assertTrue(File(generatedRoot, "BackgroundProxy.kt").exists())
    }

    @Test
    fun wiresGeneratorAsDependencyOfNativeCompile() = withProject(
        generateProxies = false,
        generateAdapters = false
    ) { dir ->
        val result = runTask(dir, "compileKotlinLinuxX64", "--dry-run")
        assertTrue(
            result.output.contains("generateSdbusWrappers"),
            "Expected compileKotlinLinuxX64 to depend on generateSdbusWrappers; output was:\n" +
                result.output
        )
    }

    @Test
    fun wiresGeneratorAsDependencyOfSourcesJar() = withProject(
        generateProxies = false,
        generateAdapters = false,
        applyMavenPublish = true
    ) { dir ->
        val result = runTask(dir, "sourcesJar", "--dry-run")
        assertTrue(
            result.output.contains("generateSdbusWrappers"),
            "Expected sourcesJar to depend on generateSdbusWrappers; output was:\n" +
                result.output
        )
    }

    @Test
    fun appliesOutputPackageOverride() = withProject(
        generateProxies = false,
        generateAdapters = false,
        outputPackage = "com.example.generated"
    ) { dir ->
        runTask(dir, "generateSdbusWrappers")

        val generated = File(
            dir,
            "build/generated/sdbus/sample/sampleOut/com/example/generated/Background.kt"
        )
        assertTrue(generated.exists())
        assertFalse(
            File(dir, "build/generated/sdbus/sample/sampleOut/org/foo/Background.kt").exists()
        )
    }

    @Test
    fun honorsNamingAnnotationsWhenEnabled() = withProject(
        generateProxies = false,
        generateAdapters = false,
        honorNamingAnnotations = true,
        xml = HINTED_XML
    ) { dir ->
        runTask(dir, "generateSdbusWrappers")

        val generatedRoot = File(dir, "build/generated/sdbus/sample/sampleOut/org/foo")
        assertTrue(File(generatedRoot, "QPoint.kt").exists())
        assertFalse(File(generatedRoot, "Corner.kt").exists())
    }

    @Test
    fun unknownOutputSourceSetFailsConfigurationListingTheRealOnes() = withProject(
        generateProxies = false,
        generateAdapters = false,
        compileTargetJvm = true,
        outputSourceSet = "notASourceSet"
    ) { dir ->
        val result = runTaskAndFail(dir, "generateSdbusWrappers")

        assertTrue(
            result.output.contains("notASourceSet"),
            "Expected the failure to name the bad value; output was:\n" + result.output
        )
        assertTrue(
            result.output.contains("jvmMain"),
            "Expected the failure to list the source sets that do exist; output was:\n" +
                result.output
        )
        assertFalse(
            File(dir, "build/generated/sdbus/sample/sampleOut/org/foo/Background.kt").exists(),
            "Generation should not have run at all; the failure is a configuration error."
        )
    }

    @Test
    fun omittedOutputsFallsBackToLinuxMainAndSaysSo() = withProject(
        generateProxies = false,
        generateAdapters = false,
        omitOutputs = true
    ) { dir ->
        val result = runTaskAndFail(dir, "generateSdbusWrappers")

        assertTrue(
            result.output.contains("linuxMain"),
            "Expected the failure to name the default; output was:\n" + result.output
        )
        assertTrue(
            result.output.contains("It is the default; set sdbus { outputs }"),
            "A missing default needs different advice from a typo; output was:\n" +
                result.output
        )
    }

    @Test
    fun knownOutputSourceSetGetsTheGeneratedDirectory() = withProject(
        generateProxies = false,
        generateAdapters = false,
        compileTargetJvm = true
    ) { dir ->
        val result = runTask(dir, "printSdbusSrcDirs")

        val generatedDir = File(dir, "build/generated/sdbus").absolutePath
        val srcDirs = result.output.lineSequence()
            .single { it.startsWith("SRCDIRS=") }
            .removePrefix("SRCDIRS=")
            .split(":")
        assertTrue(
            generatedDir in srcDirs,
            "Expected jvmMain to include $generatedDir; source dirs were $srcDirs"
        )
    }

    private fun withProject(
        generateProxies: Boolean,
        generateAdapters: Boolean,
        outputPackage: String? = null,
        honorNamingAnnotations: Boolean = false,
        xml: String = SAMPLE_XML,
        compileTargetJvm: Boolean = false,
        includeCompileFixture: Boolean = false,
        applyMavenPublish: Boolean = false,
        outputSourceSet: String? = null,
        omitOutputs: Boolean = false,
        block: (File) -> Unit
    ) {
        // A single-target KMP project has no "linuxMain" intermediate source set — the
        // hierarchy template only materialises one for a group with several targets. See
        // the plugin's default in SdbusPlugin, which is why this fixture must be explicit.
        val targetSourceSet = if (compileTargetJvm) "jvmMain" else "linuxX64Main"
        val root = Files.createTempDirectory("kdbus-plugin-test").toFile()
        try {
            writeFile(
                File(root, "settings.gradle.kts"),
                """
                    pluginManagement {
                      repositories {
                        gradlePluginPortal()
                        mavenCentral()
                      }
                    }
                    rootProject.name = "plugin-fixture"
                """
            )
            writeFile(
                File(root, "build.gradle.kts"),
                """
                    plugins {
                      id("org.jetbrains.kotlin.multiplatform") version "2.2.10"
                      id("com.monkopedia.sdbus.plugin")
                      ${if (applyMavenPublish) "id(\"maven-publish\")" else ""}
                    }

                    repositories {
                      mavenCentral()
                    }

                    kotlin {
                      ${if (compileTargetJvm) "jvm()" else "linuxX64()"}
                    }

                    sdbus {
                      generateProxies = $generateProxies
                      generateAdapters = $generateAdapters
                      honorNamingAnnotations = $honorNamingAnnotations
                      ${outputPackage?.let { "outputPackage = \"$it\"" } ?: ""}
                      ${if (omitOutputs) "" else outputsLine(outputSourceSet ?: targetSourceSet)}
                      sources.srcDir("src/sdbus")
                    }

                    tasks.register("printSdbusSrcDirs") {
                      doLast {
                        val srcDirs =
                          kotlin.sourceSets.getByName("$targetSourceSet").kotlin.srcDirs
                        println("SRCDIRS=" + srcDirs.joinToString(":"))
                      }
                    }
                """
            )
            writeFile(File(root, "src/sdbus/sample.xml"), xml)
            if (includeCompileFixture) {
                writeFile(
                    File(root, "src/$targetSourceSet/kotlin/com/monkopedia/sdbus/Stubs.kt"),
                    """
                        package com.monkopedia.sdbus

                        class InterfaceName(val value: String)
                        class MethodName(val value: String)

                        interface Proxy

                        class CallBuilder {
                          fun call(vararg args: Any?) {}
                        }

                        @Suppress("UNUSED_PARAMETER")
                        suspend fun <T> Proxy.callMethodAsync(
                          interfaceName: InterfaceName,
                          methodName: MethodName,
                          block: CallBuilder.() -> Unit,
                        ): T = error("test stub")
                    """
                )
                writeFile(
                    File(root, "src/$targetSourceSet/kotlin/org/foo/Usage.kt"),
                    """
                        package org.foo

                        import com.monkopedia.sdbus.Proxy

                        class Usage(private val proxy: Proxy) {
                          val generated = BackgroundProxy(proxy)
                        }
                    """
                )
            }

            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runTask(projectDir: File, vararg args: String): BuildResult =
        runner(projectDir, *args).build()

    private fun runTaskAndFail(projectDir: File, vararg args: String): BuildResult =
        runner(projectDir, *args).buildAndFail()

    private fun runner(projectDir: File, vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(
            *args,
            "--offline",
            "--stacktrace",
            "-g",
            File(System.getProperty("user.home"), ".gradle").absolutePath
        )

    private fun outputsLine(sourceSet: String): String = "outputs.add(\"$sourceSet\")"

    private fun writeFile(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent())
    }

    private companion object {
        private val SAMPLE_XML = """
            <node>
              <interface name="org.foo.Background">
                <method name="currentBackground">
                  <arg type="s" direction="out"/>
                </method>
              </interface>
            </node>
        """

        private val HINTED_XML = """
            <node>
              <interface name="org.foo.Hinted">
                <property name="Corner" type="(ii)" access="read">
                  <annotation name="org.qtproject.QtDBus.QtTypeName" value="QPoint"/>
                </property>
              </interface>
            </node>
        """
    }
}
