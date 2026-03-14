Wenn Operator-Profile fehlen, soll der Pod künftig Template-Dateien schreiben, die Nutzerinnen und Nutzer nur noch ausfüllen müssen.

Damit soll das bisherige Verhalten ersetzt werden, bei dem die passenden Template-YAMLs lediglich geloggt werden. Ziel ist, dass die benötigten Vorlagen direkt als Dateien verfügbar sind und nicht mehr aus Logs herauskopiert werden müssen.

Konkret betrifft das sowohl das Privacy-Operator-Profil als auch das AGB-Operator-Profil. Wenn eine oder beide Dateien fehlen, sollen die fehlenden Dateien automatisch erzeugt werden.

Die Aufgabe umfasst mindestens diese Punkte:

- Wenn erforderliche Operator-Profile fehlen, soll der Pod die passenden Template-Dateien automatisch erzeugen.
- Fehlende Privacy- und AGB-Profile sollen jeweils als eigene Datei im aktuellen Arbeitsverzeichnis des Mosa-Server-Prozesses angelegt werden.
- Dafür sollen die bereits vorgesehenen Zieldateien `mosa_privacy.yaml` und `mosa_terms.yaml` verwendet werden.
- Die geschriebenen Dateien sollen so vollständig vorbereitet sein, dass sie nur noch mit den konkreten Werten ausgefüllt werden müssen.
- Die vorbereiteten Werte in neu erzeugten Template-Dateien sollen für auszufüllende Felder explizit `null` sein, nicht fachlich plausible Platzhalterwerte.
- Neu erzeugte Template-Dateien dürfen nicht dazu führen, dass der Pod erfolgreich startet; solange `null`-Werte noch nicht ersetzt wurden, muss der Start weiterhin an der Validierung scheitern. Textuelle Default-Ersetzungen in Textblöcken bleiben dabei akzeptiert.
- Das bisherige Loggen der Template-YAMLs soll durch dieses Dateischreib-Verhalten ersetzt werden.
- Die erzeugten Dateien müssen für Nutzerinnen und Nutzer an ihrem Schreibort klar auffindbar sein.
- Die Lösung muss sauber mit wiederholten Starts oder erneuten Prüfungen umgehen; bereits vorhandene Dateien bleiben vollständig unangetastet und nur fehlende Dateien werden erzeugt.
- Die generierten Vorlagen sollen die `expectedContent`-Hinweise als YAML-Kommentare enthalten, damit klar ist, welche Inhalte pro Feld erwartet werden.
- Wenn Dateien erzeugt wurden oder noch unvollständig sind, soll eine Log-Meldung auf ERROR-Level mit Verweis auf die betroffenen Dateien ausgegeben werden; der Serverstart soll dabei weiterhin abgebrochen werden.
