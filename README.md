# Mosa

Mosa is a social network organized around **semantic topics**: people describe their interests in text, and posts are found by relevance rather than social connections. Its design uses independent servers (pods) and a web client.

![Mosa web client showing topic filters, a feed of matching posts, and the replies panel](docs/images/mosa-screenshot.png)

## Quick facts

- **Structure:** a Ktor server, Kotlin/JS client, and shared Kotlin models; Lucene provides vector search.
- **Status:** the core content loop works, including publishing, replies, and semantic search. Mosa is still in development and has open requirements before a public launch; see the [MVP scope](spec/mvp.md) for details. The [concept](spec/Concept.md) also includes ideas and postponed features.

## Areas of experimentation

Mosa is also a playground for exploring several ideas:

- **Topic-centered discovery:** people describe interests in their own words and find semantically related posts, organizing discovery around topics rather than social connections.
- **Lightweight AI:** a local embedding model supports semantic search, with the goal of running a social network on modest hardware.
- **Cyber self-defense:** request shedding, rate limits, and blocking at different levels, including automatically triggered measures.
- **Identity:** [MIS](spec/MIS1.0.md) explores a minimal, privacy-friendly identity system that stays out of the user's way and addresses problems of existing approaches by design. Each identity is represented by a fixed fictional name and avatar derived from its cryptographic identity, making it easier for people to recognize.
- **Browser-side logic:** moving more state and logic into the browser to improve responsiveness and resilience to pod downtime and temporary rate limits. The design also envisages client-based federation: collecting posts from multiple pods in the browser could simplify the server architecture.
- **Transparent legal terms:** [ClearTerms](spec/ClearTerms1.0.md) builds pod-specific privacy policies and terms of service on shared reference texts and highlights differences so users can understand them quickly. The [baselines and Mosa profiles](server/src/main/resources/clearterms/) in this repository are provisional proposals and have not been legally vetted. Independently verifiable trust anchors are still an open launch requirement.

These are areas of experimentation and design goals; see the [MVP scope](spec/mvp.md) for implementation status and remaining work.

## Explore the repository

- [Getting started](docs/getting-started.md) — build, verify, and prepare a local pod.
- [Architecture](spec/Architecture.md) — module boundaries and system design.
- [Pod API](spec/mosa-pod-api.raml) — API specification.
- [MVP scope](spec/mvp.md) — launch posture and outstanding requirements.
- [Documentation index](spec/README.md) — all specifications, design notes, and development guidance.

The main code is in `server/`, `client/`, and `shared/`; `tasks/` contains project task tracking.

## Licenses and attribution

Mosa is distributed under the [Apache License 2.0](LICENSE). The client uses Font Awesome Free and DiceBear Adventurer Neutral; details and other dependencies are listed in the [third-party license inventory](spec/ThirdPartyLicenses.md). The running client also provides notices under **Legal → Third-party notices**.
