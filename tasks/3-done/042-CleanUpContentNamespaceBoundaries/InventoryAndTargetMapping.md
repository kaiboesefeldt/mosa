## Ownership Decisions

- `straightway.mosa.content` remains the owner of publishing, replies, fetch, semantic and thread queries, content signatures, content export, and post deletion.
- Blocked-identity state is owned by `straightway.mosa.identity.blocklist`.
- Identity moderation is owned by `straightway.mosa.identity.moderation`.
- Identity deletion and privacy snapshot flows are owned by `straightway.mosa.identity.privacy`.
- Snapshot implementation details are owned by `straightway.mosa.identity.privacy.snapshot`.
- Shared structured-state persistence infrastructure is owned on the server by `straightway.mosa.structuredstate`.
- Shared protocol packages should mirror stable cross-module boundaries. Server and client implementation packages may differ locally, but they should depend on shared packages whose ownership is explicit.

## Shared Mapping

Stays in `straightway.mosa.content`:

- `ContentCapabilitiesResult`
- `ContentKind`
- `ContentSignaturePayload`
- `ContentSignatureVersion`
- `DeletePostsResult`
- `FetchRequest`
- `FetchResult`
- `PublishRequest`
- `QueryOnRequest`
- `QueryRequest`
- `QueryResult`
- `ReplyRequest`
- `SearchResult`

Stays in `straightway.mosa.content.complaint`:

- `CreatePostComplaintRequest`
- `DecidePostComplaintRequest`
- `OpenPostComplaint`
- `OpenPostComplaintCountResult`
- `OpenPostComplaintsResult`
- `PostComplaintAvailability`
- `PostComplaintAvailabilityResult`
- `PostComplaintDecisionSignaturePayload`
- `PostComplaintPayloadType`
- `PostComplaintSignaturePayload`
- `PostComplaintSignatureVersion`
- `PostComplaintState`

Moves to `straightway.mosa.identity.moderation`:

- `AdminIdentityModerationStatus`
  Reason: admin-side block status and admin eligibility are identity moderation concerns, not content concerns.
- `IdentityModerationRequest`
  Reason: the request targets a `gid`, not a content object.

Moves to `straightway.mosa.identity.privacy`:

- `DeleteIdentityResult`
  Reason: the result describes destructive identity lifecycle cleanup across posts, complaints, and admin messages.
- `FreezeSnapshotRestoreResult`
  Reason: restore is part of the privacy lifecycle, not of content querying or publishing.

## Server Mapping

Stays in `straightway.mosa.content`:

- `ContentCommandLineOptions`
- `ContentController`
- `ContentCsvExportService`
- `ContentDeleteService`
- `ContentLimits`
- `ContentMetadataField`
- `ContentReadService`
- `ContentService`
- `ContentSignatureVerifier`
- `InvalidAuthenticatedPublicKeyException`
- `InvalidContentAuthorException`
- `InvalidContentSignatureException`
- `InvalidContentSignatureFormatException`
- `MissingVectorMetadataException`
- `UnprocessableContentException`
- `UnsupportedContentSignatureVersionException`

Stays in `straightway.mosa.content.complaint`:

- `InvalidPostComplaintSignatureException`
- `InvalidPostComplaintSignatureFormatException`
- `PostComplaintConflictException`
- `PostComplaintController`
- `PostComplaintForbiddenException`
- `PostComplaintRecord`
- `PostComplaintService`
- `PostComplaintSignatureVerifier`
- `PostComplaintStore`
- `PostComplaintStoreFactory`
- `PostComplaintsTable`
- `SqlitePostComplaintStore`
- `UnsupportedPostComplaintSignatureVersionException`

Moves to `straightway.mosa.identity.blocklist`:

- `BlockedIdentitiesTable`
- `BlockedIdentityStore`
- `SqliteBlockedIdentityStore`
  Reason: these files represent reusable identity blocklist state consumed by auth, defense, moderation, and identity privacy flows. They are not owned by moderation.

Moves to `straightway.mosa.identity.moderation`:

- `IdentityModerationController`
- `IdentityModerationService`
  Reason: these files implement admin-side moderation actions over identities, but they should depend on the neutral blocklist capability instead of owning it.

