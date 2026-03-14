Endpoints sollen künftig nicht mehr einzelne Profilstufen getrennt ausliefern, sondern pro Dokumentart genau einen Endpoint für die vollständige Dokumentkette.

Die Kette wird über Parent-Referenzen aufgebaut. Ausgangspunkt ist immer das Operator-Profil. Von dort wird rückwärts über die referenzierten Basisprofile gegangen, bis die referenzierte Baseline erreicht ist. Der API-Response trennt die Baseline dabei ausdrücklich von der Profilkette: `baseline` enthält genau das kanonische Baseline-JSON, `profiles` enthält die zugehörige Profilkette in fachlich eindeutiger und deterministischer Reihenfolge von der ersten Profilstufe bis zum Operator-Profil.

Die Aufgabe betrifft aktuell diese Dokumentarten:

- Privacy
- Terms

Der neue API-Vertrag soll ein strukturiertes JSON-Objekt liefern, damit Baseline und Profilkette fachlich sauber getrennt bleiben:

- `baseline`: kanonisches JSON der Baseline
- `profiles`: geordnete Liste kanonischer Profil-JSONs von der ersten Profilstufe bis zum Operator-Profil

Die bisherigen Endpoints für einzelne Profilstufen werden ersetzt. Abwärtskompatibilität ist nicht erforderlich, weil noch keine Software im Feld ist.

Die Aufgabe umfasst mindestens diese Punkte:

- Die bisherige Auslieferung einzelner Profiltypen über Endpoints wird durch genau einen Endpoint pro Dokumentart ersetzt.
- Alle betroffenen Clients und internen Aufrufer werden auf den neuen Vertrag umgestellt.
- Die Reihenfolge der ausgelieferten Profilkette in `profiles` ist stabil und ergibt sich ausschließlich aus der Parent-Kette, nicht aus Dateinamen, Discovery-Reihenfolge oder anderen indirekten Kriterien.
- Jedes Profil in der Kette muss unmittelbar das vorhergehende Dokument referenzieren.
- Das Operator-Profil ist immer das letzte Profil in der gelieferten Kette.
- Die Lösung muss zusätzliche künftige Zwischenprofile unterstützen, ohne das Endpoint-Modell erneut grundlegend anzupassen.
- Es gibt keine Einzel-Endpunkte mehr für Default-, Mosa-, Service- oder Operator-Profile.

Validierung und Fehlerverhalten:

- Beim Pod-Start wird für jede betroffene Dokumentart geprüft, dass die vollständige Kette vom Operator-Profil bis zur Baseline konsistent aufgebaut werden kann.
- Wenn ein benötigtes Zwischenprofil oder eine benötigte Baseline fehlt oder mit dem Softwarestand inkompatibel ist, darf der Pod nicht starten.
- Wenn der Pod erfolgreich gestartet ist, ist garantiert, dass die Dokumentketten konsistent sind. Die Endpoints liefern daher niemals absichtlich Teilketten als Laufzeit-Fallback.
- Der Error-Log beim Startfehler muss klar und deutlich benennen:
  - die betroffene Dokumentart
  - das betroffene Ziel-Operator-Profil
  - die fehlende oder inkompatible referenzierte Versionsnummer
  - den konkreten Fehlergrund
  - die im Pod verfügbaren Versionsnummern der passenden Dokumentart

Fachliche Leitplanken:

- Zusätzliche künftige Profiltypen werden frei über Parent-Beziehungen eingeordnet.
- Es ist zulässig, dass künftige Minor- oder andere Versionierungsschritte durch zusätzliche Zwischenprofile modelliert werden.
- Die Implementierung darf nicht voraussetzen, dass es dauerhaft genau feste Profilstufen wie Default, Mosa, Service und Operator gibt.
