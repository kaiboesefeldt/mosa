package straightway.mosa.legal

import straightway.mosa.legal.clearterms.yaml.YamlLanguageFragmentMerger
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

internal class LegalProfileResourceResolver(
    private val languageFragmentMerger: YamlLanguageFragmentMerger,
    private val resourceTextReader: ResourceTextReader,
    private val resourceDirectoryEntriesProvider: ResourceDirectoryEntriesProvider,
    private val resourceUrlsProvider: ResourceUrlsProvider,
) {
    fun readRequiredResource(path: String): String {
        val baseYaml = resourceTextReader.read(path) ?: throw IllegalStateException("Missing resource '$path'")
        val fragmentYamls = listResourceFragments(path).map(::readRequiredResourceWithoutFragments)
        return languageFragmentMerger.merge(baseYaml, fragmentYamls)
    }

    fun readOptionalProfile(path: Path): String? {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!Files.exists(normalizedPath)) {
            return null
        }
        val baseYaml = Files.readString(normalizedPath, Charsets.UTF_8)
        val fragmentYamls = listSiblingFileFragments(normalizedPath).map { fragmentPath -> Files.readString(fragmentPath, Charsets.UTF_8) }
        return languageFragmentMerger.merge(baseYaml, fragmentYamls)
    }

    fun listResourceBaseDocuments(directoryPath: String): List<String> =
        listResourceFiles(directoryPath)
            .filter { path ->
                path.endsWith(".yaml") && path.substringAfterLast('/').count { character -> character == '.' } == 1
            }.sorted()

    private fun readRequiredResourceWithoutFragments(path: String): String =
        resourceTextReader.read(path) ?: throw IllegalStateException("Missing resource '$path'")

    private fun listSiblingFileFragments(basePath: Path): List<Path> {
        val baseFileName = basePath.fileName.toString()
        if (!baseFileName.endsWith(".yaml")) {
            return emptyList()
        }
        val prefix = "${baseFileName.removeSuffix(".yaml")}."
        val parent = basePath.parent
        return Files
            .list(parent)
            .use { stream ->
                stream
                    .filter { candidate ->
                        Files.isRegularFile(candidate) &&
                            candidate.fileName.toString() != baseFileName &&
                            candidate.fileName.toString().startsWith(prefix) &&
                            candidate.fileName.toString().endsWith(".yaml")
                    }.sorted()
                    .toList()
            }
    }

    private fun listResourceFragments(basePath: String): List<String> {
        val baseFileName = basePath.substringAfterLast('/')
        if (!baseFileName.endsWith(".yaml")) {
            return emptyList()
        }
        val directoryPath = basePath.substringBeforeLast('/', "")
        val discovered = linkedSetOf<String>()
        val resources = resourceUrlsProvider.list(directoryPath)
        while (resources.hasMoreElements()) {
            val directoryUrl = resources.nextElement()
            val fragments =
                when (directoryUrl.protocol) {
                    "file" -> {
                        listFileResourceFragments(directoryUrl.toURI(), directoryPath, baseFileName)
                    }

                    "jar" -> {
                        listJarResourceFragments(
                            directoryUrl.openConnection() as JarURLConnection,
                            directoryPath,
                            baseFileName,
                        )
                    }

                    else -> {
                        emptyList()
                    }
                }
            fragments.forEach(discovered::add)
        }
        return discovered.toList()
    }

    private fun listFileResourceFragments(
        directoryUri: java.net.URI,
        directoryPath: String,
        baseFileName: String,
    ): List<String> {
        val directory = Path.of(directoryUri)
        return Files
            .list(directory)
            .use { stream ->
                stream
                    .filter { candidate ->
                        Files.isRegularFile(candidate) && isMatchingFragmentName(candidate.fileName.toString(), baseFileName)
                    }.map { candidate -> toResourcePath(directoryPath, candidate.fileName.toString()) }
                    .sorted()
                    .toList()
            }
    }

    private fun listJarResourceFragments(
        connection: JarURLConnection,
        directoryPath: String,
        baseFileName: String,
    ): List<String> {
        val directoryEntryPrefix = connection.entryName.trimEnd('/') + "/"
        return connection
            .jarFile
            .entries()
            .asSequence()
            .map { entry -> entry.name }
            .filter { entryName ->
                entryName.startsWith(directoryEntryPrefix) &&
                    !entryName.removePrefix(directoryEntryPrefix).contains("/")
            }.map { entryName -> entryName.removePrefix(directoryEntryPrefix) }
            .filter { name -> isMatchingFragmentName(name, baseFileName) }
            .map { name -> toResourcePath(directoryPath, name) }
            .sorted()
            .toList()
    }

    private fun listResourceFiles(directoryPath: String): List<String> {
        resourceDirectoryEntriesProvider.list(directoryPath)?.let { entries ->
            return entries.sorted()
        }
        val discovered = linkedSetOf<String>()
        val resources = resourceUrlsProvider.list(directoryPath)
        while (resources.hasMoreElements()) {
            val directoryUrl = resources.nextElement()
            when (directoryUrl.protocol) {
                "file" -> {
                    val directory = Path.of(directoryUrl.toURI())
                    Files
                        .list(directory)
                        .use { stream ->
                            stream
                                .filter(Files::isRegularFile)
                                .map { candidate ->
                                    if (directoryPath.isEmpty()) {
                                        candidate.fileName.toString()
                                    } else {
                                        "$directoryPath/${candidate.fileName}"
                                    }
                                }.sorted()
                                .forEach(discovered::add)
                        }
                }

                "jar" -> {
                    val connection = directoryUrl.openConnection() as JarURLConnection
                    val directoryEntryPrefix = connection.entryName.trimEnd('/') + "/"
                    connection
                        .jarFile
                        .entries()
                        .asSequence()
                        .map { entry -> entry.name }
                        .filter { entryName ->
                            entryName.startsWith(directoryEntryPrefix) &&
                                !entryName.removePrefix(directoryEntryPrefix).contains("/")
                        }.sorted()
                        .forEach(discovered::add)
                }
            }
        }
        return discovered.toList()
    }

    private fun isMatchingFragmentName(
        candidateFileName: String,
        baseFileName: String,
    ): Boolean =
        candidateFileName != baseFileName &&
            candidateFileName.startsWith("${baseFileName.removeSuffix(".yaml")}.") &&
            candidateFileName.endsWith(".yaml")

    private fun toResourcePath(
        directoryPath: String,
        fileName: String,
    ): String =
        if (directoryPath.isEmpty()) {
            fileName
        } else {
            "$directoryPath/$fileName"
        }
}
