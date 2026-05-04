
import io.fabric8.crd.generator.collector.CustomResourceCollector
import io.fabric8.crdv2.generator.CRDGenerationInfo
import io.fabric8.crdv2.generator.CRDGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists

abstract class GenerateCrdsTask : DefaultTask() {

    @get:Input
    abstract val sourceSet: Property<SourceSet>

    @get:Input
    abstract val includePackages: ListProperty<String>

    @get:InputDirectory
    abstract val targetDirectory: DirectoryProperty

    @TaskAction
    fun generateCrds() {
        val sourceSet = sourceSet.get()

        val compileClasspathElements = sourceSet.compileClasspath.map { it.absolutePath }
        val outputClassesDirs = sourceSet.output.classesDirs
        val outputClasspathElements = outputClassesDirs.map { it.absolutePath }
        val classpathElements = listOf(outputClasspathElements, compileClasspathElements).flatten()
        val filesToScan = listOf(outputClassesDirs).flatten()

        val collector = CustomResourceCollector()
            .withParentClassLoader(Thread.currentThread().contextClassLoader)
            .withClasspathElements(classpathElements)
            .withFilesToScan(filesToScan)
            .withIncludePackages(includePackages.get())

        val crdGenerator = CRDGenerator()
            .customResourceClasses(collector.findCustomResourceClasses())
            .inOutputDir(temporaryDir)

        val targetDir = targetDirectory.get().asFile.toPath()
        if (targetDir.notExists()) targetDir.createDirectory()

        val crdGenerationInfo: CRDGenerationInfo = crdGenerator.detailedGenerate()
        crdGenerationInfo.crdDetailsPerNameAndVersion.forEach { (_, versionToInfo) ->
            versionToInfo.forEach { (_, info) ->
                val srcFile = File(info.filePath)
                val crdContent = srcFile.readText()
                val metadataRegex = Regex(
                    "(metadata:\\s*.*?)(annotations:\\s*(\\{.*?\\}|.*?\\n))?(\\s+\\w+:|$)",
                    RegexOption.DOT_MATCHES_ALL
                )

                val updatedContent = metadataRegex.replace(crdContent) { matchResult ->
                    val metadataBlock = matchResult.groups[1]?.value.orEmpty()
                    val annotationsBlock = matchResult.groups[2]?.value?.trim().orEmpty()
                    val rest = matchResult.groups[4]?.value.orEmpty()

                    buildString {
                        append(metadataBlock)
                        if (annotationsBlock.isBlank()) {
                            appendLine()
                            appendLine("  annotations:")
                        } else {
                            appendLine(annotationsBlock)
                        }
                        appendLine("{{- with .Values.annotations }}")
                        appendLine("{{- toYaml . | nindent 4 }}")
                        append("{{- end }}")
                        if (annotationsBlock.isNotBlank()) appendLine()
                        append(rest)
                    }
                }

                val targetPath = targetDir.resolve(srcFile.name)
                targetPath.toFile().writeText(updatedContent, Charsets.UTF_8)
            }
        }
    }
}