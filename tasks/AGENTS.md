# Poor-Man's-Aufgabenverwaltung

Dieses Verzeichnis ist ein bewusst einfaches Taskboard im Dateisystem. Eine Aufgabe ist ein Ordner. Sein Status ergibt sich nur aus dem übergeordneten Statusordner.

## Statusordner

- `1-open`: bekannt, noch nicht in Bearbeitung
- `2-in-progres`: aktiv in Bearbeitung
- `3-done`: fachlich erledigt
- `4-discarded`: bewusst verworfen
- `5-follow-up`: vorerst abgeschlossen oder pausiert, später erneut prüfen

Pro Aufgabe gibt es immer genau einen Status. Statuswechsel erfolgen durch Verschieben des ganzen Aufgabenordners.

## Aufgabenformat

Schema:

```text
tasks/<status>/<NNN>-<ShortName>/
```

Beispiel:

```text
tasks/1-open/002-LegalDocumentChangeConfirmationFlow/
```

Pflichtdatei:

- `Description.md`: Problem, Ziel, wichtige Randbedingungen

Erlaubt:

- weitere Markdown-Dateien für Kontext
- weitere Dateien oder einfache Unterstrukturen, wenn sie Bearbeitung erleichtern

Markdown ist Standard. Inhalt darf knapp bleiben, muss aber für Mensch oder Coding-KI klar ausführbar sein.

## Subtasks

Wenn eine Aufgabe zerlegt wird, liegen Subtasks als Markdown-Dateien im selben Aufgabenordner.

Schema:

```text
subNNN-ShortDescription.md
```

Beispiel:

```text
sub001-CreateMissingOperatorProfileFiles.md
```

Regeln:

- `NNN` ist dreistellige laufende Nummer innerhalb der Aufgabe.
- `ShortDescription` bleibt kurz, stabil, ASCII-kompatibel.
- Jede Subtask soll eigenständig bearbeitbar sein.
- Abgeschlossene Subtasks werden nicht verschoben, sondern umbenannt:

```text
done-subNNN-ShortDescription.md
```

Beispiel:

```text
done-sub001-CreateMissingOperatorProfileFiles.md
```

- Die Hauptaufgabe bleibt fachliche Klammer. Subtasks ergänzen `Description.md`, ersetzen sie nicht.

### Pflichtinhalt der letzten Subtask

Wenn eine Aufgabe in Subtasks zerlegt ist, muss die letzte Subtask enthalten:

1. Abschlusstest der integrierten Gesamtänderung
2. Prüfung und ggf. Anpassung der Doku in:
   - `spec`
   - `server/src/main/resources/clearterms`
   - `server/src/main/resources/doc/serve`
3. Prüfung neuer 3rd-Party-Lizenzen auf Kompatibilität mit `License`

Der Abschlusstest muss klar sagen:

- welche Komponenten gemeinsam geprüft werden
- welche Vorbedingungen gelten
- welche Schritte auszuführen sind
- woran Erfolg oder Fehlschlag erkannt wird
- ob Test vollautomatisch, repo-intern manuell durch KI, zur Laufzeit manuell oder nur durch Menschen ausführbar ist

Wenn ein Mensch testen muss, muss Beschreibung ohne Rückfragen ausführbar sein.

## Nummerierung

- Neue Aufgaben bekommen nächste freie dreistellige Nummer.
- Nummer bleibt über gesamten Lebenszyklus gleich.
- `ShortName` bleibt knapp, lesbar, stabil.

## Arbeitsweise

1. Neue Aufgabe: Ordner in `1-open` anlegen, `Description.md` schreiben.
2. Arbeit starten: Ordner nach `2-in-progres` verschieben.
3. Arbeit abschließen: erst nach menschlicher Freigabe nach `3-done` oder `5-follow-up` verschieben.
4. Aufgabe verwerfen: nur nach menschlicher Freigabe nach `4-discarded` verschieben.
5. Wiedervorlage: nach `5-follow-up` verschieben.

Wenn Zuschnitt einer Aufgabe wesentlich wechselt:

- `Description.md` aktualisieren
- bei echter Aufspaltung neue Aufgabe mit neuer Nummer anlegen

## Konventionen

- Beschreibungen standardmäßig auf Deutsch
- Ordnernamen ASCII, ohne Leerzeichen
- Keine Prioritätslogik im Namen
- Erledigte Aufgaben werden nicht umnummeriert
- `4-discarded` ist Endstatus, kein Parkplatz
- `5-follow-up` ist für bewusste spätere Wiederaufnahme
- Schreibweise `2-in-progres` bleibt absichtlich unverändert
