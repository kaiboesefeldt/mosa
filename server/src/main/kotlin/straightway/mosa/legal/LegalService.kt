package straightway.mosa.legal

import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter

class LegalService(
    private val converter: ClearTermsYamlCanonicalJsonConverter = ClearTermsYamlCanonicalJsonConverter(),
    private val operatorProfilePath: java.nio.file.Path,
    private val completenessChecker: ClearTermsProfileChainCompletenessChecker = ClearTermsProfileChainCompletenessChecker(),
) {
    private val documents: PrivacyLegalDocuments = loadPrivacyDocuments()

    fun privacyBaselineCanonicalJson(): String = documents.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = documents.defaultCanonicalJson

    fun privacyMosaCanonicalJson(): String = documents.mosaCanonicalJson

    fun privacyServiceCanonicalJson(): String = documents.serviceCanonicalJson

    private fun loadPrivacyDocuments(): PrivacyLegalDocuments {
        val baselineYaml = readRequiredResource(BASELINE_RESOURCE)
        val defaultYaml = readRequiredResource(DEFAULT_RESOURCE)
        val mosaYaml = readRequiredResource(MOSA_RESOURCE)
        val mandatoryConversion = converter.convert(baselineYaml, listOf(defaultYaml, mosaYaml))
        val mandatoryCanonicals = mandatoryConversion.profiles.map { artifact -> artifact.canonicalJson }
        val operatorYaml = readOptionalProfile(operatorProfilePath)
        if (operatorYaml == null) {
            val analysis =
                completenessChecker.analyze(
                    baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
                    profileChainCanonicalJsons = mandatoryCanonicals,
                )
            printProfileChainSkeleton(
                reason = "Operator profile file is missing at '${operatorProfilePath.toAbsolutePath().normalize()}'",
                skeletonYaml = analysis.skeletonYaml,
            )
            throw IllegalStateException("Missing required legal profile file '${operatorProfilePath.toAbsolutePath().normalize()}'")
        }

        val fullConversion =
            try {
                converter.convert(baselineYaml, listOf(defaultYaml, mosaYaml, operatorYaml))
            } catch (_: ClearTermsValidationException) {
                val analysis =
                    completenessChecker.analyze(
                        baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
                        profileChainCanonicalJsons = mandatoryCanonicals,
                    )
                printProfileChainSkeleton(
                    reason = "Operator profile is invalid and could not be converted",
                    skeletonYaml = analysis.skeletonYaml,
                )
                throw IllegalStateException("Legal profile chain is invalid")
            }

        val fullChainCanonicals = fullConversion.profiles.map { artifact -> artifact.canonicalJson }
        val analysis =
            completenessChecker.analyze(
                baselineCanonicalJson = fullConversion.baseline.canonicalJson,
                profileChainCanonicalJsons = fullChainCanonicals,
            )
        if (!analysis.complete) {
            printProfileChainSkeleton(
                reason = "Legal profile chain is incomplete",
                skeletonYaml = analysis.skeletonYaml,
            )
            throw IllegalStateException("Legal profile chain is incomplete")
        }

        return PrivacyLegalDocuments(
            baselineCanonicalJson = fullConversion.baseline.canonicalJson,
            defaultCanonicalJson = fullConversion.profiles[0].canonicalJson,
            mosaCanonicalJson = fullConversion.profiles[1].canonicalJson,
            serviceCanonicalJson = fullConversion.profiles[2].canonicalJson,
        )
    }

    private fun readRequiredResource(path: String): String =
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("Missing resource '$path'")

    private fun readOptionalProfile(path: java.nio.file.Path): String? {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!java.nio.file.Files
                .exists(normalizedPath)
        ) {
            return null
        }
        return java.nio.file.Files
            .readString(normalizedPath, Charsets.UTF_8)
    }

    private fun printProfileChainSkeleton(
        reason: String,
        skeletonYaml: String,
    ) {
        println("Legal profile chain completeness check failed: $reason")
        println("Suggested operator profile skeleton:")
        println(skeletonYaml)
    }

    private data class PrivacyLegalDocuments(
        val baselineCanonicalJson: String,
        val defaultCanonicalJson: String,
        val mosaCanonicalJson: String,
        val serviceCanonicalJson: String,
    )

    private companion object {
        private const val BASELINE_RESOURCE = "clearterms/privacy/privacy-gdpr-baseline.yaml"
        private const val DEFAULT_RESOURCE = "clearterms/privacy/privacy-gdpr-default.yaml"
        private const val MOSA_RESOURCE = "clearterms/privacy/mosa-privacy-gdpr-service.yaml"
    }
}
