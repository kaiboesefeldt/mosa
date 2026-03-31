Die aktuelle Loesung erreicht die Fehlerrobustheit der Identitaetsloeschung ueber eine Kompensationslogik mit Content-Restore. Fachlich ist das akzeptabel, architektonisch ist fuer diesen Use Case aber ein sauberer transaktionsorientierter Zuschnitt der SQLite-Stores gewuenscht.

Ziel dieser Subtask ist ein Umbau des Structured-State-Zugriffs, so dass eine gemeinsame aeussere DB-Transaktion mehrere Stores koordinieren kann, ohne Exposed-Transaktionsobjekte durch die fachlichen Store-Signaturen zu reichen.

Die avisierte Loesung ist:

- Es wird eine kleine Kontext-Abstraktion fuer Structured-State-Zugriffe eingefuehrt, zum Beispiel `StructuredStateContext`.
- Diese Abstraktion kapselt die Ausfuehrung von Exposed-Zugriffen, ohne dass fachliche Stores wissen muessen, ob sie in einer eigenen oder in einer bereits offenen Transaktion laufen.
- `StructuredStateDatabase` implementiert diesen normalen Kontext fuer den bisherigen Standardfall.
- `StructuredStateDatabase` bekommt zusaetzlich eine API wie `inTransaction { context -> ... }`, die einen transaktionsgebundenen Kontext fuer koordinierte Multi-Store-Operationen bereitstellt.
- Die betroffenen SQLite-Stores werden so umgebaut, dass sie nur noch gegen diesen Kontext arbeiten und keine eigene Transaktionseroeffnung mehr verstecken.
- Fuer kontextgebundene Store-Instanzen werden in Koin Factories registriert, statt die konkreten SQLite-Stores ad hoc im Service zu konstruieren.

Fuer diese Aufgabe sind mindestens folgende Punkte umzusetzen:

- Eine neue Context-Abstraktion fuer Structured-State-Zugriffe entwerfen und einfuehren.
- `StructuredStateDatabase` so erweitern, dass sowohl der bisherige Einzelzugriff als auch ein aeusserer `inTransaction`-Block moeglich ist.
- `SqlitePostComplaintStore` auf den neuen Context-Schnitt umstellen.
- `SqliteAdminMessageStore` auf den neuen Context-Schnitt umstellen.
- In Koin passende Factory-Typen und Registrierungen fuer kontextgebundene `PostComplaintStore`- und `AdminMessageStore`-Instanzen einfuehren.
- `IdentityDeletionService` so umbauen, dass die koordinierten SQLite-Loeschungen und die anschliessende Lucene-Loeschung aus einer aeusseren DB-Transaktion heraus orchestriert werden.
- Die aktuelle Restore-/Kompensationslogik fuer geloeschten Content entfernen, sofern sie nach dem Umbau nicht mehr benoetigt wird.

Wichtige Randbedingungen:

- Es soll keine rohe Exposed-`Transaction` durch fachliche Store-Interfaces oder Service-Methoden gereicht werden.
- Die Infrastruktur fuer Transaktionen soll an der Grenze zur DB gekapselt bleiben.
- Der Umbau soll nicht nur fuer diesen einen Use Case funktionieren, sondern einen sauberen Zuschnitt fuer weitere koordinierte SQLite-Operationen vorbereiten.
- Die Loeschung bleibt fachlich ein Best-Effort-Modell bezueglich SQLite plus Lucene. Es wird ausdruecklich keine echte verteilte Cross-Store-Atomizitaet behauptet.
- Die neue Architektur soll einfacher und nachvollziehbarer sein als eine Restore-/Kompensationsloesung.

Automatisierte Verifikation:

- Unit- oder Integrationstests belegen, dass die beiden SQLite-Stores sowohl im Standardfall als auch innerhalb einer aeusseren DB-Transaktion korrekt arbeiten.
- Eine fachliche Verifikation fuer `DELETE /v1/identity` belegt, dass bei einem Lucene-Fehler die aeussere DB-Transaktion nicht committed wird.
- Bestehende Loesch- und Moderationstests bleiben gruen oder werden passend auf den neuen Schnitt angepasst.
- Abschliessend laeuft `./gradlew check` erfolgreich.
