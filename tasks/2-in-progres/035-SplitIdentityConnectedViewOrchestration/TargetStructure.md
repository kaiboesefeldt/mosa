Verbindlicher Zielzuschnitt fuer Task 035

Die verbleibende View-Orchestrierung wird in folgende Teile geschnitten. Die Zeilenbudgets sind harte Leitplanken fuer die jeweilige Datei.

- `IdentityConnectedView.kt`: nur Service-Zugriff, Hook-Komposition und Shell-Zusammensetzung, Ziel `<= 220` Zeilen
- `IdentityConnectedViewContentController.kt`: Topic-, Post- und Reply-Lifecycle mit Selektion, Publish, Delete und Filter, Ziel `<= 280` Zeilen
- `IdentityConnectedViewContentState.kt`: kleine pure Helfer fuer Content-State und Auswahlauflosung, Ziel `<= 180` Zeilen
- `IdentityConnectedViewModerationController.kt`: Inbox- und Complaint-Queue-Ablaufsteuerung, Ziel `<= 280` Zeilen
- `IdentityConnectedViewAdminController.kt`: Admin-User-Card, History und Admin-Messaging, Ziel `<= 260` Zeilen
- `IdentityConnectedViewMobileShell.kt`: Mobile-Pane-, Resize-, Swipe- und Observer-Verdrahtung, Ziel `<= 300` Zeilen
- `IdentityConnectedViewUiState.kt`: kleine berechnete UI-Ableitungen fuer Badges, sichtbare Listen und Overlay-Flags, Ziel `<= 180` Zeilen

Bewusst in der Hauptdatei verbleibt nur:

- Koin-basierte Service-Aufloesung
- die Hook-Aufrufe fuer die extrahierten Controller
- die abschliessende Zusammensetzung der bereits vorhandenen UI-Bausteine

Bewusst nicht Teil des Zuschnitts sind:

- die bereits extrahierten Render-Dateien `IdentityConnectedViewContentUi.kt`, `IdentityConnectedViewComposerUi.kt`, `IdentityConnectedViewModerationUi.kt` und `IdentityConnectedViewAdminUi.kt`
- generische projektweite Utilities ausserhalb dieser View
