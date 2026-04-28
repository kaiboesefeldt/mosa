# MVP Scope

This document defines the functional baseline for a public Mosa launch.
It is the reference point for deciding whether a feature is a launch blocker,
an immediate follow-up, or a later enhancement.

## Launch Posture

Mosa can be public in two different senses:

1. Single-pod public launch
2. Federated public network launch

The MVP baseline in this document assumes the first posture unless a task
explicitly targets federation.

If Mosa is launched as a federated network, multi-pod access becomes part of
the MVP baseline. If Mosa is launched as a single public pod first, multi-pod
access can remain a follow-up.

## MVP Must-Haves Inventory

This is a broader launch checklist, intentionally independent from the current
task backlog. Use it to spot missing tasks or missing acceptance criteria.

### Identity and Session

- A user can create or load a local identity.
- Identity state survives browser restart.
- The active identity can be switched without corrupting other local profiles.
- Browser-local data is separated per identity where the product stores user
  state locally.
- The client can re-establish a valid authenticated session with the pod.

### Pod Access

- The client can connect to a configured pod and keep that connection usable.
- Authentication and certificate flows are understandable to users.
- A pod can reject access cleanly when a session, identity, or certificate is
  no longer valid.
- Public launch should not require manual operator intervention for normal
  session refresh or reconnection.

### Core Content Loop

- Users can publish content.
- Users can reply to content.
- Users can read content they published or received through search and fetch
  flows.
- Users can find content through semantic or topic-oriented search.
- Users can delete their own content where the product promises that behavior.
- Content signatures and content verification remain consistent across client
  and server.

### Moderation and Safety

- Users can report problematic content.
- Admins can review reports and act on them.
- Admins can block, unblock, and delete identities where the product exposes
  those actions.
- Admin communication to users is available for moderation outcomes and other
  relevant operational messages.
- Moderation-related state remains consistent across deletion and freeze flows.
- The public product has a clear answer for what happens to misuse, spam, and
  operator mistakes.

### Legal and Trust

- Users can view the current imprint, privacy policy, and terms.
- Legal document changes require explicit confirmation before continued use.
- Confirmed legal versions are stored per identity and survive restart.
- Legal PDFs are available for all public legal documents the product offers.
- ClearTerms content can be verified with stable trust anchors.

### Client Experience

- The client shows understandable errors for common failure modes.
- The UI remains usable on the intended supported browsers.
- Long-lived client state does not leak between different local identities.
- The user can recover from a temporary failure without losing their place more
  than necessary.

### Data Persistence and Recovery

- Browser state, server state, and structured pod state persist across restart.
- The product has a clear story for backup, export, and restore of user-owned
  data.
- A pod restart does not silently lose normal user data.

### Operational Baseline

- The pod starts and serves the client bundle reliably.
- The pod has a documented and practical configuration path.
- The server can degrade safely under load rather than failing unpredictably.
- Operators can identify what is broken when a public launch issue happens.

## Current Core Coverage

The repository already covers the main content loop:

- identity and pod authentication
- publishing and replying
- semantic and thread-based search
- post complaints and admin decisions
- admin messages
- profile block, unblock, and delete actions
- profile freeze and restore flows
- legal document rendering and delivery

## Public Launch Must-Haves

These items should be in place before a public launch that users are expected
to trust and use without caveats.

### Legal and Trust

- Legal document changes must require explicit user confirmation before normal
  pod usage continues.
- The confirmed legal document version must be stored per active identity and
  survive browser restarts.
- Users must be able to read and download the current legal documents in a
  stable format.
- ClearTerms baselines and profile chains must be independently verifiable.

### Client Usability

- The client must present server and network errors in a user-readable way.
- Common failures such as `503` and `422` need explicit treatment.
- The client must keep per-profile browser state separated where it is stored
  locally, including topic state.

### Moderation and Admin Operations

- Admins must be able to delete previously sent admin messages.
- Moderation data must remain consistent across delete and freeze flows.
- The public-facing admin path must not leave obvious recovery gaps for
  operator mistakes.

## Launch-Conditional Item

Multi-pod access is conditional:

- required for a federated launch
- not required for a single-pod public launch

## Can Follow After Launch

These items are useful, but they do not define the minimum public launch bar.

- topic and issue filters
- browser automation for UI smoke tests
- richer moderation history and audit trail
- broader admin console consolidation
- additional client-side convenience features

## Relevant Open Tasks

- [002-LegalDocumentChangeConfirmationFlow](../tasks/1-open/002-LegalDocumentChangeConfirmationFlow/Description.md)
- [005-TopicAndIssueFilters](../tasks/1-open/005-TopicAndIssueFilters/Description.md)
- [010-ClearTermsSignaturesForBaselinesAndProfileChains](../tasks/1-open/010-ClearTermsSignaturesForBaselinesAndProfileChains/Description.md)
- [011-MultiPodAccessByClient](../tasks/1-open/011-MultiPodAccessByClient/Description.md)
- [021-BetterClientErrorPresentation](../tasks/1-open/021-BetterClientErrorPresentation/Description.md)
- [024-AllowAdminDeleteAdminMessages](../tasks/1-open/024-AllowAdminDeleteAdminMessages/Description.md)
- [028-PerBrowserProfileTopics](../tasks/1-open/028-PerBrowserProfileTopics/Description.md)
- [039-ModerationAndAdminMessaging](../tasks/1-open/039-ModerationAndAdminMessaging/Description.md)
- [044-LegalPdfExportForAdditionalDocuments](../tasks/1-open/044-LegalPdfExportForAdditionalDocuments/Description.md)

## Working Rule

When a change request touches launch readiness, check this document first.
If a feature is not a blocker here, treat it as a follow-up unless the user
explicitly asks for a broader scope.
