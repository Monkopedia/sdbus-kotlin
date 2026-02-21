package com.monkopedia.sdbus.plugin

import com.github.ajalt.clikt.core.main
import com.monkopedia.sdbus.Xml2Kotlin
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Optional
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

    @TaskAction
    fun execute() {
        inputXmlFile.collection().forEach {
            val outDir = File(outputDir, it.nameWithoutExtension + "Out")
            outDir.mkdirs()
            val args = mutableListOf<String>()
            if (generateProxies) {
                args.add("--proxy")
            }
            if (generateAdapters) {
                args.add("--adaptor")
            }
            outputPackage?.takeUnless(String::isBlank)?.let { packageName ->
                args.add("--output-package")
                args.add(packageName)
            }
            args.add("--output")
            args.add(outDir.absolutePath)
            args.add(it.absolutePath)
            Xml2Kotlin().main(
                args
            )
        }
    }
}

private fun File?.collection(): List<File> =
    this?.takeIf { it.isFile && it.extension == "xml" }?.let(::listOf)
        ?: this?.walkBottomUp()?.filter { it.isFile && it.extension == "xml" }?.toList()
        ?: emptyList()
