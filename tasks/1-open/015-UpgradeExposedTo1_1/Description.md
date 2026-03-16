Die Exposed-Abhaengigkeiten im Server sollen von `1.0.0-beta-2` auf die aktuelle `1.1.x`-Linie angehoben werden.

Ziel ist, den Datenbankzugriff auf einen neueren und stabileren Stand zu bringen, ohne das Verhalten von Persistenz, Queries und Transaktionen unbeabsichtigt zu veraendern.

Die Aufgabe ist separat angelegt, weil der Sprung von der bisherigen Beta-Version auf `1.1.x` ein groesserer ORM- und API-Wechsel sein kann und gezielte Migrationsarbeit braucht.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle Exposed-Verwendungen im Server identifizieren, insbesondere Schema-, Query-, DAO- und Transaktionscode.
- Relevante API- und Verhaltensaenderungen bis `1.1.x` bewerten.
- Noetige Anpassungen an Produktions- und Testcode minimal umsetzen.
- Datenbanknahe Tests und die betroffenen Integrationspfade grün bekommen.
- Den Repo-weiten Vollcheck mit `./gradlew check` ausfuehren.

Besonders zu klaeren:

- Ob sich Transaktionsverhalten, Typabbildungen oder SQL-Generierung geaendert haben.
- Ob Anpassungen an SQLite-spezifischem Verhalten noetig sind.
- Ob die Migration in einem Schritt sinnvoll ist oder zunaechst auf eine kleinere Zwischenversion fuehren sollte.
