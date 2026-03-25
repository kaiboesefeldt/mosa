Ziel ist, `IdentityConnectedView` strukturell aufzuraeumen und die Datei deutlich zu verkleinern.

Ausgangslage:

- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityConnectedView.kt` ist inzwischen deutlich zu gross und vereint zu viele Verantwortlichkeiten in einer einzelnen Datei.
- Die Datei enthaelt gleichzeitig Zustandslogik, Ladefluesse, UI-Hilfsfunktionen, Rendering und mehrere fachlich getrennte Bereiche wie Inbox, Complaint-Queue, Admin-Aktionen, Composer und mobile Navigation.

Diese Aufgabe soll mindestens klären und umsetzen:

- welche fachlich zusammengehoerigen Teile aus `IdentityConnectedView` in eigene Dateien oder Hilfskomponenten extrahiert werden sollen
- wie Zustandslogik und reine Rendering-Teile sauberer getrennt werden koennen
- wie die Lesbarkeit und Wartbarkeit verbessert werden kann, ohne das Verhalten zu aendern
- wie die bestehenden Tests waehrend der Aufraeumarbeiten stabil gehalten oder gezielt ergaenzt werden

Erwartetes Ergebnis:

- `IdentityConnectedView` ist deutlich kleiner und klarer strukturiert
- zusammengehoerige Teilbereiche sind in passend benannte Dateien verschoben
- das bestehende Verhalten bleibt erhalten und ist weiter automatisiert abgesichert
