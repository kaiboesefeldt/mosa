# ClearTerms Legacy-Profile aufraeumen

## Ausgangslage

Im ClearTerms-Code unter `shared` existieren neben dem aktuellen Modell `baseline + n profile` weiterhin die Legacy-Typen `ClearTermsDefaultProfile` und `ClearTermsDeviationProfile` sowie passende Lese- und Render-Pfade.

Die bisherige Pruefung im Repository zeigt:

- Produktive Ressourcen und produktiver Server-Code verwenden bereits `kind: baseline` und `kind: profile`.
- Die Legacy-Typen und der alte Renderer-Pfad werden aktuell nur noch innerhalb von `shared` selbst und in den dazugehoerigen Tests verwendet.
- Endpunkte oder Dateinamen mit `default` bezeichnen weiterhin Dokumentrollen, aber nicht mehr das alte Dokumentformat `kind: default`.

## Ziel

Den ClearTerms-Code auf das Zielmodell `baseline + n profile` vereinfachen und die Legacy-Struktur fuer `default` und `deviation` entfernen, sofern waehrend der Umsetzung keine versteckte produktive Abhaengigkeit auftaucht.

## Plan

1. Absicherung erweitern: gezielte Tests fuer das aktuelle Profilketten-Modell identifizieren oder ergaenzen, damit die gewuenschte Struktur vor dem Entfernen des Legacy-Pfads klar abgesichert ist.
2. Legacy-Lesepfad entfernen: `readDefault`, `readDeviation` sowie die dazugehoerigen Typen und Parsing-Helfer aus `shared` entfernen.
3. Legacy-Renderer-Pfad entfernen: Overloads fuer `baseline + default + deviation` entfernen und alle betroffenen Tests auf Profilketten umstellen.
4. Repo bereinigen: Benennungen, Hilfsfunktionen und Tests vereinfachen, die nur noch wegen des Legacy-Modells existieren.
5. Qualitaetsgate ausfuehren: `./gradlew check` laufen lassen und verbleibende Risiken dokumentieren.

## Randbedingungen

- TDD-Regeln aus `AGENTS.md` einhalten.
- Keine neuen APIs oder Strukturen fuer kuenftige Eventualfaelle einfuehren.
- `AGENTS.md` nur anpassen, wenn sich Architektur oder relevante Arbeitsregeln wirklich aendern.
