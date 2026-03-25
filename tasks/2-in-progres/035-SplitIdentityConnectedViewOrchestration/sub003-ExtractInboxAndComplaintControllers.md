Ziel

Inbox- und Complaint-Queue-Steuerung sollen in eigene Moderations-Controller zerlegt werden, damit lokale Snapshot-Logik, Paging und Decision-Flows nicht mehr im Hauptcomponent liegen.

Scope

- Inbox-State-Snapshot, Refresh, Paging, Read/Unread-Filter und lokale Persistenz extrahieren.
- Complaint-Count, Complaint-Queue-Refresh, Paging, Composer und Decision-Flow extrahieren.
- Wo sinnvoll gemeinsame Moderation-Helfer fuer Paging, Snapshot-Anwendung oder Fehlerzustand klar benannt zusammenfassen.
- Die Hauptdatei nur noch ueber klar benannte Rueckgabewerte und Aktionen mit diesen Controllern sprechen lassen.

Nicht Teil dieser Subtask

- Content-Lifecycle.
- Admin-User-Card und Admin-Messaging-History.
- Abschliessende Gesamtpruefung.

Akzeptanzkriterien

- Inbox- und Complaint-Logik ist nicht mehr als langer Inline-Block in `IdentityConnectedView.kt` sichtbar.
- Kein neu entstandener Moderations-Controller ist groesser als 300 Zeilen.
- Read-Filter, Complaint-Filter, Paging und Decision-Flow bleiben funktional gleich.
- Die lokale Inbox-Persistenz ist weiterhin an einer klar auffindbaren Stelle gepflegt.
