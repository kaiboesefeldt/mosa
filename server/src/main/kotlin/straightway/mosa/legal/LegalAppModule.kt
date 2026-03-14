package straightway.mosa.legal

import org.koin.core.module.Module
import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import straightway.mosa.base.BaseController
import straightway.mosa.legal.clearterms.ClearTermsCanonicalJson
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.ClearTermsProfileValidator
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import java.nio.file.Path

fun Module.configureLegalAppModule() {
    single(legalPrivacyProfilePathQualifier) { Path.of("mosa_privacy.yaml") }
    single(legalTermsProfilePathQualifier) { Path.of("mosa_terms.yaml") }
    single(legalImprintPathQualifier) { Path.of("imprint.yaml") }
    single {
        ClearTermsYamlCanonicalJsonConverter()
    }
    single {
        ClearTermsProfileChainCompletenessChecker()
    }
    single {
        ClearTermsJsonParser()
    }
    single {
        ClearTermsDocumentReader()
    }
    single {
        ClearTermsProfileValidator(placeholderParser = get())
    }
    single {
        ClearTermsPlaceholderParser()
    }
    single {
        ClearTermsCanonicalJson()
    }
    single(createdAtStart = true) {
        ImprintService(path = get(legalImprintPathQualifier)).also { it.validateStartup() }
    }
    single(createdAtStart = true) {
        LegalService(
            canonicalJsonConverter = get(),
            completenessChecker = get(),
            operatorProfilePath = get(legalPrivacyProfilePathQualifier),
            termsOperatorProfilePath = get(legalTermsProfilePathQualifier),
            parser = get(),
            reader = get(),
            profileValidator = get(),
            canonicalJson = get(),
        ).also { it.validateStartup() }
    }
    single { LegalController(get(), get()) } bind BaseController::class
}

private val legalPrivacyProfilePathQualifier: StringQualifier = named("legalPrivacyProfilePath")
private val legalTermsProfilePathQualifier: StringQualifier = named("legalTermsProfilePath")
private val legalImprintPathQualifier: StringQualifier = named("legalImprintPath")
