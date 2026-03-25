Ziel

Die fachlich riskanten Bereiche von `IdentityConnectedView` sollen vor groesseren Strukturveraenderungen ueber kleine, gezielte Tests und klar benannte Zustandsausschnitte abgesichert werden.

Scope

- Die bestehenden oeffentlich beobachtbaren Verhaltensweisen rund um Inbox, Complaint-Queue, Admin-Message-Composer, Composer-Dialog und mobile Pane-Navigation identifizieren.
- Fuer diese Ausschnitte vor dem eigentlichen Refactoring die kleinstmoeglichen automatisierten Tests ergaenzen oder schaerfen.
- Reine Zustands- und Ableitungslogik aus `IdentityConnectedView.kt` in kleine, gut benannte Typen, Hilfsfunktionen oder State-Hooks auslagern, wenn sie ohne UI-Redesign isolierbar ist.
- Die Trennung zwischen reinen Ableitungen und Render-Callbacks expliziter machen, damit spaetere Dateiextraktionen auf bereits abgesicherter Flaeche passieren.

Nicht Teil dieser Subtask

- Grosse Renderbloecke in neue UI-Dateien verschieben.
- Den sichtbaren Aufbau der Connected-View neu gestalten.
- Abschlusspruefung der Gesamtintegration.

Akzeptanzkriterien

- Vor den groesseren Extraktionen existieren gezielte automatisierte Tests fuer die fachlich riskanten Uebergaenge der betroffenen Ausschnitte, soweit sie ueber oeffentliches Verhalten erreichbar sind.
- Reine Zustands- oder Ableitungslogik fuer mindestens Inbox, Complaint-Queue oder mobile Pane-Navigation liegt nicht mehr anonym verstreut im Hauptcomponent.
- Die neuen Hilfstypen oder Hooks sind fachlich benannt und koennen ohne Kenntnis der gesamten View verstanden werden.
- `IdentityConnectedView.kt` behaelt nach dieser Subtask noch die Gesamtverdrahtung, aber weniger inline Logik fuer Zustandsableitungen.
