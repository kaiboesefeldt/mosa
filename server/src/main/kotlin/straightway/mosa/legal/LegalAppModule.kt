package straightway.mosa.legal

import org.koin.core.module.Module
import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import straightway.mosa.base.BaseController
import straightway.mosa.legal.clearterms.canonical.CanonicalNodeRenderer
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.yaml.YamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.yaml.YamlLanguageFragmentMerger
import java.nio.file.Path

fun Module.configureLegalAppModule() {
    single(legalPrivacyProfilePathQualifier) { Path.of("mosa_privacy.yaml") }
    single(legalTermsProfilePathQualifier) { Path.of("mosa_terms.yaml") }
    single(legalImprintPathQualifier) { Path.of("mosa_imprint.yaml") }
    single {
        YamlCanonicalJsonConverter()
    }
    single {
        YamlLanguageFragmentMerger()
    }
    single {
        ProfileChainCompletenessChecker()
    }
    single {
        JsonParser()
    }
    single {
        DocumentReader()
    }
    single {
        ProfileValidator(placeholderParser = get())
    }
    single {
        PlaceholderParser()
    }
    single {
        CanonicalNodeRenderer()
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
    single {
        ImprintService(path = get(legalImprintPathQualifier))
    }
    single {
        LegalService(
            operatorProfilePath = get(legalPrivacyProfilePathQualifier),
            termsOperatorProfilePath = get(legalTermsProfilePathQualifier),
            profileSource = get(),
            chainValidator = get(),
            templateWriter = get(),
            documentFactory = get(),
        )
    }
    single(createdAtStart = true) {
        val legalService = get<LegalService>()
        val imprintService = get<ImprintService>()
        LegalStartupStatus(legalService::validateStartup, imprintService::validateStartup)
    }
    single { LegalController(get(), get(), get()) } bind BaseController::class
}

private val legalPrivacyProfilePathQualifier: StringQualifier = named("legalPrivacyProfilePath")
private val legalTermsProfilePathQualifier: StringQualifier = named("legalTermsProfilePath")
private val legalImprintPathQualifier: StringQualifier = named("legalImprintPath")
