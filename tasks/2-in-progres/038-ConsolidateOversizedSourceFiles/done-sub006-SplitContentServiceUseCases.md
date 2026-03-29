Ziel dieser Subtask ist, `server/src/main/kotlin/straightway/mosa/content/ContentService.kt` entlang seiner Anwendungsfaelle zu konsolidieren.

Ausgangsproblem:

- Die Datei vereint Publishing, Reply-Erzeugung, Loeschregeln, Root-Reply-Loeschung, Query- und Fetch-Use-Cases, Suchprojektionen, CSV-Export und Modell-Mapping.
- Schreiben, Lesen, Loeschen und Export sind dadurch in einer Sammelklasse gekoppelt.

Erwartetes Ergebnis:

- Schreib-, Lese-, Loesch- und Exportlogik sind klarer getrennt, ohne die bestehende API unnoetig zu verkomplizieren.
- Mapping- und Hilfslogik wird aus dem zentralen Service herausgezogen, wenn sie den Hauptzuschnitt verduennt.
- Das fachliche Verhalten der Content-Anwendungsfaelle bleibt unveraendert.
