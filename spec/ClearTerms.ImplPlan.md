# ClearTerms Implementierungsplan (Pod + Client)

## 1. Ziel und Scope

Dieser Plan beschreibt die Laufzeit-Integration von ClearTerms im MOSA-Pod und im Client.

Im Scope:
- Bereitstellung von ClearTerms-Artefakten über Pod-Endpoints
- Interne Pflege der Inhalte in YAML + Markdown-Textblöcken
- Generierung kanonischer JSON-Artefakte beim Pod-Start inkl. Hash-Berechnung und Referenzverkettung
- Striktes Rendering und Nutzung im Client
- Sichtbare Zugänglichkeit von Datenschutzerklärung, Nutzungsbedingungen und Impressum im Client

Nicht im Scope:
- Version-Historie oder Mehrfach-Version-Auslieferung
- `bundle`-Endpoint
- Signatur über Deviation-Profile
- Externer Baseline-Server (kann später separat entstehen)

## 2. Architekturentscheidungen (festgelegt)

1. Keine `bundle`-API. Es gibt getrennte Endpoints für Baseline, Default und Deviation.
2. Keine Mehrfachversionen in dieser Software. Pro Dokumenttyp wird genau ein aktueller Satz ausgeliefert.
3. Generierung der JSON-Artefakte erfolgt beim Pod-Start.
4. Wenn Generierung oder serverseitige Artefakt-Validierung fehlschlägt, startet der Pod nicht weiter (fail-fast).
5. Client ist bei Fetch-/Validierungsfehlern strikt: dann kein Zugriff auf den Pod (harte Sperre).
6. Keine zusätzliche kryptografische Signatur über Deviation im aktuellen Schritt.

## 3. Dokumenttypen

Initial werden drei Dokumenttypen integriert:
- `privacy` (Datenschutzerklärung)
- `terms` (Nutzungsbedingungen)
- `imprint` (Impressum)

Jeder Dokumenttyp besteht aus:
- Baseline
- Default Profile
- Deviation Profile

## 4. Datenquelle im Pod (YAML)

### 4.1 Source of Truth

Die Artefakte werden intern als YAML gepflegt.  
Textblöcke werden als Markdown in den YAMLs geführt.

### 4.2 Wichtige Modellregel

Die YAMLs entsprechen semantisch den ClearTerms-JSONs, mit einer Ausnahme:
- Referenzen in YAML enthalten **keine Hashes**.
- Hashes werden erst aus den generierten kanonischen JSONs berechnet und dann in die JSON-Referenzen eingetragen.

### 4.3 Empfohlene Ablage

Pod-Ressourcen, z. B.:
- `server/src/main/resources/clearterms/privacy/baseline.yaml`
- `server/src/main/resources/clearterms/privacy/default.yaml`
- `server/src/main/resources/clearterms/privacy/deviation.yaml`
- analog für `terms` und `imprint`

## 5. Generierungspipeline beim Pod-Start

Für jeden Dokumenttyp (`privacy`, `terms`, `imprint`) wird beim Start eine feste Pipeline durchlaufen:

1. YAML laden (Baseline, Default, Deviation).
2. YAML in interne ClearTerms-Modelle konvertieren.
3. Baseline und Default zunächst als JSON ohne Hash-in-Referenz serialisieren.
4. Kanonisierung und Hash-Berechnung:
   - Baseline: canonical JSON -> SHA-256 -> `sha256:<hex>`
   - Default: canonical JSON -> SHA-256 -> `sha256:<hex>`
5. Referenzen einsetzen:
   - Default.baseline.hash = Baseline-Hash
   - Deviation.default.hash = Default-Hash
6. Finale JSONs erzeugen.
7. Strikte serverseitige Validierung der Artefakte:
   - JSON-Struktur und Referenzkonsistenz (inkl. `id`/`version`/`hash`) müssen gültig sein.
   - Es findet kein Rendering im Pod statt.
8. Ergebnis im Speicher ablegen und für Endpoints bereitstellen.

Fail-fast-Regel:
- Jeder Fehler in Parsing, Mapping, Kanonisierung, Hashing oder serverseitiger Artefakt-Validierung bricht den Pod-Start ab.

