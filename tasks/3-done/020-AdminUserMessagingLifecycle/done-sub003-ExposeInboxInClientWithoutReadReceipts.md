Ziel ist, die minimal noetige Client-Unterstuetzung fuer die neue Inbox zu schaffen.

Diese Subtask umfasst mindestens:

- `PodAccess` um den Inbox-Abruf und das Admin-Senden erweitern
- einen kleinen Client-Service fuer das Laden der Inbox anlegen
- eine einfache UI fuer Benachrichtigung und Nachrichtenliste anbinden
- eine minimale Admin-Sende-UI als Popover am Avatar eines Nutzerbeitrags oder einer Nutzerantwort anbinden
- keine serverseitigen Read-Receipts oder Antwortfunktionen einfuehren

Wichtig:

- Die UI soll den Kanal sichtbar und nutzbar machen, aber bewusst klein bleiben.
- Der Admin-Compose-Flow soll den Empfaenger aus dem geklickten Avatar-Kontext uebernehmen und keine allgemeine Nutzerverwaltung einfuehren.
- Lokale kosmetische Markierungen fuer bereits gesehene Nachrichten sind erlaubt, aber nicht verpflichtend.
