Ziel

Die Gesamtintegration der neuen Dokumentketten-Auslieferung soll ueber einen abschliessenden automatisierten Test nachgewiesen werden.

Scope

- Gemeinsame Betrachtung von Startvalidierung, HTTP-Endpoints und Client-Nutzung.
- Nachweis des Erfolgsfalls fuer alle aktuell betroffenen Dokumentarten.
- Nachweis eines Startfehlers bei fehlender oder inkompatibler Referenz in einer Dokumentkette.

Form des Abschlusstests

- Vollautomatisierter Test mit den bestehenden Testwerkzeugen des Projekts.

Vorbedingungen

- Die neuen Dokumentketten-DTOs sind eingefuehrt.
- Die serverseitige Kettenaufloesung und Startvalidierung ist implementiert.
- Die neuen Endpoints sind aktiv.
- Die betroffenen Clients oder internen Aufrufer sind umgestellt.

Durchfuehrung

- Einen Testfall fuer einen gueltig startenden Pod mit vollstaendiger Privacy-Kette und vollstaendiger Terms-Kette ausfuehren.
- Dabei ueber die oeffentlichen Endpoints pruefen, dass pro Dokumentart genau ein Response mit separater `baseline` und geordneter `profiles`-Liste geliefert wird.
- Dabei ueber den betroffenen Client oder internen Aufrufer pruefen, dass die Dokumente erfolgreich geladen und weiterverarbeitet werden koennen.
- Einen Testfall fuer einen Pod-Start mit fehlendem oder inkompatiblem referenziertem Dokument ausfuehren.
- Dabei pruefen, dass der Pod-Start scheitert und der Error-Log die geforderten Diagnoseinformationen enthaelt.

Akzeptanzkriterien

- Der Erfolgsfall deckt Privacy und Terms gemeinsam ab.
- Der Erfolgsfall prueft die Trennung von `baseline` und `profiles` sowie die Reihenfolge der Profilkette.
- Der Fehlfall weist nach, dass unvollstaendige oder inkompatible Ketten den Pod-Start verhindern.
- Der Fehlfall weist nach, dass die Error-Logs die benoetigten Details enthalten.
- Der Abschlusstest betrachtet die wichtigsten geaenderten Komponenten zusammen statt nur isolierte Teilaspekte.
