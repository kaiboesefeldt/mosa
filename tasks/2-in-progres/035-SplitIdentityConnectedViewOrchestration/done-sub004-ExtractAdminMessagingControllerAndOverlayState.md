Ziel

Admin-User-Card, Empfaenger-Auswahl, History-Paging und Send-Flow sollen aus dem Hauptcomponent in einen eigenen Admin-Messaging-Controller ausgelagert werden.

Scope

- Oeffnen, Schliessen und Zuruecksetzen der Admin-User-Card extrahieren.
- History-Refresh, History-Paging und Scroll-Restore aus der Hauptdatei loesen.
- Subject-, Body-, Error- und Sending-State fuer den Admin-Composer in einen klar benannten Hook oder State-Typ verschieben.
- Die Hauptdatei fuer Admin-Messaging auf wenige klar erkennbare Aufrufe reduzieren.

Nicht Teil dieser Subtask

- Inbox- und Complaint-Queue-Moderation.
- Topic/Post/Reply-Lifecycle.
- Mobile-Swipe-Logik.

Akzeptanzkriterien

- Admin-Messaging-Steuerung liegt nicht mehr als Inline-Block in `IdentityConnectedView.kt`.
- Kein neu entstandener Admin-Messaging-Teil ist groesser als 300 Zeilen.
- User-Card-Oeffnen, History-Laden, Paging und Nachrichtensenden bleiben unveraendert beobachtbar.
- Die Hauptdatei zeigt fuer diesen Bereich nur noch Orchestrierung.
