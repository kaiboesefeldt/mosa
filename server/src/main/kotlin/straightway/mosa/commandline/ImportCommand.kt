package straightway.mosa.commandline

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.HasherFactory
import straightway.mosa.VectorStore
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.configureMosaAppModule
import straightway.mosa.content.ContentService
import straightway.mosa.content.ContentService.RootNotFoundException
import straightway.mosa.content.ContentSignatureVerifier
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.io.InputStream
import java.security.Signature
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal class ImportCommand(
    private val streamIO: PostDumpStreamIO,
    private val input: InputStream,
    private val exit: (Int) -> Nothing = ::exitProcess,
    private val hasherFactory: HasherFactory,
) : Subcommand("import", "Import the mosa posts and replies from stdin") {
    private val contentSignatureVerifier =
        ContentSignatureVerifier(
            crypto = CryptoEd25519(),
            hasherFactory = hasherFactory,
            signatureVerifierProvider = { Signature.getInstance("Ed25519") },
        )

    override fun execute() {
        val koinApplication =
            startKoin {
                modules(module { configureMosaAppModule(CommandLineArgumentStore.EMPTY) })
            }
        val contentService: ContentService = koinApplication.koin.get()
        try {
            runBlocking {
                val idTable = mutableMapOf<String, String>()
                val postponed = mutableListOf<VectorStore.PostDump>()
                var importCount = 0
                var overallCount = 0
                input.bufferedReader().use { stdin ->
                    val reader = streamIO.readerFrom(stdin)
                    reader.asFlow().collect { postDump ->
                        ++overallCount
                        if (contentService.import(postDump, idTable) == ImportState.Failure) {
                            postponed.add(postDump)
                        } else {
                            importCount++
                        }
                    }
                }
                var lastPostponedSize = Int.MAX_VALUE
                while (postponed.size < lastPostponedSize) {
                    lastPostponedSize = postponed.size
                    val p = postponed.toList()
                    postponed.clear()
                    p.forEach { postDump ->
                        if (contentService.import(postDump, idTable) == ImportState.Failure) {
                            postponed.add(postDump)
                        } else {
                            ++importCount
                        }
                    }
                }
                logger.info { "Imported $importCount of $overallCount posts and replies." }
                check(postponed.isEmpty()) { "Could not import these posts: $postponed" }
            }
        } finally {
            koinApplication.close()
        }
        exit(0)
    }

    private enum class ImportState { Success, Failure }

    private suspend fun ContentService.import(
        post: VectorStore.PostDump,
        idTable: MutableMap<String, String>,
    ): ImportState {
        val rawAuthorGid = post.requireField(AUTHOR_GID_KEY)
        val authorPublicKey = post.requireField(AUTHOR_PUBLIC_KEY_KEY)
        val authorGid = normalizeAuthorGid(rawAuthorGid, authorPublicKey)
        val publishRequest = post.toPublishRequest()
        val rootId = post.fields[ROOT_ID_KEY]
        val receiveTimestamp = post.fields[RECEIVED_AT_KEY]?.toLong() ?: 0L
        val id =
            try {
                val principal = authorPrincipal(authorGid, authorPublicKey)
                if (rootId == null) {
                    contentSignatureVerifier.verifyPublish(principal, publishRequest)
                    publish(authorGid, publishRequest, receiveTimestamp, authorPublicKey)
                } else {
                    val realRootId = idTable.getOrDefault(rootId, rootId)
                    val replyRequest =
                        ReplyRequest(
                            publishRequest,
                            realRootId,
                        )
                    contentSignatureVerifier.verifyReply(principal, replyRequest)
                    reply(
                        authorGid,
                        replyRequest,
                        receiveTimestamp,
                        authorPublicKey,
                    )
                }
            } catch (_: RootNotFoundException) {
                return ImportState.Failure
            }

        if (id != post.id) idTable[post.id] = id
        return ImportState.Success
    }

    private fun VectorStore.PostDump.toPublishRequest(): PublishRequest =
        PublishRequest(
            text = text,
            signature = requireField(AUTHOR_SIGNATURE_KEY),
            signatureVersion = requireField(SIGNATURE_VERSION_KEY).toInt(),
        )

    private fun VectorStore.PostDump.requireField(fieldName: String): String =
        fields[fieldName] ?: throw RuntimeException("user without field value for `$fieldName`")

    private fun normalizeAuthorGid(
        authorGid: String,
        authorPublicKey: String,
    ): String {
        val authorPublicKeyBytes = decodeBase64Url(authorPublicKey) ?: throw RuntimeException("authorPublicKey is not valid base64url")
        val normalizedAuthorGid =
            decodeBase64Url(authorGid)?.let(::encodeBase64Url) ?: run {
                val hasher = hasherFactory()
                val hash = hasher.digest(authorGid.toByteArray())
                encodeBase64Url(hash)
            }
        val expectedAuthorGid = encodeBase64Url(hasherFactory().digest(authorPublicKeyBytes))
        if (normalizedAuthorGid.isNotEmpty() && normalizedAuthorGid != expectedAuthorGid) {
            throw RuntimeException("authorGID does not match authorPublicKey")
        }
        return normalizedAuthorGid
    }

    private fun decodeBase64Url(value: String): ByteArray? =
        runCatching {
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .decode(value)
        }.getOrNull()

    private fun encodeBase64Url(value: ByteArray): String =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(value)

    private fun authorPrincipal(
        authorGid: String,
        authorPublicKey: String,
    ) = PodPrincipal(
        certificate = PodCertificate(gid = authorGid, exp = Long.MAX_VALUE, sig = ""),
        publicKeyBase64 = authorPublicKey,
    )

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
