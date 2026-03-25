Ziel

Die Steuerlogik fuer Topics, Posts, Replies, Filter, Delete-Flow und Publish/Reply-Lifecycle soll aus `IdentityConnectedView.kt` in klar benannte Controller- oder Hook-Dateien ausgelagert werden.

Scope

- Topic-Auswahl, Session-Persistenz der Topic-Selektion und Filter-Persistenz entflechten.
- Post-Refresh, Post-Paging, Reply-Refresh, Reply-Paging und Reply-Composer-Ablauf aus dem Hauptcomponent ziehen.
- Delete- und Delete-All-Flows so auslagern, dass Content-Manipulationen nicht mehr zwischen Shell- und Moderationslogik liegen.
- Die verbleibende Hauptdatei fuer Content nur noch Datenfluss und Callback-Verdrahtung beschreiben lassen.

Nicht Teil dieser Subtask

- Inbox- oder Complaint-Queue-Steuerung.
- Admin-Messaging.
- Mobile-Swipe- und Observer-Verdrahtung ausser soweit unmittelbar fuer Content-Lifecycle erforderlich.

Akzeptanzkriterien

- Content-bezogene Ablaufsteuerung liegt nicht mehr inline in `IdentityConnectedView.kt`.
- Kein neu entstandener Content-Controller ist groesser als 300 Zeilen.
- Topic-, Post- und Reply-Interaktionen verhalten sich unveraendert.
- Die Hauptdatei verliert deutlich sichtbare Mengen an fachlicher Content-Logik.
