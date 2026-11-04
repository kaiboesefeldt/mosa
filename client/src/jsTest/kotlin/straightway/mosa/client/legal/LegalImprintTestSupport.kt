package straightway.mosa.client.legal

import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.identity.privacy.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import straightway.mosa.legal.imprint.ImprintAddress
import straightway.mosa.legal.imprint.ImprintContactBlock
import straightway.mosa.legal.imprint.ImprintDocument
import straightway.mosa.legal.imprint.ImprintProviderBlock
import straightway.mosa.legal.LegalStatus

internal fun fixedImprintPodBaseProvider(base: String): PodBaseProvider =
    PodBaseProvider {
        val global = js("globalThis")
        global.asDynamic().MOSA_POD_BASE = base
        null
    }

internal fun fixedImprintPodAccessFactory(): PodAccessFactory = { RecordingImprintPodAccess() }

private class RecordingImprintPodAccess : PodAccess {
    override suspend fun legalStatus(): LegalStatus = LegalStatus(complete = true)

    override suspend fun approveGid(gid: String): PodCertificateResponse = error("unused")

    override suspend fun refreshGid(jwt: String): PodCertificateResponse = error("unused")

    override suspend fun publish(request: PublishRequest, jwt: String) = error("unused")

    override suspend fun reply(request: ReplyRequest, jwt: String) = error("unused")

    override suspend fun deletePosts(jwt: String): DeletePostsResult = error("unused")

    override suspend fun deletePost(postId: String, jwt: String): DeletePostsResult = error("unused")

    override suspend fun exportPostsCsv(jwt: String): ByteArray = error("unused")

    override suspend fun freezePrivacySnapshot(jwt: String): ByteArray = error("unused")

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult = error("unused")

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult = error("unused")

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult = error("unused")

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult = error("unused")

    override suspend fun imprint(): ImprintDocument =
        ImprintDocument(
            provider = ImprintProviderBlock(name = "Example Pod GmbH"),
            address =
                ImprintAddress(
                    street = "Main Street 1",
                    postalCode = "12345",
                    city = "Berlin",
                    country = "Deutschland",
                ),
            contact = ImprintContactBlock(email = "legal@example.invalid"),
        )

    override suspend fun privacyBaselineClearTerms(): String = error("unused")

    override suspend fun privacyDefaultClearTerms(): String = error("unused")

    override suspend fun privacyMosaClearTerms(): String = error("unused")

    override suspend fun privacyOperatorClearTerms(): String = error("unused")

    override suspend fun termsBaselineClearTerms(): String = error("unused")

    override suspend fun termsDefaultClearTerms(): String = error("unused")

    override suspend fun termsMosaClearTerms(): String = error("unused")

    override suspend fun termsOperatorClearTerms(): String = error("unused")

    override suspend fun termsServiceClearTerms(): String = error("unused")
}