Moves to `straightway.mosa.identity.privacy`:

- `IdentityDeleter`
- `IdentityDeletionController`
- `IdentityDeletionService`
- `PrivacySnapshotController`
  Reason: these files expose or orchestrate privacy lifecycle actions on an identity.

Moves to `straightway.mosa.identity.privacy.snapshot`:

- `FileBackedFreezeSnapshotArchiveKeyStore`
- `FreezeSnapshot`
- `FreezeSnapshotActiveArchiveKeyProvider`
- `FreezeSnapshotAdminMessageRecord`
- `FreezeSnapshotArchiveKey`
- `FreezeSnapshotArchiveKeyStore`
- `FreezeSnapshotComplaintRecord`
- `FreezeSnapshotContentRecord`
- `FreezeSnapshotCryptoService`
- `FreezeSnapshotFormat`
- `FreezeSnapshotHeader`
- `FreezeSnapshotJson`
- `FreezeSnapshotManifest`
- `FreezeSnapshotNonceGenerator`
- `FreezeSnapshotPolicy`
- `FreezeSnapshotRecord`
- `FreezeSnapshotRestoreService`
- `FreezeSnapshotRestoreValidator`
- `FreezeSnapshotSelectionService`
- `FreezeSnapshotService`
- `FreezeSnapshotSizePolicy`
- `InvalidFreezeSnapshotException`
- `SecureRandomFreezeSnapshotNonceGenerator`
  Reason: these files already form a coherent snapshot implementation slice; only the owner package is wrong.

Moves to `straightway.mosa.structuredstate`:

- `StructuredStateContext`
- `StructuredStateDatabase`
  Reason: these files are cross-feature structured persistence infrastructure used by complaints, admin messages, identity moderation, identity deletion, and snapshot restore.

Needs split instead of a single move:

- `ContentAppModule`
  Reason: it currently wires content, complaint handling, admin messaging, identity moderation, identity privacy, snapshot infrastructure, and structured-state persistence in one file.
  Target shape:
  - keep a reduced `configureContentAppModule` in `straightway.mosa.content`
  - introduce `configureIdentityBlocklistModule` in `straightway.mosa.identity.blocklist`
  - introduce `configureIdentityModerationModule` in `straightway.mosa.identity.moderation`
  - introduce `configureIdentityPrivacyModule` in `straightway.mosa.identity.privacy`
  - introduce `configureStructuredStateModule` in `straightway.mosa.structuredstate`

## Client Mapping

Stays in `straightway.mosa.client.content`:

- `AllPostsDeletionService`
- `CachedPost`
- `CachedPostResolution`
- `ContentCapabilitiesService`
- `ContentSignatureVerifier`
- `ContentSigner`
- `EntryResolution`
- `IndexedDbPostCache`
- `IndexedDbReplyCache`
- `IndexedDbTopicStore`
- `PostCache`
- `PostCacheSnapshot`
- `PostDeletionService`
- `PostPublisher`
- `PostsCsvDownloader`
- `PostsExportPort`
- `PostsExportService`
- `ReplyCache`
- `ReplyCacheSnapshot`
- `ReplyEntryRef`
- `ReplyPublisher`
- `ReplyRecord`
- `ReplyReferenceStore`
- `ReplySearchService`
- `ThreadReplies`
- `ThreadScopedReply`
- `Topic`
- `TopicEntryRef`
- `TopicEntryStore`
- `TopicFilters`
- `TopicPosts`
- `TopicRecord`
- `TopicReferenceStore`
- `TopicRepository`
- `TopicScopedPost`
- `TopicSearchService`
- `TopicStore`

Stays in `straightway.mosa.client.content.complaint`:

- `AdminPostComplaintService`
- `PostComplaintService`
- `PostComplaintSigner`

Moves to `straightway.mosa.client.identity.moderation`:

- `AdminIdentityModerationStatusService`
- `IdentityModerationService`
  Reason: these client services call identity moderation endpoints and consume identity moderation contracts.

Moves to `straightway.mosa.client.identity.privacy`:

- `IdentityDeletionService`
- `PrivacySnapshotPort`
- `PrivacySnapshotService`
- `PrivacySnapshotTransferPort`
- `PrivacySnapshotTransferService`
  Reason: these files implement privacy lifecycle actions for the active identity.

