package straightway.mosa.legal

import straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker
import java.nio.file.Files
import java.nio.file.Path

internal class LegalOperatorProfileTemplateWriter(
    private val templateCompletenessChecker: ProfileChainCompletenessChecker,
) {
    fun createMissingOperatorProfileFile(
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
        kind: LegalDocumentKind,
    ) {
        createMissingOperatorProfileFileIfNeeded(
            profilePath = profilePath,
            mandatoryContext = mandatoryContext,
            kind = kind,
            createdPaths = mutableListOf(),
        )
    }

    fun createMissingOperatorProfileFileIfNeeded(
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
        kind: LegalDocumentKind,
        createdPaths: MutableList<Path>,
    ) {
        val normalizedPath = profilePath.toAbsolutePath().normalize()
        if (Files.exists(normalizedPath)) {
            return
        }
        Files.createDirectories(normalizedPath.parent)
        Files.writeString(
            normalizedPath,
            renderOperatorProfileTemplate(
                mandatoryContext = mandatoryContext,
                kind = kind,
            ) + "\n",
            Charsets.UTF_8,
        )
        createdPaths.add(normalizedPath)
    }

    private fun renderOperatorProfileTemplate(
        mandatoryContext: MandatoryChainContext,
        kind: LegalDocumentKind,
    ): String {
        val analysis =
            templateCompletenessChecker.analyze(
                baselineCanonicalJson = mandatoryContext.conversion.baseline.canonicalJson,
                profileChainCanonicalJsons = mandatoryContext.profileCanonicalJsons,
            )
        val overridesYaml =
            analysis.skeletonYaml
                .lineSequence()
                .dropWhile { line -> line != "overrides:" }
                .joinToString("\n")
        return buildString {
            appendLine("kind: profile")
            appendLine("""id: "${kind.operatorProfileId}"""")
            appendLine("""version: "${kind.operatorProfileVersion}"""")
            appendLine("parent:")
            appendLine("""  id: "${mandatoryContext.lastProfile.id}"""")
            appendLine("""  version: "${mandatoryContext.lastProfile.version}"""")
            append(overridesYaml)
        }
    }
}
