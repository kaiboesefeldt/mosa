Ziel dieser Subtask ist, `server/src/main/kotlin/straightway/mosa/legal/LegalProfileSource.kt` entlang von Infrastruktur- und Fachlogik zu konsolidieren.

Ausgangsproblem:

- Die Datei vereint Ressourcenlesen, Dateisystem- und JAR-Scanning, Fragment-Erkennung, YAML-Merging, Metadaten-Parsing und Profilketten-Aufloesung.
- Infrastrukturdetails fuer verschiedene Ressourcenquellen und fachliche Kettenlogik liegen damit in derselben Klasse.

Erwartetes Ergebnis:

- Resource- und Fragment-Aufloesung sind von der eigentlichen Profilketten-Logik getrennt.
- YAML- und Metadaten-Parsing bleiben nachvollziehbar und kleiner geschnitten.
- Das bestehende Verhalten beim Laden und Validieren der Legal-Profile bleibt erhalten.