Moves to `straightway.mosa.client.message`:

- `AdminMessageHistoryService`
- `AdminMessageInboxService`
- `AdminMessagePublisher`
  Reason: these files already depend on the message boundary and only live in `client.content` by historical accident.

Moves to `straightway.mosa.client.identity`:

- `JwtBuilder`
  Reason: the typealias builds JWTs from `Identity` and `PodCertificate`, which is identity/auth plumbing rather than content behavior.

Moves to `straightway.mosa.client.pod`:

- `PodAccessFactory`
  Reason: it is a constructor alias for `PodAccess`, not a content abstraction.

## Sequencing Notes For Implementation

1. Move shared contracts first so server and client imports can converge on the new stable package boundaries.
2. Move server structured-state infrastructure before updating moderation and privacy services, because both depend on it.
3. Split `ContentAppModule` during the server move instead of carrying the old ownership leak into the new package tree.
4. Move client services after shared and server packages stabilize.
5. Update `spec/Architecture.md` in the same implementation change that applies the package cleanup.

## Impacted Consumers Checklist

Revisit these consumers explicitly during the first code-moving increment so the cleanup does not stop at the moved
source files themselves.

`StructuredStateContext` / `StructuredStateDatabase`:

- `server/src/main/kotlin/straightway/mosa/content/complaint/PostComplaintStoreFactory.kt`
- `server/src/main/kotlin/straightway/mosa/content/complaint/SqlitePostComplaintStore.kt`
- `server/src/main/kotlin/straightway/mosa/message/AdminMessageStoreFactory.kt`
- `server/src/main/kotlin/straightway/mosa/message/SqliteAdminMessageStore.kt`
- `server/src/main/kotlin/straightway/mosa/content/snapshot/FreezeSnapshotSelectionService.kt`
- `server/src/main/kotlin/straightway/mosa/content/snapshot/FreezeSnapshotService.kt`
- `server/src/main/kotlin/straightway/mosa/content/snapshot/FreezeSnapshotRestoreService.kt`

`BlockedIdentityStore`:

- `server/src/main/kotlin/straightway/mosa/auth/AuthenticationController.kt`
- `server/src/main/kotlin/straightway/mosa/auth/AuthAppModule.kt`
- `server/src/main/kotlin/straightway/mosa/defense/DefenseRoutingPlugins.kt`
- `server/src/main/kotlin/straightway/mosa/defense/blocklist/AuthenticatedIdentityBlockListPlugin.kt`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt`
- `server/src/main/kotlin/straightway/mosa/content/IdentityModerationService.kt`

`PodAccessFactory`:

- `client/src/jsMain/kotlin/straightway/mosa/client/pod/PodSessionProvider.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/AppModule.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalImprintService.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPrivacyPolicyService.kt`

`JwtBuilder`:

- `client/src/jsMain/kotlin/straightway/mosa/client/pod/PodSessionProvider.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/AppModule.kt`

`PrivacySnapshotPort` and transfer ports:

- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySession.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySessionStateMachine.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/AppModule.kt`

Client admin-message services:

- `client/src/jsMain/kotlin/straightway/mosa/client/AppModule.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/IdentityConnectedView.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/admin/AdminController.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/admin/AdminActions.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/admin/AdminActionParameters.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/moderation/ModerationInboxController.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/moderation/ModerationActionParameters.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/moderation/ModerationControllerParameters.kt`

Shared moderation and privacy DTOs:

- `server/src/main/kotlin/straightway/mosa/content/IdentityModerationController.kt`
- `server/src/main/kotlin/straightway/mosa/content/IdentityModerationService.kt`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionController.kt`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeleter.kt`
- `server/src/main/kotlin/straightway/mosa/content/snapshot/FreezeSnapshotRestoreService.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/pod/PodAccess.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/content/AdminIdentityModerationStatusService.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/content/IdentityModerationService.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/content/IdentityDeletionService.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/content/PrivacySnapshotPort.kt`
- `client/src/jsMain/kotlin/straightway/mosa/client/content/PrivacySnapshotService.kt`

Nested moderation exceptions:

- `server/src/main/kotlin/straightway/mosa/network/NetworkServerPlugin.kt`
