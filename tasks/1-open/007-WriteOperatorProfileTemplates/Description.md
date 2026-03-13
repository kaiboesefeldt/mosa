Wenn Operator-Profile fehlen, soll der Pod künftig Template-Dateien schreiben, die Nutzerinnen und Nutzer nur noch ausfüllen müssen.

Damit soll das bisherige Verhalten ersetzt werden, bei dem die passenden Template-YAMLs lediglich geloggt werden. Ziel ist, dass die benötigten Vorlagen direkt als Dateien verfügbar sind und nicht mehr aus Logs herauskopiert werden müssen.

Die Aufgabe umfasst mindestens diese Punkte:

- Wenn erforderliche Operator-Profile fehlen, soll der Pod die passenden Template-Dateien automatisch erzeugen.
- Die geschriebenen Dateien sollen so vollständig vorbereitet sein, dass sie nur noch mit den konkreten Werten ausgefüllt werden müssen.
- Das bisherige Loggen der Template-YAMLs soll durch dieses Dateischreib-Verhalten ersetzt werden.
- Es muss klar sein, an welchem Ort die Template-Dateien geschrieben werden und wie dieser Ort für Nutzerinnen und Nutzer auffindbar ist.
- Die Lösung muss sauber mit wiederholten Starts oder erneuten Prüfungen umgehen, insbesondere wenn Template-Dateien bereits vorhanden sind.
- Es ist zu klären, wie Nutzerinnen und Nutzer darauf hingewiesen werden, dass Dateien erzeugt wurden und noch auszufüllen sind.

Bei der Umsetzung ist zu klären:

- In welches Verzeichnis die Template-Dateien geschrieben werden sollen.
- Wie Dateinamen gewählt werden, wenn mehrere fehlende Operator-Profile gleichzeitig vorliegen.
- Ob bereits vorhandene Dateien überschrieben, ergänzt oder unangetastet gelassen werden sollen.
- Wie verhindert wird, dass unvollständige Template-Dateien versehentlich als echte Profile verarbeitet werden.
- Ob zusätzlich zum Schreiben der Dateien weiterhin eine knappe Log-Meldung sinnvoll ist, die nur auf die erzeugten Dateien verweist.
