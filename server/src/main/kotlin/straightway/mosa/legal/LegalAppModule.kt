package straightway.mosa.legal

import org.koin.core.module.Module
import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import straightway.mosa.base.BaseController
import straightway.mosa.legal.clearterms.ClearTermsCanonicalNodeRenderer
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.ClearTermsProfileValidator
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.ClearTermsYamlLanguageFragmentMerger
import java.nio.file.Path

fun Module.configureLegalAppModule() {
    single(legalPrivacyProfilePathQualifier) { Path.of("mosa_privacy.yaml") }
    single(legalTermsProfilePathQualifier) { Path.of("mosa_terms.yaml") }
    single(legalImprintPathQualifier) { Path.of("mosa_imprint.yaml") }
    single {
        ClearTermsYamlCanonicalJsonConverter()
    }
    single {
        ClearTermsYamlLanguageFragmentMerger()
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
        ClearTermsCanonicalNodeRenderer()
    }
    single<ResourceTextReader> {
        ResourceTextReader { path ->
            LegalService::class.java.classLoader
                .getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }
    }
    single<ResourceDirectoryEntriesProvider> {
        ResourceDirectoryEntriesProvider { null }
    }
    single<ResourceUrlsProvider> {
        ResourceUrlsProvider { path ->
            LegalService::class.java.classLoader.getResources(path)
        }
    }
    single {
        LegalProfileSource(
            canonicalJsonConverter = get(),
            languageFragmentMerger = get(),
            resourceTextReader = get(),
            resourceDirectoryEntriesProvider = get(),
            resourceUrlsProvider = get(),
        )
    }
    single {
        LegalChainValidator(
            completenessChecker = get(),
            parser = get(),
            reader = get(),
            profileValidator = get(),
            profileSource = get(),
        )
    }
    single {
        LegalOperatorProfileTemplateWriter(templateCompletenessChecker = get())
    }
    single {
        LegalDocumentFactory(
            parser = get(),
            reader = get(),
            profileValidator = get(),
            canonicalNodeRenderer = get(),
        )
    }
    single(createdAtStart = true) {
        ImprintService(path = get(legalImprintPathQualifier)).also { it.validateStartup() }
    }
    single(createdAtStart = true) {
        LegalService(
            operatorProfilePath = get(legalPrivacyProfilePathQualifier),
            termsOperatorProfilePath = get(legalTermsProfilePathQualifier),
            profileSource = get(),
            chainValidator = get(),
            templateWriter = get(),
            documentFactory = get(),
        ).also { it.validateStartup() }
    }
    single { LegalController(get(), get()) } bind BaseController::class
}

private val legalPrivacyProfilePathQualifier: StringQualifier = named("legalPrivacyProfilePath")
private val legalTermsProfilePathQualifier: StringQualifier = named("legalTermsProfilePath")
private val legalImprintPathQualifier: StringQualifier = named("legalImprintPath")
