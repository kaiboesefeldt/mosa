Stand: 2026-03-17

Gesichtete Lucene-relevante Produktionspfade:

- `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStore.kt`
- `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneQueryPlanner.kt`
- `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneWriteTransaction.kt`
- `server/src/main/kotlin/straightway/mosa/vectorstore/QueryOnRegistry.kt`
- `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneIndexAccess.kt`

Gesichtete bestehende Tests:

- `server/src/test/kotlin/straightway/mosa/vectorstore/LuceneQueryPlannerTest.kt`
- `server/src/test/kotlin/straightway/mosa/vectorstore/LuceneWriteTransactionTest.kt`
- `server/src/test/kotlin/straightway/mosa/vectorstore/QueryOnRegistryTest.kt`
- `server/src/test/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStoreTest.kt`
- `server/src/test/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStoreSubIntegrationTest.kt`
- indirekt betroffene Service-Integration u.a. in `server/src/test/kotlin/straightway/mosa/content/AdminDeleteIntegrationTest.kt`

Abgedeckte Upgrade-Risiken nach Sichtung:

- Schreibpfad und Dokumentersetzung ueber `LuceneWriteTransaction`
- Query-Planung fuer KNN, Feldfilter und Zeitfilter
- KNN-Suche, Feldsuche und Pagination inklusive `queryOn`
- Registry-Lebenszyklus fuer Folgeseiten
- Fehlerverhalten fuer leere/fehlende Indizes und geschlossene Stores

Identifizierte und geschlossene Testluecke:

- Die echten Lucene-Lesepfade `fetch` und `fetchByFieldValues` waren nur ueber Mock-basierte Store-Tests abgesichert, nicht ueber eine Subintegration mit realem Lucene-Index.
- Ergaenzt wurden in `LuceneLocalVectorStoreSubIntegrationTest`:
  - dokument-id-basierte Suche mit kompletter Rueckgabepruefung
  - `fetch` mit erhaltener angeforderter ID-Reihenfolge
  - `fetchByFieldValues` ueber mehrere Lucene-Seiten mit deterministischer Zeitreihenfolge
- Dabei fiel auf, dass nicht-vektorbasierte Lucene-Suchen bislang `similarity = NaN` zurueckgeben konnten, weil Lucene bei sortierten Abfragen keine Scores berechnet. Der Store normalisiert diesen Rueckgabewert nun auf `1.0f`.

Einschaetzung:

- Fuer das geplante Lucene-10-Upgrade ist die Testbasis vor dem eigentlichen Dependency-Sprung ausreichend breit, um Regressionen in Schreibpfad, Suche, Pagination und indexbasierten Lesezugriffen sichtbar zu machen.
