package straightway.mosa.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.browser.BrowserPrefs
import straightway.mosa.client.browser.ObjectUrlFactory
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.browser.WindowObjectUrlFactory
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.CryptoExport
import straightway.mosa.client.identity.IdentityGenerator
import straightway.mosa.client.identity.IdentityProvider
import straightway.mosa.client.identity.IdentityRecordMapper
import straightway.mosa.client.identity.IdentityRepository
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityStore
import straightway.mosa.client.identity.IdentityTransferCodec
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.identity.IdentityTransferService
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.identity.IndexedDbIdentityStore
import straightway.mosa.client.identity.IndexedDbSchemaManager
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.logging.BrowserLogger
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodAccessImpl
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.topic.BrowserTopicFilterStore
import straightway.mosa.client.topic.IndexedDbTopicStore
import straightway.mosa.client.topic.JwtBuilder
import straightway.mosa.client.topic.PodAccessFactory
import straightway.mosa.client.topic.PostPublisher
import straightway.mosa.client.topic.TopicRepository
import straightway.mosa.client.topic.TopicSearchService
import straightway.mosa.client.topic.TopicFilterStore
import straightway.mosa.client.topic.TopicStore
import straightway.mosa.client.ui.BrowserIdentitySessionStore
import straightway.mosa.client.ui.IdentitySessionStore
import straightway.mosa.identity.FictionalNameGenerator
import web.idb.IDBFactory
import web.idb.indexedDB

object AppModule : KoinComponent {
    fun start() {
        val alreadyStarted = runCatching { GlobalContext.get() }.isSuccess
        if (!alreadyStarted) {
            startKoin {
                modules(
                    module {
                        single { TimeService() }
                        single {
                            MisJwtService(
                                get(),
                                get(),
                                get()
                            )
                        }
                        single { IdentityGenerator(get(), get()) }
                        single<IdentityProvider> { get<IdentityGenerator>() }
                        single<IDBFactory?> { browserIndexedDb() }
                        single { IndexedDbSchemaManager(get()) }
                        single { IndexedDbAsyncBridge() }
                        single { IdentityRecordMapper(get(), get()) }
                        single<IdentityStore> {
                            IndexedDbIdentityStore(
                                get(),
                                get(),
                                get(),
                                get(),
                            )
                        }
                        single<IdentitySelectionRepository> { IdentityRepository(get(), get<IdentityProvider>()) }
                        single<IdentitySessionStore> { BrowserIdentitySessionStore() }
                        single {
                            HttpClient {
                                install(ContentNegotiation) { json() }
                            }
                        }
                        single { PodBaseProvider() }
                        factory<PodAccess> { PodAccessImpl(it[0], get()) }
                        single<PodAccessFactory> {
                            { podBase -> get<PodAccess> { parametersOf(podBase) } }
                        }
                        single {
                            SubtleCrypto(
                                window.asDynamic().crypto?.subtle
                                    ?: error("WebCrypto not available")
                            )
                        }
                        single { CryptoExport(get()) }
                        single<JwtBuilder> {
                            { identity, certificate ->
                                get<MisJwtService>().buildMisJwt(identity, certificate)
                            }
                        }
                        single {
                            IdentityTransferCodec(
                                get(),
                                get(),
                                Json { prettyPrint = true },
                            )
                        }
                        single<ObjectUrlFactory> { WindowObjectUrlFactory() }
                        single<IdentityTransferPort> { IdentityTransferService(get(), get()) }
                        single { FictionalNameGenerator() }
                        single<TopicStore> { IndexedDbTopicStore(get(), get(), get()) }
                        single<TopicFilterStore> { BrowserTopicFilterStore() }
                        single { TopicRepository(get()) }
                        single { PostPublisher(get(), get(), get(), get(), get()) }
                        single {
                            TopicSearchService(
                                get(),
                                get(),
                                get(),
                                get(),
                                get(),
                            )
                        }
                        single { BrowserPrefs() }
                        single { Translator(get<BrowserPrefs>().languages) }
                        single { DiceBearAvatarGenerator() }
                        single<Logger> { BrowserLogger() }
                    },
                )
            }
        }
    }
}

private fun browserIndexedDb(): IDBFactory? =
    if ((js("typeof indexedDB === 'undefined'") as Boolean)) {
        null
    } else {
        indexedDB
    }
