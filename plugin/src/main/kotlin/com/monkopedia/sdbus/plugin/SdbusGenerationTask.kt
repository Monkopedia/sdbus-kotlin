package com.monkopedia.sdbus.plugin

import com.github.ajalt.clikt.core.parse
import com.monkopedia.sdbus.Xml2Kotlin
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
open class SdbusGenerationTask : DefaultTask() {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    open var inputXmlFile: File? = null

    @OutputDirectory
    open var outputDir: File? = null

    @Input
    open var generateProxies: Boolean = false

    @Input
    open var generateAdapters: Boolean = false

    @get:Input
    @get:Optional
    open var outputPackage: String? = null

    @Input
    open var honorNamingAnnotations: Boolean = false

    @TaskAction
    fun execute() {
        inputXmlFile.collection().forEach { xml ->
            val outDir = File(outputDir, xml.nameWithoutExtension + "Out")
            outDir.mkdirs()
            val args = mutableListOf<String>()
            if (generateProxies) {
                args.add("--proxy")
            }
            if (generateAdapters) {
                args.add("--adaptor")
            }
            if (honorNamingAnnotations) {
                args.add("--honor-naming-annotations")
            }
            outputPackage?.takeUnless(String::isBlank)?.let { packageName ->
                args.add("--output-package")
                args.add(packageName)
            }
            args.add("--output")
            args.add(outDir.absolutePath)
            args.add(xml.absolutePath)
            // Clikt's main() is the terminal entry point for a CLI process: it reports
            // CliktError by printing usage and calling exitProcess, which inside a Gradle
            // build takes down the whole daemon. parse() does the same parsing and raises
            // the same errors, leaving them for Gradle to report as a task failure.
            try {
                Xml2Kotlin().parse(args)
            } catch (e: Exception) {
                throw GradleException(
                    "Failed to generate sdbus wrappers for ${xml.absolutePath}: " +
                        (e.message ?: e.toString()),
                    e
                )
            }
        }
    }
}

private fun File?.collection(): List<File> =
    this?.takeIf { it.isFile && it.extension == "xml" }?.let(::listOf)
        ?: this?.walkBottomUp()?.filter { it.isFile && it.extension == "xml" }?.toList()
        ?: emptyList()
