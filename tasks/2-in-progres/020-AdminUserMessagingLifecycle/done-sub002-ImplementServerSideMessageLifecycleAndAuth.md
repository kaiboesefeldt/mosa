Ziel ist, die serverseitige Fachlogik fuer private Admin-Nachrichten mit Lifecycle und Berechtigungen umzusetzen.

Diese Subtask umfasst mindestens:

- privaten JSON-Store fuer Admin-Nachrichten getrennt vom Content-/VectorStore anlegen
- Service fuer Senden, Inbox-Abruf und Expiry-Cleanup implementieren
- Ablaufdatum beim Abruf atomar auf hoechstens die kurze TTL verkuerzen
- Admin-Rechte beim Senden serverseitig pruefen
- Zugriff auf fremde Nachrichten serverseitig verhindern
- periodischen Background-Cleanup direkt in v1 anbinden
- passende Ktor-Endpunkte und Koin-Wiring ergaenzen

Wichtig:

- Der Lifecycle muss ueber oeffentliches Verhalten testbar sein.
- Die Verkuerzung des Ablaufdatums beim Abruf muss als Red-Green-Schritt ueber Tests abgesichert werden.
