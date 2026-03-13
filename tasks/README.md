# Poor-Man's-Aufgabenverwaltung

Dieses Verzeichnis ist ein bewusst einfaches Taskboard im Dateisystem. Eine Aufgabe ist ein Ordner mit einer kurzen Beschreibung. Der Status einer Aufgabe ergibt sich nur aus dem übergeordneten Ordner.

## Statusordner

- `1-open`: Aufgabe ist bekannt, aber noch nicht in Bearbeitung.
- `2-in-progres`: Aufgabe wird gerade aktiv bearbeitet.
- `3-done`: Aufgabe ist fachlich erledigt.
- `4-discarded`: Aufgabe wird nicht weiterverfolgt.

Es soll immer nur einen aktuellen Status pro Aufgabe geben. Statuswechsel passieren durch Verschieben des ganzen Aufgabenordners.

## Aufbau einer Aufgabe

Jede Aufgabe liegt in einem eigenen Ordner nach diesem Schema:

```text
tasks/<status>/<laufende-nummer>-<Kurzname>/
```

Beispiel:

```text
tasks/1-open/002-LegalDocumentChangeConfirmationFlow/
```

Pflichtdatei pro Aufgabe:

- `Description.md`: beschreibt Problem, Ziel und wichtige Randbedingungen.

Bevorzugtes Textformat ist Markdown. Der Inhalt darf knapp bleiben. Wenn eine Aufgabe mehr Kontext braucht, wird `Description.md` erweitert oder es werden weitere Markdown-Dateien im Aufgabenordner ergänzt.

Zusätzliche Informationen duerfen direkt im Aufgabenordner abgelegt werden, wenn sie fuer die Bearbeitung nuetzlich sind, zum Beispiel Notizen, Entscheidungen, Skizzen oder Verweise.

## Nummerierung

- Neue Aufgaben bekommen die nächste freie dreistellige Nummer.
- Die Nummer bleibt über den gesamten Lebenszyklus gleich.
- Der Kurzname soll knapp, lesbar und stabil sein.

Beispiel: Aus `tasks/1-open/003-NewTask/` wird bei Arbeitsbeginn `tasks/2-in-progres/003-NewTask/`.

## Arbeitsweise

1. Neue Aufgabe anlegen: Ordner unter `1-open` erstellen und `Description.md` schreiben.
2. Arbeit starten: Ordner nach `2-in-progres` verschieben.
3. Arbeit abschließen: Ordner nach `3-done` verschieben.
4. Aufgabe verwerfen: Ordner nach `4-discarded` verschieben.

Wenn sich der Zuschnitt einer Aufgabe wesentlich ändert, wird die Beschreibung aktualisiert. Wenn daraus eigentlich zwei Aufgaben werden, bleibt die ursprüngliche Aufgabe klein und es wird eine neue Aufgabe mit neuer Nummer angelegt. Falls es pragmatischer ist, duerfen im Aufgabenordner auch Subtasks als weitere Markdown-Dateien oder Unterordner abgelegt werden, solange klar bleibt, was die Hauptaufgabe ist und was nur Teil davon ist.

## Pragmatische Regeln

- Kein separates Metadatenformat, solange `Description.md` ausreicht.
- Markdown ist das Standardformat fuer textuelle Informationen im Tasksystem.
- Zusätzliche Dateien und einfache Unterstrukturen sind erlaubt, wenn sie die Bearbeitung erleichtern.
- Keine Prioritätslogik im Verzeichnisnamen; Priorisierung passiert beim Blick auf `1-open`.
- Erledigte Aufgaben werden nicht umnummeriert.
- Verworfen ist ein bewusster Endstatus, kein temporärer Parkplatz.

## Konventionen

- Beschreibungen werden auf Deutsch geschrieben, wenn nichts anderes sinnvoller ist.
- Namen in Ordnern verwenden ASCII ohne Leerzeichen, damit Verschieben und Referenzieren unkompliziert bleibt.
- Die bestehende Ordnerbenennung `2-in-progres` bleibt absichtlich so bestehen, damit keine unnötigen Umbenennungen nötig sind.
