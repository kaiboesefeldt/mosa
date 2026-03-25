Ziel

Die Moderations- und Admin-Oberflaechen sollen aus `IdentityConnectedView` in kleinere, klar geschnittene UI-Dateien ausgelagert werden, ohne ihr Verhalten zu aendern.

Scope

- Die Renderteile fuer Inbox-Popover, Complaint-Queue-Popover, Admin-User-Action-Card, Admin-Message-History und Admin-Message-Composer aus `IdentityConnectedView.kt` extrahieren.
- Gemeinsam genutzte UI-Hilfen fuer diese Bereiche an einer gut auffindbaren Stelle zusammenfassen, wenn dadurch Duplikate entfallen.
- Die neue Struktur so schneiden, dass Moderation und Admin-Messaging nicht mehr zwischen Content-Rendering und allgemeiner Seitenverdrahtung vermischt sind.
- Bestehende Tests fuer diese Bereiche stabil halten und gezielt ergaenzen, wenn die Extraktion bislang ungesicherte fachliche Flaeche beruehrt.

Nicht Teil dieser Subtask

- Topic-, Post- oder Reply-Rendering extrahieren.
- Die mobile Shell oder Profil-Navigation umbauen.
- Neue Moderationsfunktionen einfuehren.

Akzeptanzkriterien

- Die Moderations- und Admin-Overlays liegen in passend benannten Dateien ausserhalb von `IdentityConnectedView.kt`.
- `IdentityConnectedView.kt` rendert diese Bereiche nur noch als klar benannte Teilkomponenten oder ueber klar benannte Builder-Funktionen.
- Fachliche Verdrahtung wie Laden, Filtern, Aktionen und Fehleranzeige bleibt unveraendert beobachtbar.
- Die neue Dateistruktur macht ohne tiefes Scrollen auffindbar, wo Inbox, Complaint-Queue und Admin-Messaging gepflegt werden.
