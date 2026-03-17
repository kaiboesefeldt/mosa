Ziel

Gemeinsame Build- und Shared-Abhaengigkeiten auf die in der Inventarisierung festgelegten stabilen Zielversionen anheben, ohne die Projektgrundstruktur zu veraendern.

Scope

- Versionen im Root-Build und im Version-Catalog aktualisieren, soweit diese gemeinsam genutzt werden.
- Direkt deklarierte Plugins und gemeinsame Kotlin-Multiplatform-Abhaengigkeiten in `shared` aktualisieren.
- Erforderliche kleine Code- oder Build-Anpassungen in `shared` vornehmen, wenn sie unmittelbar aus den Upgrades folgen.

Nicht Teil dieser Subtask

- Server-spezifische Laufzeit- und Testanpassungen.
- Client-spezifische Wrapper-, Browser- oder NPM-Upgrades.

Akzeptanzkriterien

- Gemeinsame Versionsdefinitionen sind auf die geplanten Zielversionen angehoben.
- `shared` baut und seine bestehenden Tests laufen wieder grün.
- Etwaige notwendige Anpassungen bleiben auf direkt upgradebedingte Aenderungen begrenzt.