## 6. Pod-API

Keine Bundle-API, nur getrennte Endpoints:

- `GET /v1/legal/{docType}/baseline`
- `GET /v1/legal/{docType}/default`
- `GET /v1/legal/{docType}/deviation`

Parameter:
- `{docType}` in `{privacy, terms, imprint}`

Antwort:
- jeweiliges ClearTerms-JSON als UTF-8 JSON

Fehlerfälle:
- Unbekannter `docType` -> 404
- Laufzeitfehler bei Auslieferung -> 500 (sollte bei erfolgreichem Start praktisch nicht auftreten)

## 7. Validierung und Betriebsverhalten

Artefakt-Validierung wird zentral beim Start durchgeführt, nicht erst bei Request.

Konsequenzen:
- Pod liefert nur verifizierte, intern konsistente Artefakte aus.
- Inkonsistenzen werden als Deployment-/Konfigurationsfehler behandelt.
- Es gibt keinen „best effort“-Fallback.
- Der Pod rendert keine Rechtstexte. Rendering passiert ausschließlich im Client.

## 8. Client-Integration

## 8.1 Datenfluss

Für gewählten Dokumenttyp und Sprache:
1. Baseline JSON laden
2. Default JSON laden
3. Deviation JSON laden
4. `ClearTermsRenderer` mit diesen drei JSONs ausführen
5. Ergebnis-Markdown in HTML rendern (Markdown-Lib)
6. HTML anzeigen

## 8.2 Striktes Fehlerverhalten

Wenn einer dieser Schritte fehlschlägt (Fetch, Parsing, Renderer-Validierung):
- Pod-Zugriff im Client wird blockiert.
- Nutzer sieht einen klaren Fehlerzustand (kein stilles Fallback).

Das ist absichtlich streng und entspricht dem Trust-Modell.

## 8.3 UI-Zugänglichkeit

Rechtstexte werden gut sichtbar erreichbar gemacht:
- Footer-Links zu Datenschutz, Nutzungsbedingungen, Impressum
- Zusätzlich im Login/Registrierungskontext verlinken

Empfohlene Seitenstruktur:
- Eigene Legal-Route mit Auswahl/Unterseiten für `privacy`, `terms`, `imprint`

## 9. Implementierungsschritte (inkrementell)

1. Server:
   - YAML-Modelle + Loader für die drei Dokumenttypen
   - Generator-Service (`yaml -> json -> canonical hash -> references`)
   - Start-up Hook mit fail-fast
2. Server:
   - Drei Read-Endpoints pro Dokumenttyp
   - Tests für Endpoint-Verhalten und Start-up-Fail-fast
3. Inhalte:
   - Erste Entwürfe in YAML/Markdown für `privacy`, `terms`, `imprint`
4. Client:
   - Fetch-Schicht für drei Endpoints
   - Renderer-Integration + Markdown->HTML
   - Striktes Sperrverhalten bei Fehlern
5. Client:
   - UI-Einstiegspunkte (Footer + Auth-Kontext)
6. Späterer Ausbau:
   - UI für Abweichungsanzeige (Deviation vs. Default/Baseline)
7. Qualität:
   - `./gradlew check` muss grün

## 10. Teststrategie

Server-Tests:
- YAML-Laden und Mapping
- Hash-Berechnung und Referenzverkettung
- Fail-fast bei fehlerhaften YAMLs/Inkonsistenzen
- Endpoint-Tests je `docType` und Artefakt-Typ

Client-Tests:
- Erfolgsfall: 3 Fetches -> Rendering -> Anzeige
- Fehlerfälle: einzelner Fetch/Renderer-Fehler blockiert Pod-Zugriff
- Sichtbarkeit der Links/Routes für `privacy`, `terms`, `imprint`

## 11. Offene Punkte (bewusst zurückgestellt)

1. Mehrfachversionierung und historische Auslieferung
2. Externer Baseline-/Default-Server
3. Signatur auf Deviation
4. Bundle-Endpoint
5. Detaillierte Abweichungsanzeige im Client (späterer Ausbau)

Diese Punkte sind explizit nicht Teil dieser Iteration und können später separat geplant werden.
