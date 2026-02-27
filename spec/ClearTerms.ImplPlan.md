# ClearTerms Implementierungsplan

## 1. Ziel und Scope

Dieser Plan beschreibt die Integration von ClearTerms im MOSA-Pod und im Client.

Im Scope:
- Bereitstellung von ClearTerms-Artefakten über Pod-Endpoints
- Pflege der Inhalte in YAML (inkl. Markdown-Textblöcken)
- Erzeugung kanonischer JSON-Artefakte mit Hash-Verkettung
- Clientseitiges Rendering und Anzeige der Rechtstexte

Nicht im Scope:
- Version-Historie oder Mehrfach-Version-Auslieferung
- Bundle-Endpoint
- Zusätzliche Signatur auf Profil-Artefakten
- Externer Baseline-/Profil-Server

## 2. Ist-Stand (Server)

Umgesetzt:
- YAML-zu-kanonischem-JSON-Konvertierung für Baseline + Profilkette inkl. Hash-Eintrag in Parent-Referenzen
- Bereitstellung der Privacy-Dokumente über `LegalService` und `LegalController`
- Endpoints:
  - `GET /v1/legal/privacy/baseline`
  - `GET /v1/legal/privacy/default`
  - `GET /v1/legal/privacy/mosa`
  - `GET /v1/legal/privacy/operator`
- Rückgabeformat: kanonisches ClearTerms-JSON (`application/json`)
- Betreiberprofil-Quelle ist Datei `mosa_privacy.yaml` (Pfad relativ zum Startverzeichnis)
- Fallback-Verhalten ist implementiert:
  - fehlt oder ist das Betreiberprofil unvollständig/inkonsistent, wird ein synthetisches Service-Profil erzeugt
  - bei fachlich ungültigen Placeholder-Wertetypen wird der Start abgebrochen (`IllegalStateException`)
- Tests für Konvertierung, Hash-Kette, Service-Fallback und Endpoints sind vorhanden

## 3. Datenquelle (Privacy)

Aktuell genutzte Ressourcen:
- `server/src/main/resources/clearterms/privacy/privacy-gdpr-baseline.yaml`
- `server/src/main/resources/clearterms/privacy/privacy-gdpr-default.yaml`
- `server/src/main/resources/clearterms/privacy/mosa-privacy-gdpr-service.yaml`

Regel:
- YAML-Referenzen enthalten keine Hashes.
- Hashes werden aus kanonischem JSON berechnet und in Parent-Referenzen gesetzt.

## 4. Ist-Stand (Client)

Umgesetzt:
- Fetch-Schicht für Privacy-ClearTerms integriert (`PodAccess`):
  - Baseline, Default, MOSA und Operator-Profil werden geladen
- Rendering-Pipeline integriert:
  - `ClearTermsRenderer` rendert sprachspezifische Markdown-Texte
  - Markdown wird im Client in HTML umgewandelt und angezeigt
- Fehler werden nicht still geschluckt:
  - fehlgeschlagene Privacy-Ladung führt zu Fehlerzustand in der UI
- Sichtbarer Einstiegspunkt in der UI vorhanden (Legal-Menü, Privacy-Dialog)

Noch offen:
- `terms` und `imprint` sind in der UI noch Dummy-Inhalte (keine ClearTerms-Quelle)

## 5. Offene Punkte (Server)

1. Dokumenttypen `terms` und `imprint` analog zu `privacy` integrieren (YAML + Endpoints + Tests).
2. Optional: Endpunktdesign vereinheitlichen, falls später wieder ein generisches `docType`-Routing gewünscht ist.

## 6. Qualität

- Für neue Server-Funktionalität gilt: Unit-/Endpoint-Tests mit klarer, deterministischer Abdeckung.
- Ziel bleibt ein grüner `./gradlew check`-Lauf im Gesamtprojekt.
