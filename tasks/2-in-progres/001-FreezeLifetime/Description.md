# Freeze-Snapshots ohne Ablaufdatum

## Problem

Freeze-Snapshots koennen aktuell nur bis zu einem Ablaufdatum wiederhergestellt werden. Das ist im Verhalten, im Datenmodell und in den UI-Texten so angelegt. Fuer den gewuenschten Anwendungsfall ist das zu restriktiv: Ein erzeugter Snapshot soll nicht allein wegen Zeitablaufs ungueltig werden.

Aktuell ist die Lebensdauer im Server auf 365 Tage gesetzt. Beim Restore wird das Ablaufdatum geprueft und ein abgelaufener Snapshot abgelehnt.

## Ziel

Freeze-Snapshots sollen kein Ablaufdatum mehr haben. Das Ablaufdatum soll nicht nur fachlich ignoriert, sondern konsequent aus dem Modell entfernt werden. Ein Snapshot darf also nicht mehr deshalb unbrauchbar werden, weil seit seiner Erstellung Zeit vergangen ist.

## Scope dieser Aufgabe

Diese Aufgabe umfasst die vollstaendige Umstellung auf dauerhaft wiederherstellbare Freeze-Snapshots. Dazu gehoeren das konsequente Entfernen des Ablaufdatums aus Snapshot-Modell, Snapshot-Format, Snapshot-Erzeugung, Snapshot-Validierung, Tests, Nutzerkommunikation und rechtlichen Dokumenten sowie die dauerhafte Vorhaltung historischer Archivschluessel.

Insbesondere soll:

- das Snapshot-Header-Modell kein Ablaufdatum mehr enthalten
- das serialisierte Snapshot-Format kein Ablaufdatum mehr enthalten
- ein Snapshot nicht mehr wegen Zeitablaufs abgelehnt werden
- ein Snapshot auch nach kuenftiger Schluesselrotation weiterhin wiederherstellbar bleiben
- die Archivschluessel nicht nur den aktiven Schluessel, sondern auch historische Schluessel aufloesen koennen
- der Archivschluessel-Store dafuer ein neues Dateiformat mit mehreren Schluesseln verwenden
- die aktive Schluesselauswahl im neuen Dateiformat eindeutig erkennbar sein
- die UI nicht mehr behaupten, dass die Wiederherstellung nur bis zu einem Ablaufdatum moeglich sei
- rechtliche Dokumente die neue Regelung korrekt widerspiegeln
- die technische Nutzerdokumentation in `server/src/main/resources/doc/serve/files.md` die geaenderte Datei- und Schluesselablage korrekt beschreiben
- die Tests das neue Verhalten explizit absichern

## Nicht-Ziele

Rueckwaertskompatibilitaet zu bereits erzeugten Snapshot-Dateien ist nicht erforderlich.

Eine Migration bereits bestehender Archivschluessel-Dateien in das neue Format ist ebenfalls nicht erforderlich.

## Akzeptanzkriterien

1. Ein Snapshot-Restore scheitert nicht mehr daran, dass ein gespeichertes Ablaufdatum in der Vergangenheit liegt.
2. Das Snapshot-Datenmodell enthaelt kein Ablaufdatum mehr.
3. Das Snapshot-Format enthaelt kein Ablaufdatum mehr.
4. Alle Nutzertexte und rechtlichen Dokumente beschreiben korrekt, dass die Restore-Grenze nicht mehr zeitbasiert ist.
5. Der Archivschluessel-Store kann historische Schluessel dauerhaft vorhalten und per `keyId` aufloesen.
6. Die aktive Schluesselauswahl bleibt von der Historie getrennt, sodass kuenftige Rotation sauber moeglich ist.
7. Der Archivschluessel-Store verwendet ein neues Dateiformat, das mehrere Schluessel aufnehmen kann und den aktiven Schluessel eindeutig festlegt.
8. Es wird keine Migration oder Rueckwaertskompatibilitaet fuer das alte Archivschluessel-Dateiformat eingebaut.
9. Bestehende Snapshot-Tests werden angepasst oder erweitert, sodass das neue Verhalten klar dokumentiert ist.
10. `server/src/main/resources/doc/serve/files.md` beschreibt die geaenderte Snapshot-bezogene Dateistruktur korrekt.
11. Es wird kein Workaround mit einer nur sehr grossen, aber endlichen Laufzeit eingefuehrt.
