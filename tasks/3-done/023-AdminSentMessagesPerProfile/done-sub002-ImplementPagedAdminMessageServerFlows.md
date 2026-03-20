Ziel ist, die serverseitige Fachlogik fuer gepagte Nachrichtenlisten und den Admin-Verlauf pro Profil umzusetzen.

Diese Subtask umfasst mindestens:

- Red-Green-Tests fuer gepagte Listenresponses der Nutzer-Inbox schreiben
- Red-Green-Tests fuer `GET /admin/messages` als Admin-Verlauf pro Profil schreiben
- Store- und Service-Zuschnitt so erweitern, dass `GET /messages` und `GET /admin/messages` getrennte Read-Semantik haben
- `GET /messages` auf Paging mit fester Page-Groesse 10 umstellen
- `GET /admin/messages` fuer authentifizierte Admins implementieren und auf Paging mit fester Page-Groesse 10 auslegen
- sicherstellen, dass der Admin-Verlauf alle Admin-Nachrichten eines Profils zeigt, aber keine TTL-Verkuerzung ausloest
- Restlaufzeit in Tagen fuer den Admin-Verlauf serverseitig berechenbar und auslieferbar machen
- abgelaufene Nachrichten beim Abruf weiter en passant bereinigen und nicht mehr ausliefern

Wichtig:

- Die Aenderungen an Paging, Sortierung, Auth und TTL-Semantik muessen ueber oeffentliches Verhalten testbar sein.
- Die fachliche Trennung zwischen Empfaenger-Inbox und Admin-Verlauf darf nicht nur im Client entstehen.
