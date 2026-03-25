Ziel ist, die verbleibende Orchestrierungsdatei `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityConnectedView.kt` weiter zu zerlegen, bis kein beteiligter Teil groesser als 300 Zeilen ist.

Ausgangslage:

- Nach Task 034 liegen die grossen Renderbloecke bereits in eigenen Dateien.
- `IdentityConnectedView.kt` hat trotzdem noch rund 2000 Zeilen und vereint weiterhin zu viele Verantwortlichkeiten in einem einzelnen Component-Body.
- Die Datei enthaelt noch gleichzeitig:
  - Service-Injektion und globale State-Deklarationen
  - Inbox-, Complaint- und Admin-Messaging-Ablaufsteuerung
  - Topic-, Post- und Reply-Lifecycle
  - Mobile-Pane-, Swipe- und Observer-Steuerung
  - Header-, Shell- und Overlay-Verdrahtung

Zielbild:

- `IdentityConnectedView.kt` wird auf eine duenne Zusammensetzungsdatei reduziert.
- Fachlich zusammengehoerige Steuerlogik liegt in mehreren klar benannten Hooks oder Hilfstypen.
- Kein neu entstandener Teil fuer diese View ist groesser als 300 Zeilen.
- Die bestehende Funktion bleibt unveraendert.

Bevorzugter Zuschnitt:

- Eine kleine Hauptdatei fuer Service-Zugriff und Zusammensetzung.
- Ein dedizierter Bereich fuer Content-Lifecycle und Topic/Post/Reply-Selektion.
- Ein dedizierter Bereich fuer Inbox- und Complaint-Moderation.
- Ein dedizierter Bereich fuer Admin-User-Card und Admin-Messaging.
- Ein dedizierter Bereich fuer Mobile-Pane-, Swipe- und Observer-Verdrahtung.
- Kleine Shared-Helfer fuer wiederkehrende Setter-, Snapshot- oder Scroll-Logik, sofern sie Benennung und Lesbarkeit verbessern.

Wichtige Randbedingungen:

- Kein sichtbares Verhalten aendern.
- Keine Sammeldatei schaffen, die den Monolithen nur verlagert.
- Die 300-Zeilen-Grenze gilt fuer die neu entstehenden Teile dieser View-Struktur als harte Leitplanke.
- Die bestehenden Client-Tests fuer `IdentityConnectedView` bleiben die primaere Absicherung; bei Bedarf duerfen gezielte Tests fuer extrahierte, oeffentlich erreichbare Logik ergaenzt werden.

Festgelegter Zielzuschnitt:

- `IdentityConnectedView.kt`: nur Service-Zugriff, Hook-Komposition und Shell-Zusammensetzung, Ziel `<= 220` Zeilen
- `IdentityConnectedViewContentController.kt`: Topic-, Post- und Reply-Lifecycle, Ziel `<= 280` Zeilen
- `IdentityConnectedViewContentState.kt`: kleine pure Content-State-Helfer, Ziel `<= 180` Zeilen
- `IdentityConnectedViewModerationController.kt`: Inbox- und Complaint-Queue-Steuerung, Ziel `<= 280` Zeilen
- `IdentityConnectedViewAdminController.kt`: Admin-User-Card, History und Messaging, Ziel `<= 260` Zeilen
- `IdentityConnectedViewMobileShell.kt`: Mobile-Pane-, Resize-, Swipe- und Observer-Verdrahtung, Ziel `<= 300` Zeilen
- `IdentityConnectedViewUiState.kt`: kleine berechnete UI-Ableitungen, Ziel `<= 180` Zeilen
