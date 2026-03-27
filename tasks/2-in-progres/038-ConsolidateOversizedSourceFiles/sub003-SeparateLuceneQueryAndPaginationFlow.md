Ziel dieser Subtask ist, `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStore.kt` so zu konsolidieren, dass Query-Ausfuehrung, scorebasierte Nachfilterung und Pagination klarer getrennt sind.

Ausgangsproblem:

- Die Datei buendelt Lifecycle, Schreibzugriff, Suche, KNN-Nachfilterung, Pagination-Token-Verwaltung, Fetch-Varianten, Voll-Dump und Lucene-Dokumentabbildung.
- Besonders Suchlogik und Pagination sind eng verflochten und daher schwer isoliert pruefbar.

Erwartetes Ergebnis:

- Lifecycle- und Fetch-Aspekte bleiben klar von Query- und Paginationlogik getrennt.
- KNN-Nachfilterung und Paginationfluss sind in kleineren, besser benannten Einheiten gekapselt.
- Das Verhalten der bestehenden VectorStore-Schnittstelle bleibt unveraendert.
