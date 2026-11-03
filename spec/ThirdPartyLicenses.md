# Third-party license inventory

This inventory describes third-party material in the current server and browser distributions. It is a release-maintenance record, not a legal opinion.

## Browser client

[`client/src/jsMain/resources/licenses/THIRD-PARTY-NOTICES.md`](../client/src/jsMain/resources/licenses/THIRD-PARTY-NOTICES.md) lists production npm dependencies with their resolved versions and includes available package license texts. It also lists the Kotlin/JS runtime modules shipped with the client. The user-facing copy is served from `/licenses/THIRD-PARTY-NOTICES.md`.

Refresh the npm inventory after dependency changes by first resolving the client production bundle, then running:

```bash
./gradlew :client:jsBrowserProductionWebpack
python3 scripts/update-client-third-party-notices.py
```

The script follows `npm ls --workspace=mosa-client --all --omit=dev`, so test and development dependencies are excluded. Review its output and add any newly used Gradle-supplied Kotlin/JS modules to its Kotlin runtime section.

Font Awesome Free uses separate licenses for icons (CC BY 4.0), web fonts (SIL OFL 1.1), and code (MIT). DiceBear Adventurer Neutral's design is CC BY 4.0 and its package code is MIT. Their upstream license files are under `client/src/jsMain/resources/licenses/`.

## Server distribution

The `:server:generateServerThirdPartyNotices` task extracts embedded `LICENSE`, `NOTICE`, and `COPYING` text files from every resolved `runtimeClasspath` JAR, labels each text with its component coordinates, and writes `server/build/generated/third-party-notices/SERVER-THIRD-PARTY-NOTICES.txt`. The file is included in the Shadow JAR and copied into the served static resources as `/SERVER-THIRD-PARTY-NOTICES.txt`. The Shadow JAR also includes the repository's Apache 2.0 license as `MOSA-LICENSE.txt`; original dependency license files remain in the archive. Additional license texts missing from upstream artifacts are included in `server/src/main/resources/licenses/` and in the served static resources.

The report explicitly marks components whose binary JAR has no embedded license text. Before release, check those components' published license metadata and add applicable license texts or notices. Also inspect the final distribution after dependency changes; an embedded file alone does not establish that a license is compatible with the distribution.

## Runtime model

The default embedding model is fetched at runtime from [`intfloat/multilingual-e5-small`](https://huggingface.co/intfloat/multilingual-e5-small), whose model card declares MIT. The model is not part of the application archive, but deployments download and use it; retain this model-card link in distribution notices. The model card documents a 512-token input limit and recommends `query:` and `passage:` prefixes for retrieval. The previous `efederici/multilingual-e5-small-4096` is an LSG long-context variant with a different weight file, despite sharing the tokenizer and E5-small base. Re-evaluate retrieval quality, prefix handling, and long-input behavior before release because this change truncates inputs beyond 512 tokens.
