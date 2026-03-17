Stand: 2026-03-17

Zielversion:

- Lucene `10.4.0` ist laut offizieller Apache-Lucene-Projektseite die aktuelle stabile `10.x`-Version.
- Die auf der Projektseite verlinkte Download-/News-Kommunikation nennt `10.4.0` mit Release-Datum `2026-02-25`.

Direkt verwendete Lucene-Module im Server:

- `org.apache.lucene:lucene-core`
- `org.apache.lucene:lucene-analysis-common`

Tatsaechlich noetige API-Anpassungen fuer Lucene 10:

- Stored fields werden nicht mehr ueber `DirectoryReader.document(...)` gelesen, sondern ueber `storedFields().document(...)`.
- `DocumentStoredFieldVisitor` liefert das Dokument ueber `getDocument()`.
- Die parameterlose Konstruktion von `MatchAllDocsQuery()` ist in Lucene 10 deprecated; ersetzt durch `MatchAllDocsQuery.INSTANCE`.

Beobachtetes Verhalten fuer lokale Lucene-9-Indizes:

- Eine echte lokal erzeugte Lucene-`9.12.3`-Fixture schlaegt ohne `lucene-backward-codecs` beim Oeffnen unter Lucene `10.4.0` reproduzierbar fehl.
- Der Startfehler wird nun in `LuceneIndexAccess` in eine klare Handlungsanweisung uebersetzt: lokalen Altindex manuell loeschen und neu starten.
- Der Repo-Stand behandelt lokale Lucene-`9.x`-Indizes damit bewusst als inkompatibel; eine Migration oder Weiterverwendung findet nicht statt.

Warnungspruefung:

- Die vom Lucene-Upgrade neu eingefuehrten Deprecation-Warnungen zu `MatchAllDocsQuery()` wurden beseitigt.
- Bei `:server:compileKotlin --warning-mode all` blieben danach keine neuen Lucene-bezogenen Compilerwarnungen offen.
- Verbleibende Warnungen aus `./gradlew` betreffen weiterhin andere, bereits bekannte Themen:
  - Gradle-`archives`-Deprecation im Client-Build
  - `sun.misc.Unsafe`-/JDK-Warnungen aus externen Komponenten

Verifikation:

- `./gradlew :server:compileKotlin :server:compileTestKotlin --warning-mode all`
- `./gradlew :server:test --tests straightway.mosa.vectorstore.LuceneIndexAccessTest`
- `./gradlew :server:test --tests 'straightway.mosa.vectorstore.*'`

Einschaetzung:

- Die Lucene-Migration im Repo blieb klein und lokal auf den Vector-Store-Stack begrenzt.
- Das beobachtbare Verhalten fuer Query-Planung, Schreiben, textuelle Suche, KNN-Suche, Pagination und indexbasierte Lesewege ist durch die bestehende und erweiterte Testbasis abgesichert.
