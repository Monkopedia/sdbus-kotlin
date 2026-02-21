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
    fun generatesInterfaceByDefault() = withProject(generateProxies = false, generateAdapters = false) { dir ->
        val result = runTask(dir, "generateSdbusWrappers")
        assertTrue(result.output.contains("generateSdbusWrappersSample"))

        val generatedRoot = File(dir, "build/generated/sdbus/sample/sampleOut/org/foo")
        assertTrue(File(generatedRoot, "Background.kt").exists())
        assertFalse(File(generatedRoot, "BackgroundAdaptor.kt").exists())
        assertFalse(File(generatedRoot, "BackgroundProxy.kt").exists())
    }

    @Test
    fun generatesProxyAndAdaptorWhenEnabled() = withProject(generateProxies = true, generateAdapters = true) { dir ->
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

    private fun withProject(
        generateProxies: Boolean,
        generateAdapters: Boolean,
        outputPackage: String? = null,
        compileTargetJvm: Boolean = false,
        includeCompileFixture: Boolean = false,
        block: (File) -> Unit
    ) {
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
                      ${outputPackage?.let { "outputPackage = \"$it\"" } ?: ""}
                      outputs.add("${if (compileTargetJvm) "jvmMain" else "linuxMain"}")
                      sources.srcDir("src/sdbus")
                    }
                """
            )
            writeFile(
                File(root, "src/sdbus/sample.xml"),
                """
                    <node>
                      <interface name="org.foo.Background">
                        <method name="currentBackground">
                          <arg type="s" direction="out"/>
                        </method>
                      </interface>
                    </node>
                """
            )
            if (includeCompileFixture) {
                val sourceSetDir = if (compileTargetJvm) "jvmMain" else "linuxMain"
                writeFile(
                    File(root, "src/$sourceSetDir/kotlin/com/monkopedia/sdbus/Stubs.kt"),
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
                    File(root, "src/$sourceSetDir/kotlin/org/foo/Usage.kt"),
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

    private fun runTask(projectDir: File, taskName: String): BuildResult = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(
            taskName,
            "--offline",
            "--stacktrace",
            "-g",
            File(System.getProperty("user.home"), ".gradle").absolutePath
        )
        .build()

    private fun writeFile(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent())
    }
}
