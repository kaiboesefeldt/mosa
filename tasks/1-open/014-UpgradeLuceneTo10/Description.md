Die Lucene-Abhaengigkeiten im Server sollen von `9.12.3` auf die aktuelle `10.x`-Linie angehoben werden.

Ziel ist, den Such- und Vector-Store-Stack auf eine aktuelle Hauptversion zu bringen, ohne das beobachtbare Verhalten der Such- und KNN-Pfade unbeabsichtigt zu veraendern.

Die Aufgabe ist bewusst separat von der allgemeinen Dependency-Aktualisierung geschnitten, weil der Sprung auf Lucene `10.x` ein echter Major-Upgrade mit moeglichen API- und Index-Kompatibilitaetsfolgen ist.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle direkt verwendeten Lucene-Module im Server identifizieren.
- Relevante API-Aenderungen zwischen `9.12.3` und der gewaehlten `10.x`-Version bewerten.
- Noetige Code-Anpassungen in Query-Planung, Index-Zugriff und Vector-Store umsetzen.
- Pruefen, ob bestehende lokale Indexdaten migriert, verworfen oder beim Start neu aufgebaut werden muessen.
- Die betroffenen Integrations- und Unit-Tests grün bekommen und den Vollcheck des Repos ausfuehren.

Besonders zu klaeren:

- Ob Lucene `10.x` Aenderungen an Vektor-Feldern, KNN-Abfragen oder Codec-Konfiguration erfordert.
- Ob bestehende lokale Indizes aus `9.x` noch gelesen werden koennen oder bewusst invalide werden.
- Ob zusaetzliche Schutzlogik oder Dokumentation fuer lokale Entwicklungsdaten noetig ist.
