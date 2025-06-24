package com.monkopedia.sdbus.plugin

import com.github.ajalt.clikt.core.main
import com.monkopedia.sdbus.Xml2Kotlin
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
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

    @TaskAction
    fun execute() {
        inputXmlFile.collection().forEach {
            val outDir = File(outputDir, it.nameWithoutExtension + "Out")
            outDir.mkdirs()
            Xml2Kotlin().main(
                listOfNotNull(
                    "--proxy".takeIf { generateProxies == true },
                    "--adaptor".takeIf { generateAdapters == true },
                    "--output",
                    outDir.absolutePath,
                    it.absolutePath
                )
            )
        }
    }
}

private fun File?.collection(): List<File> =
    this?.takeIf { it.isFile && it.extension == "xml" }?.let(::listOf)
        ?: this?.walkBottomUp()?.filter { it.isFile && it.extension == "xml" }?.toList()
        ?: emptyList()
