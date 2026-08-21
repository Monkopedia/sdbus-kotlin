package com.monkopedia.sdbus.plugin

import com.monkopedia.sdbus.capitalized
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool

class SdbusPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create("sdbus", SdbusExtension::class.java)
        target.afterEvaluate {
            val outputDirectory = target.layout.buildDirectory.dir("generated/sdbus")
            val rootTask = target.tasks.create("generateSdbusWrappers")
            val sourceSets = target.kotlinExtension.sourceSets
            ext.outputs.ifEmpty { listOf("linuxMain") }.forEach { name ->
                val sourceSet = sourceSets.findByName(name) ?: throw InvalidUserDataException(
                    unknownSourceSetMessage(name, ext.outputs.isEmpty(), sourceSets.names)
                )
                sourceSet.kotlin.srcDirs(outputDirectory)
            }
            target.tasks.withType(AbstractKotlinCompileTool::class.java).configureEach { task ->
                task.dependsOn(rootTask)
            }
            target.tasks.withType(Jar::class.java).configureEach { task ->
                task.dependsOn(rootTask)
            }
            ext.sources.asFileTree.filter { it.isFile && it.extension == "xml" }.forEach { file ->
                val name = "generateSdbusWrappers${file.nameWithoutExtension.capitalized}"
                val task = target.tasks.register(name, SdbusGenerationTask::class.java) {
                    it.outputDir = outputDirectory.get().dir(file.nameWithoutExtension).asFile
                    it.inputXmlFile = file
                    it.generateProxies = ext.generateProxies
                    it.generateAdapters = ext.generateAdapters
                    it.outputPackage = ext.outputPackage
                    it.honorNamingAnnotations = ext.honorNamingAnnotations
                }
                rootTask.dependsOn(task)
            }
        }
    }
}

/**
 * Attaching generated sources to a source set that does not exist would otherwise succeed
 * silently, leaving the consumer with generated files on disk that nothing ever compiles.
 */
private fun unknownSourceSetMessage(
    name: String,
    isDefault: Boolean,
    available: Collection<String>
): String = buildString {
    append("sdbus has nowhere to attach its generated code: Kotlin source set \"")
    append(name)
    append("\" does not exist in this project. ")
    if (isDefault) {
        append("It is the default; set sdbus { outputs } to the source set that should ")
        append("compile the generated code. ")
    }
    append("Available source sets: ")
    append(available.joinToString(", "))
}
