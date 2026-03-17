Ziel

Das Verhalten bei gefundenen inkompatiblen lokalen Lucene-`9.x`-Indizes soll fuer den Upgrade-Fall klar und minimal festgelegt werden.

Scope

- Auf Basis des bereits umgestellten Lucene-`10.x`-Codes feststellen, wie sich ein vorhandener lokaler Lucene-`9.x`-Index verhaelt.
- Falls noetig, minimale Produktivlogik umsetzen, die einen inkompatiblen lokalen Altindex erkennt, klar loggt und den Start beendet.
- Falls erforderlich, die benoetigte Betriebsdokumentation fuer das manuelle Loeschen lokaler Altindizes anpassen.

Nicht Teil dieser Subtask

- Migration, Uebernahme oder automatische Reparatur bestehender `9.x`-Indexdaten.
- Automatisches Loeschen, automatischer Neuaufbau oder sonstige Sonderbehandlung lokaler Altindizes.
- Allgemeine Dokumentationsarbeit ohne Bezug zum Verhalten bei gefundenem Altindex.
- Der abschliessende Gesamtnachweis ueber alle betroffenen Komponenten.

Akzeptanzkriterien

- Lokale Lucene-`9.x`-Indizes muessen nicht migriert werden.
- Falls ein inkompatibler lokaler Altindex gefunden wird, ist das Verhalten klar: erkennen, verstaendlich loggen, Start beenden.
- Falls dafuer Produktivcode oder Dokumentation angepasst werden muss, ist dies minimal umgesetzt.
- Es bleibt kein stillschweigender oder halbautomatischer Pfad offen, der Altindizes weiterverwendet oder automatisch bereinigt.
