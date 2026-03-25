Gelesene oder serverseitig geloeschte Admin-Nachrichten sollen im Client nicht einfach verschwinden, sondern lokal erhalten und ueber einen Filter steuerbar bleiben.

Aktuell verschwinden Admin-Nachrichten nach dem Lesen oder nach serverseitiger Entfernung aus der sichtbaren Liste. Dadurch fehlt fuer Nutzerinnen und Nutzer ein nachvollziehbarer Verlauf, und die Oberflaeche kann nicht zwischen aktuell ungelesenen Nachrichten und lokal bekannten, bereits verarbeiteten Eintraegen unterscheiden.

Ziel ist eine kleine, klare Lifecycle-Darstellung fuer Admin-Nachrichten im Browser. Gelesene Nachrichten sollen standardmaessig ausgeblendet sein, aber ueber einen Filterknopf einblendbar bleiben. Nachrichten, die spaeter serverseitig geloescht wurden, sollen lokal weiterhin sichtbar bleiben, solange sie bereits im Browser-Speicher bekannt sind.

Die Aufgabe umfasst mindestens diese Punkte:

- Gelesene Admin-Nachrichten bleiben im lokalen Browser-Zustand erhalten.
- Es gibt einen Filterknopf, mit dem gelesene Admin-Nachrichten ein- und ausgeblendet werden koennen.
- Der Default-Zustand blendet gelesene Admin-Nachrichten aus.
- Bereits lokal bekannte Admin-Nachrichten bleiben im Browser-Speicher erhalten, auch wenn sie spaeter serverseitig geloescht wurden.
- Die Darstellung macht erkennbar, ob eine Nachricht gelesen wurde.

Akzeptanzkriterien:

- Nach dem Lesen einer Admin-Nachricht verschwindet sie nicht dauerhaft aus dem Client, sondern wird bei aktivem Filter wieder sichtbar.
- Beim ersten Oeffnen der Ansicht sind gelesene Admin-Nachrichten standardmaessig ausgeblendet.
- Eine bereits lokal bekannte Admin-Nachricht bleibt nach serverseitiger Loeschung im Browser-Speicher und in der Client-Ansicht verfuegbar.

Bei der Umsetzung ist insbesondere zu klaeren:

- Wie Browser-Speicher und Server-Synchronisation zusammenarbeiten, ohne geloeschte Servereintraege beim naechsten Refresh unbeabsichtigt lokal zu verwerfen.
- Wie der Nachrichtenabruf fuer eine effiziente Inbox-Synchronisation so erweitert werden kann, dass der Client neue oder weiterhin relevante Nachrichten ab einem vom Client uebergebenen UTC-Zeitpunkt nachladen kann, statt die sichtbare Inbox bei jedem Refresh vollstaendig neu abzurufen.
- Wie die genaue API-Ausgestaltung fuer diese Synchronisation konsistent zwischen Server, Client und RAML-Spezifikation definiert wird.
