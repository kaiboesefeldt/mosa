Die Lucene-Abhaengigkeiten im Server sollen von `9.12.3` auf die bei Bearbeitungsbeginn per Web-Recherche verifizierte neueste stabile `10.x`-Version angehoben werden.

Ziel ist, den Such- und Vector-Store-Stack auf eine aktuelle Hauptversion zu bringen, ohne das beobachtbare Verhalten der Such- und KNN-Pfade unbeabsichtigt zu veraendern.

Die Aufgabe ist bewusst separat von der allgemeinen Dependency-Aktualisierung geschnitten, weil der Sprung auf Lucene `10.x` ein echter Major-Upgrade mit moeglichen API- und Index-Kompatibilitaetsfolgen ist.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle direkt verwendeten Lucene-Module im Server identifizieren.
- Vor der eigentlichen Aktualisierung pruefen, ob die bestehenden Unit- und Integrationstests die Lucene-relevanten Risiken ausreichend abdecken.
- Falls die vorhandenen Tests nicht ausreichen, die Abdeckung gezielt erweitern, insbesondere fuer Lucene-nahe Integrationspfade, damit das Regressionsrisiko des Upgrades weitestgehend minimiert wird.
- Relevante API-Aenderungen zwischen `9.12.3` und der gewaehlten `10.x`-Version bewerten.
- Noetige Code-Anpassungen in Query-Planung, Index-Zugriff und Vector-Store umsetzen.
- Bestehende lokale Lucene-Indizes aus `9.x` duerfen als inkompatibel behandelt, verworfen und bei Bedarf lokal neu aufgebaut werden.
- Falls dafuer Startlogik oder Betriebsdokumentation angepasst werden muss, diese Aenderung minimal mit umsetzen.
- Alle durch das Lucene-Upgrade neu entstehenden Warnungen, insbesondere Deprecations, beseitigen.
- Alle betroffenen Integrations- und Unit-Tests grün bekommen und den Vollcheck des Repos mit `./gradlew check` ausfuehren.

Akzeptanzkriterien:

- Die bei Bearbeitungsbeginn neueste stabile Lucene-`10.x`-Version ist im Server eingebunden.
- Die fuer das Upgrade relevanten Lucene-Pfade sind vorab testseitig bewertet und bei Bedarf so zusaetzlich abgesichert, dass Regressionen mit hoher Wahrscheinlichkeit auffallen.
- Das beobachtbare Verhalten der Software bleibt aus Nutzersicht unveraendert, insbesondere fuer Schreibpfade, textuelle Suche, KNN-Suche und Pagination.
- Lokale Entwicklungsindizes aus Lucene `9.x` muessen nicht migriert werden; ein Verwerfen und Neuaufbau ist akzeptiert.
- Durch das Upgrade eingebrachte Warnungen, insbesondere Deprecations, bleiben nicht offen stehen.
- Alle automatisierten Tests sind grün, und `./gradlew check` laeuft erfolgreich durch.

Besonders zu klaeren:

- Ob Lucene `10.x` Aenderungen an Vektor-Feldern, KNN-Abfragen oder Codec-Konfiguration erfordert.
- Ob die bestehende Lucene-Testabdeckung fuer Query-Planung, Schreibpfade, Suche, Pagination und Index-Handling ausreicht oder gezielt erweitert werden muss.
- Welche konkrete stabile Lucene-`10.x`-Version zum Bearbeitungszeitpunkt die neueste ist.
- Ob zusaetzliche Schutzlogik oder Dokumentation fuer lokal verworfene Entwicklungsindizes noetig ist.
