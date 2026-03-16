Für die Admin-Visualisierung im Client soll ein gezieltes Refactoring umgesetzt werden.

Auslöser ist, dass die jüngsten Fixes zwar funktional korrekt sind, aber der Weg dorthin unnötig zäh war. Das deutet auf Schwächen in der Zustandsmodellierung und in der Verteilung der Verantwortlichkeiten hin.

Ziel ist nicht, das UI neu zu gestalten, sondern die bestehende Lösung wartbarer, klarer und robuster zu machen.

Die Aufgabe umfasst mindestens diese Punkte:

- Die fachlichen Zustände `verbundene Identität`, `aktuell angezeigte Identität`, `Admin-Status der verbundenen Identität` und `Admin-Status der angezeigten Identität` im Client klar voneinander trennen.
- Die dazugehörige Logik aus der großen View-Komponente [IdentityConnectedView.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityConnectedView.kt) herauslösen.
- Die Fähigkeit zur GID-bezogenen Admin-Abfrage über den Pod beibehalten, aber im Client klarer kapseln.
- Die bestehenden Race-Condition-Sicherungen gegen veraltete Capability-Responses so umformen, dass sie verständlicher und lokal begrenzt sind.
- Zustandsübergänge rund um Profilnavigation und Wechsel der verbundenen Identität gezielt mit Tests absichern.

Refactoring-Zielbild:

- [IdentitySessionStateMachine.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySessionStateMachine.kt) soll den fachlichen Verbindungszustand explizit und nachvollziehbar modellieren.
- [IdentityConnectedView.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityConnectedView.kt) soll vor allem rendern und UI-Ereignisse verdrahten, nicht mehrere fachliche Statusmaschinen nebenbei verwalten.
- Die Logik für Header-Badge und Profil-Badge soll an genau einer gut auffindbaren Stelle zusammengeführt werden.
- [ContentCapabilitiesService.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/content/ContentCapabilitiesService.kt) soll eine kleine, selbsterklärende API für Admin-Capabilities bereitstellen, statt nur ein dünner Durchreicher zu sein.

Bei der Umsetzung soll insbesondere wie folgt refactored werden:

- In [IdentitySessionStateMachine.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySessionStateMachine.kt) ein kleines explizites Modell für den Verbindungsbezug der aktuell angezeigten Identität schaffen.
- In [IdentitySessionViewState.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySessionViewState.kt) und [IdentitySession.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySession.kt) nur die dafür nötigen, fachlich klar benannten Daten nach außen geben.
- Die Admin-Präsentationslogik aus [IdentityConnectedView.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityConnectedView.kt) in eine kleine dedizierte Logikkomponente oder einen klar abgegrenzten Hook auslagern.
- Dort explizit unterscheiden zwischen:
  - Admin-Status der verbundenen Identität für die Hauptseite
  - Admin-Status der aktuell angezeigten Identität für die Profilansicht
- Die GID-bezogene Abfrage über [PodAccess.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/pod/PodAccess.kt) und [ContentCapabilitiesService.kt](/home/kai/Projects/straightway/mosa/client/src/jsMain/kotlin/straightway/mosa/client/content/ContentCapabilitiesService.kt) so kapseln, dass die View keine verstreute Fetch- und Versionslogik mehr enthalten muss.

Akzeptanzkriterien:

- Das Header-Badge auf der Hauptseite zeigt immer den Admin-Status der tatsächlich verbundenen Identität.
- Das Badge in der Profilansicht zeigt immer den Admin-Status der aktuell angezeigten Identität.
- Beim Blättern in der Profilansicht wird kein Profil versehentlich verbunden oder aktiviert.
- Beim Wechsel zwischen Admin- und Nicht-Admin-Profilen können keine veralteten Capability-Responses mehr sichtbar durchschlagen.
- Die fachliche Zustandslogik für Admin-Präsentation ist in kleineren, klar benannten Einheiten organisiert und nicht mehr verstreut in der View verdrahtet.
- Für die wichtigsten Übergänge existieren gezielte Tests, insbesondere:
  - verbundenes Admin-Profil -> anderes Profil -> zurück
  - verbundenes Nicht-Admin-Profil -> verbundenes Admin-Profil
  - Nicht-Admin-Profil im Overlay -> Admin-Profil im Overlay
  - Wechsel der verbundenen Identität mit unterschiedlichem Admin-Status

Nicht Ziel dieser Aufgabe:

- keine Änderung am sichtbaren UI-Design
- keine Erweiterung des Rechtekonzepts über den bestehenden Admin-Status hinaus
- kein großflächiger Umbau der gesamten Connected-View jenseits des betroffenen Statusbereichs
