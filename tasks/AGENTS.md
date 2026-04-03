# Poor-Man's-Aufgabenverwaltung

Dieses Verzeichnis ist ein bewusst einfaches Taskboard im Dateisystem. Eine Aufgabe ist ein Ordner mit einer kurzen Beschreibung. Der Status einer Aufgabe ergibt sich nur aus dem übergeordneten Ordner.

## Statusordner

- `1-open`: Aufgabe ist bekannt, aber noch nicht in Bearbeitung.
- `2-in-progres`: Aufgabe wird gerade aktiv bearbeitet.
- `3-done`: Aufgabe ist fachlich erledigt.
- `4-discarded`: Aufgabe wird nicht weiterverfolgt.
- `5-follow-up`: Aufgabe ist fuer den Moment abgeschlossen oder pausiert, soll aber spaeter erneut geprueft oder erneut ausgefuehrt werden, zum Beispiel bei 3rd-Party-Software-Aktualisierungen.

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

### Subtasks innerhalb einer Aufgabe

Wenn eine Aufgabe sinnvoll in kleinere, klar abgrenzbare Arbeitspakete zerlegt werden soll, duerfen im Aufgabenordner Subtasks als eigene Markdown-Dateien angelegt werden.

Namensschema fuer offene Subtasks:

```text
subNNN-ShortDescription.md
```

Beispiel:

```text
sub001-CreateMissingOperatorProfileFiles.md
```

Regeln fuer Subtasks:

- Subtasks liegen im selben Ordner wie die `Description.md` der Hauptaufgabe.
- `NNN` ist eine dreistellige laufende Nummer innerhalb der Aufgabe.
- `ShortDescription` bleibt kurz, stabil und ASCII-kompatibel.
- Jede Subtask-Beschreibung soll so geschnitten sein, dass sie von einer Coding-KI oder einer Person eigenstaendig bearbeitet werden kann.
- Die letzte Subtask einer zerlegten Aufgabe enthält verpflichtend:
  1. Einen Abschlusstest der Gesamtintegration aller von der Aenderung betroffenen Komponenten. Es wird detailliert beschrieben:
    - wie der Abschlusstest durchzufuehren ist, welche betroffenen Komponenten zusammen betrachtet werden muessen, welche Vorbedingungen gelten, welche konkreten Schritte auszufuehren sind und woran Erfolg oder Fehlschlag erkannt wird.
    - Der Abschlusstest muss nicht jedes Detail einzeln abpruefen, aber er muss die wichtigsten funktionalen Pfade der geaenderten Gesamtloesung abdecken.
      - Der Abschlusstest darf vollautomatisiert sein, von einer KI haendisch im Repo oder zur Laufzeit durchgefuehrt werden oder explizit einen Menschen benoetigen. Die Subtask-Beschreibung muss klar benennen, welche dieser Formen gemeint ist.
      - Wenn ein Mensch den Abschlusstest durchfuehren muss, muss die Subtask so konkret sein, dass die Ausfuehrung ohne Rueckfragen moeglich ist.
      2. Eine überprüfung und ggf. Anpassung der Dokumentation in folgenden Ordnern (inklusive Unterordnern)
         - `spec`
         - `server/src/main/resources/clearterms`
         - `server/src/main/resources/doc/serve`
      3. Eine Prüfung der Lizenzen neu eingefügter 3rd Party Kompomenten auf Kompatibilität mit der Lizenz, welche in `License` definiert ist. 
- Eine abgeschlossene Subtask wird nicht verschoben, sondern durch Umbenennen der Datei markiert: Aus `subNNN-ShortDescription.md` wird `done-subNNN-ShortDescription.md`.
- Die Hauptaufgabe bleibt die fachliche Klammer; Subtasks ersetzen nicht die `Description.md`, sondern praezisieren die Bearbeitung.

## Nummerierung

- Neue Aufgaben bekommen die nächste freie dreistellige Nummer.
- Die Nummer bleibt über den gesamten Lebenszyklus gleich.
- Der Kurzname soll knapp, lesbar und stabil sein.

Beispiel: Aus `tasks/1-open/003-NewTask/` wird bei Arbeitsbeginn `tasks/2-in-progres/003-NewTask/`.

## Arbeitsweise

1. Neue Aufgabe anlegen: Ordner unter `1-open` erstellen und `Description.md` schreiben.
2. Arbeit starten: Ordner nach `2-in-progres` verschieben.
3. Arbeit abschließen: Ordner nach `3-done` oder `5-follow-up` verschieben. Das erfolgt erst nach Freigabe durch einen Menschen.
4. Aufgabe verwerfen: Ordner nach `4-discarded` verschieben. Das erfolgt nur durch Freigabe durch einen Menschen.
5. Wiedervorlage vormerken: Ordner nach `5-follow-up` verschieben, wenn die Aufgabe spaeter bewusst erneut aufgegriffen werden soll.

Wenn sich der Zuschnitt einer Aufgabe wesentlich ändert, wird die Beschreibung aktualisiert. Wenn daraus eigentlich zwei Aufgaben werden, bleibt die ursprüngliche Aufgabe klein und es wird eine neue Aufgabe mit neuer Nummer angelegt. Falls es pragmatischer ist, duerfen im Aufgabenordner auch Subtasks als weitere Markdown-Dateien oder Unterordner abgelegt werden, solange klar bleibt, was die Hauptaufgabe ist und was nur Teil davon ist.

## Pragmatische Regeln

- Kein separates Metadatenformat, solange `Description.md` ausreicht.
- Markdown ist das Standardformat fuer textuelle Informationen im Tasksystem.
- Zusätzliche Dateien und einfache Unterstrukturen sind erlaubt, wenn sie die Bearbeitung erleichtern.
- Keine Prioritätslogik im Verzeichnisnamen; Priorisierung passiert beim Blick auf `1-open`.
- Erledigte Aufgaben werden nicht umnummeriert.
- Verworfen ist ein bewusster Endstatus, kein temporärer Parkplatz.
- `5-follow-up` ist fuer Aufgaben, die nicht jetzt aktiv bearbeitet werden, aber spaeter erneut relevant werden. Typische Beispiele sind geplante Rechecks, Wiederholungen oder Updates von externer Software.

## Konventionen

- Beschreibungen werden auf Deutsch geschrieben, wenn nichts anderes sinnvoller ist.
- Namen in Ordnern verwenden ASCII ohne Leerzeichen, damit Verschieben und Referenzieren unkompliziert bleibt.
- Die bestehende Ordnerbenennung `2-in-progres` bleibt absichtlich so bestehen, damit keine unnötigen Umbenennungen nötig sind.
