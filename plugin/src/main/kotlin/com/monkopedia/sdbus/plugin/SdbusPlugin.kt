package com.monkopedia.sdbus.plugin

import com.monkopedia.sdbus.capitalized
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class SdbusPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create("sdbus", SdbusExtension::class.java)
        target.afterEvaluate {
            val outputDirectory = target.layout.buildDirectory.dir("generated/sdbus")
            val rootTask = target.tasks.create("generateSdbusWrappers")
            ext.outputs.ifEmpty { mutableListOf("linuxMain") }.forEach {
                target.kotlinExtension.sourceSets.findByName(it)?.apply {
                    kotlin.srcDirs(outputDirectory)
                }
            }
            target.tasks.withType(KotlinCompile::class.java).configureEach { task ->
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
                }
                rootTask.dependsOn(task)
            }
        }
    }
}
