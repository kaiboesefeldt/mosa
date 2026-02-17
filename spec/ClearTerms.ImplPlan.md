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
- Bereitstellung der Privacy-Dokumente über eigenen Service und eigenen Controller
- Endpoints:
  - `GET /v1/legal/privacy/baseline`
  - `GET /v1/legal/privacy/default`
  - `GET /v1/legal/privacy/mosa`
- Rückgabeformat: kanonisches ClearTerms-JSON (`application/json`)
- Tests für Konvertierung, Hash-Kette, Service und Endpoints

## 3. Datenquelle (Privacy)

Aktuell genutzte Ressourcen:
- `server/src/main/resources/clearterms/privacy/privacy-gdpr-baseline.yaml`
- `server/src/main/resources/clearterms/privacy/privacy-gdpr-default.yaml`
- `server/src/main/resources/clearterms/privacy/mosa-privacy-gdpr-service.yaml`

Regel:
- YAML-Referenzen enthalten keine Hashes.
- Hashes werden aus kanonischem JSON berechnet und in Parent-Referenzen gesetzt.

## 4. Offene Punkte (Server)

1. Startverhalten auf fail-fast für Legal-Artefakte explizit absichern (Artefaktaufbau und Validierung beim Pod-Start erzwingen).
2. Dokumenttypen `terms` und `imprint` analog zu `privacy` integrieren (YAML + Endpoints + Tests).
3. Optional: Endpunktdesign vereinheitlichen, falls später wieder ein generisches `docType`-Routing gewünscht ist.

## 5. Offene Punkte (Client)

1. Fetch-Schicht für die Legal-Endpoints integrieren.
2. Rendering-Pipeline (`ClearTermsRenderer` + Markdown->HTML) in der UI anschließen.
3. Striktes Fehlerverhalten im Client sicherstellen (kein stilles Fallback).
4. Sichtbare Einstiegspunkte für Rechtstexte in der UI vervollständigen.

## 6. Qualität

- Für neue Server-Funktionalität gilt: Unit-/Endpoint-Tests mit klarer, deterministischer Abdeckung.
- Ziel bleibt ein grüner `./gradlew check`-Lauf im Gesamtprojekt.
