Gelesene oder serverseitig geloeschte Admin-Nachrichten sollen im Client nicht einfach verschwinden, sondern lokal erhalten und ueber einen Filter steuerbar bleiben.

Aktuell verschwinden Admin-Nachrichten nach dem Lesen oder nach serverseitiger Entfernung aus der sichtbaren Liste. Dadurch fehlt fuer Nutzerinnen und Nutzer ein nachvollziehbarer Verlauf, und die Oberflaeche kann nicht zwischen aktuell ungelesenen Nachrichten und lokal bekannten, bereits verarbeiteten Eintraegen unterscheiden.

Ziel ist eine kleine, klare Lifecycle-Darstellung fuer Admin-Nachrichten im Browser. Gelesene Nachrichten sollen standardmaessig ausgeblendet sein, aber ueber einen Filterknopf einblendbar bleiben. Nachrichten, die spaeter serverseitig geloescht wurden, sollen lokal weiterhin sichtbar bleiben, solange sie bereits im Browser-Speicher bekannt sind.

Die Aufgabe umfasst mindestens diese Punkte:

- Gelesene Admin-Nachrichten bleiben im lokalen Browser-Zustand erhalten.
- Es gibt einen Filterknopf, mit dem gelesene Admin-Nachrichten ein- und ausgeblendet werden koennen.
- Der Default-Zustand blendet gelesene Admin-Nachrichten aus.
- Bereits lokal bekannte Admin-Nachrichten bleiben im Browser-Speicher erhalten, auch wenn sie spaeter serverseitig geloescht wurden.
- Die Darstellung macht erkennbar, ob eine Nachricht gelesen wurde und ob sie nur noch lokal vorliegt.

Akzeptanzkriterien:

- Nach dem Lesen einer Admin-Nachricht verschwindet sie nicht dauerhaft aus dem Client, sondern wird bei aktivem Filter wieder sichtbar.
- Beim ersten Oeffnen der Ansicht sind gelesene Admin-Nachrichten standardmaessig ausgeblendet.
- Eine bereits lokal bekannte Admin-Nachricht bleibt nach serverseitiger Loeschung im Browser-Speicher und in der Client-Ansicht verfuegbar.

Bei der Umsetzung ist insbesondere zu klaeren:

- Ob der Client fuer lokal erhaltene, serverseitig nicht mehr vorhandene Nachrichten einen expliziten Status oder Marker anzeigen soll.
- Wie Browser-Speicher und Server-Synchronisation zusammenarbeiten, ohne geloeschte Servereintraege beim naechsten Refresh unbeabsichtigt lokal zu verwerfen.
- Wie sich diese Aufgabe zu [024-AllowAdminDeleteAdminMessages](/home/kai/Projects/straightway/mosa/tasks/1-open/024-AllowAdminDeleteAdminMessages/Description.md), [020-AdminUserMessagingLifecycle](/home/kai/Projects/straightway/mosa/tasks/3-done/020-AdminUserMessagingLifecycle/Description.md) und [023-AdminSentMessagesPerProfile](/home/kai/Projects/straightway/mosa/tasks/3-done/023-AdminSentMessagesPerProfile/Description.md) verhaelt.
