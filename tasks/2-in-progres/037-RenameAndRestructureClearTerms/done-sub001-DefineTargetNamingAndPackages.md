## Ziel

Einen konkreten, umsetzbaren Vorschlag fuer die kuenftigen ClearTerms-Klassennamen und Unterpakete erarbeiten, bevor produktiver Code umbenannt oder verschoben wird.

## Ergebnis

Die Subtask liefert eine belastbare Zielstruktur fuer alle betroffenen ClearTerms-Typen in `shared`, `server` und `client`, inklusive:

- einer Liste der betroffenen Klassen,
- des jeweils vorgeschlagenen neuen Klassennamens,
- des jeweils vorgeschlagenen Zielpakets,
- expliziter Ausnahmen, falls ein `ClearTerms`-Praefix oder ein laengerer Name ausnahmsweise behalten werden soll,
- einer kurzen Begruendung fuer kritische Namensentscheidungen.

Das Ergebnis liegt in `TargetNamingAndPackages.md` im selben Aufgabenordner.

## Leitplanken

- Das Ergebnis muss fuer die anschliessende Implementierung konkret genug sein, dass Umbenennungen ohne neue Grundsatzdiskussionen ausgefuehrt werden koennen.
- Kurze Namen muessen im Zielpaket eindeutig und lesbar bleiben.
- Bestehende Unterpakete wie `canonical`, `locale`, `parse`, `placeholder`, `render` und `validation` duerfen uebernommen, angepasst oder ergaenzt werden, aber nicht ohne Begruendung.
- Die Entscheidung soll den gesamten betroffenen Gradle-Baum betrachten: `shared`, `server`, `client` und Tests.
- Die Subtask enthaelt noch keine produktive Umbenennung, sondern nur den verbindlichen Vorschlag.
