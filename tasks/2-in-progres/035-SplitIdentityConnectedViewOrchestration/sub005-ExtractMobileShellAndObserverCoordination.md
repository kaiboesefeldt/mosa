Ziel

Mobile-Pane-Logik, Swipe-Steuerung, Resize-Erkennung, Outside-Click-Handler und IntersectionObserver-Verdrahtung sollen aus der Hauptdatei in kleine Shell-Controller zerlegt werden.

Scope

- Mobile-Pane-Umschaltung und Touch-Geraete-Erkennung auslagern.
- Swipe-Targets, Swipe-Offset und Touch-Handler in einen eigenen mobilen Shell-Bereich verschieben.
- Observer-Verdrahtung fuer Post- und Reply-Paging aus dem Hauptcomponent extrahieren.
- Outside-Click-Handling fuer Inbox, Complaint-Queue und Admin-User-Card in kleine wiederverwendbare Shell-Helfer oder Hooks verschieben.

Nicht Teil dieser Subtask

- Fachliche Aenderungen an Content, Inbox oder Admin-Messaging.
- Neue UI-Struktur oder andere Bedienkonzepte.

Akzeptanzkriterien

- Mobile- und Shell-Verdrahtung ist nicht mehr als grosser gemischter Effekt-Block in `IdentityConnectedView.kt` vorhanden.
- Kein neu entstandener Shell- oder Mobile-Teil ist groesser als 300 Zeilen.
- Swipe-, Resize-, Outside-Click- und Observer-Verhalten bleibt gleich.
- Die Hauptdatei enthaelt fuer Shell-Aspekte nur noch knappe Zusammensetzung.
