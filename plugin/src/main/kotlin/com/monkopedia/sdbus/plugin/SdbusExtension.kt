package com.monkopedia.sdbus.plugin

import javax.inject.Inject
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

open class SdbusExtension(
    @Inject
    open val objectFactory: ObjectFactory,
    @Input
    open val outputs: MutableList<String> = mutableListOf()
) {
    @Input
    open var generateProxies: Boolean = false

    @Input
    open var generateAdapters: Boolean = false

    @get:Input
    @get:Optional
    open var outputPackage: String? = null

    @get:InputDirectory
    open val sources: SourceDirectorySet by lazy {
        objectFactory.sourceDirectorySet("sdbusXml", "XML Inputs for Sdbus Kotlin import")
    }
}
