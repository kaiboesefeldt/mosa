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

Status legend:

- `done` = `✅`
- `partial` = `🟡`
- `open` = `🔴`

### Identity and Session

- A user can create or load a local identity. Status: done ✅.
- Identity state survives browser restart. Status: done ✅.
- The active identity can be switched without corrupting other local profiles. Status: done ✅.
- Browser-local data is separated per identity where the product stores user
  state locally. Status: partial 🟡.
- The client can re-establish a valid authenticated session with the pod. Status: done ✅.

### Pod Access

- The client can connect to a configured pod and keep that connection usable. Status: done ✅.
- Authentication and certificate flows are understandable to users. Status: partial 🟡.
- A pod can reject access cleanly when a session, identity, or certificate is
  no longer valid. Status: done ✅.
- Public launch should not require manual operator intervention for normal
  session refresh or reconnection. Status: done ✅.

### Core Content Loop

- Users can publish content. Status: done ✅.
- Users can reply to content. Status: done ✅.
- Users can read content they published or received through search and fetch
  flows. Status: done ✅.
- Users can find content through semantic or topic-oriented search. Status: done ✅.
- Users can delete their own content where the product promises that behavior. Status: done ✅.
- Content signatures and content verification remain consistent across client
  and server. Status: done ✅.

### Moderation and Safety

- Users can report problematic content. Status: done ✅.
- Admins can review reports and act on them. Status: done ✅.
- Admins can block, unblock, and delete identities where the product exposes
  those actions. Status: done ✅.
- Admin communication to users is available for moderation outcomes and other
  relevant operational messages. Status: done ✅.
- Moderation-related state remains consistent across deletion and freeze flows. Status: done ✅.
- The public product has a clear answer for what happens to misuse, spam, and
  operator mistakes. Status: partial 🟡.

### Legal and Trust

- Users can view the current imprint, privacy policy, and terms. Status: done ✅.
- Legal document changes require explicit confirmation before continued use. Status: open 🔴.
- Confirmed legal versions are stored per identity and survive restart. Status: open 🔴.
- Legal PDFs are available for all public legal documents the product offers. Status: partial 🟡.
- ClearTerms content can be verified with stable trust anchors. Status: open 🔴.

### Client Experience

- The client shows understandable errors for common failure modes. Status: open 🔴.
- The UI remains usable on the intended supported browsers. Status: partial 🟡.
- Long-lived client state does not leak between different local identities. Status: open 🔴.
- The user can recover from a temporary failure without losing their place more
  than necessary. Status: partial 🟡.

### Data Persistence and Recovery

- Browser state, server state, and structured pod state persist across restart. Status: done ✅.
- The product has a clear story for backup, export, and restore of user-owned
  data. Status: done ✅.
- A pod restart does not silently lose normal user data. Status: done ✅.

### Operational Baseline

- The pod starts and serves the client bundle reliably. Status: done ✅.
- The pod has a documented and practical configuration path. Status: done ✅.
- The server can degrade safely under load rather than failing unpredictably. Status: partial 🟡.
- Operators can identify what is broken when a public launch issue happens. Status: partial 🟡.

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
