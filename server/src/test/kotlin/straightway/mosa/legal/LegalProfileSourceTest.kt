package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegalProfileSourceTest {
    private val yamlMapper =
        ObjectMapper(YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)

    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `read optional profile merges sibling language fragments`() {
        val basePath = tempDirectory.resolve("mosa_privacy.yaml")
        Files.writeString(
            basePath,
            """
            kind: profile
            id: privacy-operator-service
            version: "1.0.0-draft2"
            parent:
              id: mosa-privacy-gdpr-service
              version: "1.0.0-draft2"
            overrides:
              values:
                ORG_CONTROLLER_NAME: "Operator Corp"
            """.trimIndent(),
        )
        Files.writeString(
            tempDirectory.resolve("mosa_privacy.de.yaml"),
            """
            overrides:
              texts:
                de:
                  replace:
                    retention-slot-service: "- Fragment text"
            """.trimIndent(),
        )

        val source = createLegalProfileSource()

        val mergedProfile = source.readOptionalProfile(basePath)

        val node = yamlMapper.readTree(checkNotNull(mergedProfile))
        assertEquals(
            "- Fragment text",
            node
                .path("overrides")
                .path("texts")
                .path("de")
                .path("replace")
                .path("retention-slot-service")
                .asText(),
        )
    }

    @Test
    fun `read optional profile returns null when file is missing`() {
        val source = createLegalProfileSource()

        val profile = source.readOptionalProfile(tempDirectory.resolve("missing.yaml"))

        assertNull(profile)
    }

    @Test
    fun `load mandatory chain context for privacy exposes deterministic last profile`() {
        val source =
            LegalProfileSource(
                canonicalJsonConverter =
                    straightway.mosa.legal.clearterms
                        .ClearTermsYamlCanonicalJsonConverter(),
                languageFragmentMerger =
                    straightway.mosa.legal.clearterms
                        .ClearTermsYamlLanguageFragmentMerger(),
                resourceTextReader =
                    ResourceTextReader { path ->
                        LegalProfileSource::class.java.classLoader
                            .getResourceAsStream(path)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                    },
                resourceDirectoryEntriesProvider = ResourceDirectoryEntriesProvider { null },
                resourceUrlsProvider =
                    ResourceUrlsProvider { path ->
                        LegalProfileSource::class.java.classLoader.getResources(path)
                    },
            )

        val context = source.loadMandatoryChainContext(LegalDocumentKind.Privacy)

        assertEquals("mosa-privacy-gdpr-service", context.lastProfile.id)
        assertTrue(context.profileYamls.isNotEmpty())
        assertTrue("privacy-gdpr-default" in context.availableProfileVersions.keys)
    }

    @Test
    fun `default resource reader fails when requested baseline resource is missing`() {
        val source =
            LegalProfileSource(
                canonicalJsonConverter =
                    straightway.mosa.legal.clearterms
                        .ClearTermsYamlCanonicalJsonConverter(),
                languageFragmentMerger =
                    straightway.mosa.legal.clearterms
                        .ClearTermsYamlLanguageFragmentMerger(),
                resourceTextReader =
                    ResourceTextReader { path ->
                        LegalProfileSource::class.java.classLoader
                            .getResourceAsStream(path)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                    },
                resourceDirectoryEntriesProvider = ResourceDirectoryEntriesProvider { null },
                resourceUrlsProvider =
                    ResourceUrlsProvider { path ->
                        LegalProfileSource::class.java.classLoader.getResources(path)
                    },
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                source.loadMandatoryChainContext(
                    kind = LegalDocumentKind.Privacy,
                    baselineResource = "missing/baseline.yaml",
                    resourceDirectory = LegalDocumentKind.Privacy.resourceDirectory,
                )
            }

        assertEquals("Missing resource 'missing/baseline.yaml'", exception.message)
    }

    @Test
    fun `read optional profile ignores sibling fragments for non yaml base file`() {
        val basePath = tempDirectory.resolve("mosa_privacy.txt")
        Files.writeString(basePath, operatorProfileYaml())
        Files.writeString(basePath.resolveSibling("mosa_privacy.txt.de.yaml"), "overrides: [")

        val source = createLegalProfileSource()

        val mergedProfile = checkNotNull(source.readOptionalProfile(basePath))

        assertEquals(operatorProfileYaml(), mergedProfile)
    }

    @Test
    fun `parse operator profile metadata falls back to missing metadata for invalid yaml`() {
        val source = createLegalProfileSource()
        val path = tempDirectory.resolve("broken.yaml")

        val metadata = source.parseOperatorProfileMetadata("kind: profile\noverrides: [", path)

        assertEquals(path.toString(), metadata.id)
        assertEquals("unknown", metadata.version)
    }

    @Test
    fun `load mandatory chain context fails when no profiles are found`() {
        val resources =
            mapOf(
                "custom/baseline.yaml" to
                    """
                    kind: baseline
                    id: baseline
                    version: "1.0.0"
                    texts:
                      en:
                        - id: only
                          text: "Text"
                    """.trimIndent(),
            )
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceDirectoryEntriesProvider =
                    ResourceDirectoryEntriesProvider { directory ->
                        if (directory == "custom") listOf("custom/baseline.yaml") else null
                    },
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                source.loadMandatoryChainContext(
                    kind = LegalDocumentKind.Privacy,
                    baselineResource = "custom/baseline.yaml",
                    resourceDirectory = "custom",
                )
            }

        assertEquals("No legal profiles found in 'custom'", exception.message)
    }

    @Test
    fun `load mandatory chain context fails when resources do not form a deterministic chain`() {
        val resources =
            mapOf(
                "custom/baseline.yaml" to baselineYaml("baseline", "1.0.0"),
                "custom/default.yaml" to profileYaml("default", "baseline", "1.0.0"),
                "custom/service-a.yaml" to profileYaml("service-a", "default", "1.0.0"),
                "custom/service-b.yaml" to profileYaml("service-b", "default", "1.0.0"),
            )
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceDirectoryEntriesProvider =
                    ResourceDirectoryEntriesProvider { directory ->
                        if (directory == "custom") resources.keys.sorted() else null
                    },
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                source.loadMandatoryChainContext(
                    kind = LegalDocumentKind.Privacy,
                    baselineResource = "custom/baseline.yaml",
                    resourceDirectory = "custom",
                )
            }

        assertTrue(exception.message?.contains("do not form a single deterministic parent chain") == true)
    }

    @Test
    fun `load mandatory chain context reads profiles and fragments from jar resources`() {
        val jarPath =
            createJarFile(
                mapOf(
                    "jar/" to "",
                    "jar/legal-baseline.yaml" to baselineYaml("baseline", "1.0.0"),
                    "jar/legal-baseline.de.yaml" to
                        """
                        texts:
                          de:
                            - id: added
                              text: "Zusatz"
                        """.trimIndent(),
                    "jar/default.yaml" to profileYaml("default", "baseline", "1.0.0"),
                    "jar/service.yaml" to profileYaml("service", "default", "1.0.0"),
                ),
            )
        val directoryUrl = java.net.URI("jar:${jarPath.toUri()}!/jar/").toURL()
        val resources =
            mapOf(
                "jar/legal-baseline.yaml" to baselineYaml("baseline", "1.0.0"),
                "jar/legal-baseline.de.yaml" to
                    """
                    texts:
                      de:
                        - id: added
                          text: "Zusatz"
                    """.trimIndent(),
                "jar/default.yaml" to profileYaml("default", "baseline", "1.0.0"),
                "jar/service.yaml" to profileYaml("service", "default", "1.0.0"),
            )
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceUrlsProvider =
                    ResourceUrlsProvider { directory ->
                        if (directory == "jar") {
                            Collections.enumeration(listOf(directoryUrl))
                        } else {
                            Collections.emptyEnumeration()
                        }
                    },
            )

        val context =
            source.loadMandatoryChainContext(
                kind = LegalDocumentKind.Privacy,
                baselineResource = "jar/legal-baseline.yaml",
                resourceDirectory = "jar",
            )

        assertEquals("service", context.lastProfile.id)
        assertTrue(context.baselineYaml.contains("Zusatz"))
    }

    @Test
    fun `load mandatory chain context ignores unsupported fragment resource protocols`() {
        val resources =
            mapOf(
                "custom/baseline.yaml" to baselineYaml("baseline", "1.0.0"),
                "custom/default.yaml" to profileYaml("default", "baseline", "1.0.0"),
            )
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceDirectoryEntriesProvider =
                    ResourceDirectoryEntriesProvider { directory ->
                        if (directory == "custom") resources.keys.sorted() else null
                    },
                resourceUrlsProvider =
                    ResourceUrlsProvider { directory ->
                        if (directory == "custom") {
                            Collections.enumeration(
                                listOf(
                                    java.net.URL(
                                        null,
                                        "memory:/custom",
                                        object : java.net.URLStreamHandler() {
                                            override fun openConnection(url: java.net.URL): java.net.URLConnection =
                                                throw UnsupportedOperationException("not used")
                                        },
                                    ),
                                ),
                            )
                        } else {
                            Collections.emptyEnumeration()
                        }
                    },
            )

        val context =
            source.loadMandatoryChainContext(
                kind = LegalDocumentKind.Privacy,
                baselineResource = "custom/baseline.yaml",
                resourceDirectory = "custom",
            )

        assertEquals("default", context.lastProfile.id)
    }

    @Test
    fun `load mandatory chain context supports empty resource directory paths`() {
        val resources =
            mapOf(
                "baseline.yaml" to baselineYaml("baseline", "1.0.0"),
                "default.yaml" to profileYaml("default", "baseline", "1.0.0"),
                "service.yaml" to profileYaml("service", "default", "1.0.0"),
            )
        val directoryUrl = tempDirectory.toUri().toURL()
        resources.forEach { (path, content) ->
            Files.writeString(tempDirectory.resolve(path), content)
        }
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceUrlsProvider =
                    ResourceUrlsProvider { directory ->
                        if (directory.isEmpty()) {
                            Collections.enumeration(listOf(directoryUrl))
                        } else {
                            Collections.emptyEnumeration()
                        }
                    },
            )

        val context =
            source.loadMandatoryChainContext(
                kind = LegalDocumentKind.Privacy,
                baselineResource = "baseline.yaml",
                resourceDirectory = "",
            )

        assertEquals("service", context.lastProfile.id)
    }

    @Test
    fun `load mandatory chain context fails when required resource is missing`() {
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader { null },
                resourceDirectoryEntriesProvider =
                    ResourceDirectoryEntriesProvider { directory ->
                        if (directory == "custom") listOf("custom/default.yaml") else null
                    },
            )

        val exception =
            assertThrows<IllegalStateException> {
                source.loadMandatoryChainContext(
                    kind = LegalDocumentKind.Privacy,
                    baselineResource = "custom/baseline.yaml",
                    resourceDirectory = "custom",
                )
            }

        assertEquals("Missing resource 'custom/baseline.yaml'", exception.message)
    }

    @Test
    fun `load mandatory chain context fails when baseline metadata is malformed`() {
        val resources =
            mapOf(
                "custom/baseline.yaml" to "kind: baseline\nversion: \"1.0.0\"",
                "custom/default.yaml" to profileYaml("default", "baseline", "1.0.0"),
            )
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceDirectoryEntriesProvider =
                    ResourceDirectoryEntriesProvider { directory ->
                        if (directory == "custom") resources.keys.sorted() else null
                    },
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                source.loadMandatoryChainContext(
                    kind = LegalDocumentKind.Privacy,
                    baselineResource = "custom/baseline.yaml",
                    resourceDirectory = "custom",
                )
            }

        assertTrue(exception.message?.contains("Expected text key 'id'") == true)
    }

    @Test
    fun `load mandatory chain context fails when profile parent metadata is malformed`() {
        val resources =
            mapOf(
                "custom/baseline.yaml" to baselineYaml("baseline", "1.0.0"),
                "custom/default.yaml" to
                    """
                    kind: profile
                    id: default
                    version: "1.0.0"
                    parent: "baseline"
                    overrides: {}
                    """.trimIndent(),
            )
        val source =
            createLegalProfileSource(
                resourceTextReader = ResourceTextReader(resources::get),
                resourceDirectoryEntriesProvider =
                    ResourceDirectoryEntriesProvider { directory ->
                        if (directory == "custom") resources.keys.sorted() else null
                    },
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                source.loadMandatoryChainContext(
                    kind = LegalDocumentKind.Privacy,
                    baselineResource = "custom/baseline.yaml",
                    resourceDirectory = "custom",
                )
            }

        assertTrue(exception.message?.contains("Expected object key 'parent'") == true)
    }

    private fun baselineYaml(
        id: String,
        version: String,
    ) = """
        kind: baseline
        id: $id
        version: "$version"
        texts:
          en:
            - id: base
              text: "Base"
        """.trimIndent()

    private fun profileYaml(
        id: String,
        parentId: String,
        parentVersion: String,
    ) = """
        kind: profile
        id: $id
        version: "1.0.0"
        parent:
          id: $parentId
          version: "$parentVersion"
        overrides: {}
        """.trimIndent()

    private fun createJarFile(entries: Map<String, String>): Path {
        val jarPath = tempDirectory.resolve("resources.jar")
        JarOutputStream(Files.newOutputStream(jarPath)).use { jar ->
            entries.forEach { (path, content) ->
                jar.putNextEntry(JarEntry(path))
                if (content.isNotEmpty()) {
                    jar.write(content.toByteArray(Charsets.UTF_8))
                }
                jar.closeEntry()
            }
        }
        return jarPath
    }
}
