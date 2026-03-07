# Umsetzungskonzept: Self-Service Datenexport (CSV) per GET

## Ziel
- Eine gemeinsame Funktion für:
  - Auskunft (Art. 15 DSGVO)
  - Datenübertragbarkeit (Art. 20 DSGVO)
- Auslösung im Client über einen Button.
- Technisch: Download über einen authentifizierten `GET`-Request an den Server.

## UX/Produktverhalten
- Neuer Tool-Button im Identity-Profil: `Meine Daten exportieren (CSV)`.
- Button ist nur aktiv, wenn eine verbundene Identität vorhanden ist (analog zu anderen pod-gebundenen Aktionen).
- Klick startet Export direkt (MVP ohne Dialog).
- Während Export läuft:
  - Button disabled
  - kurzer Statushinweis: `CSV wird erstellt...`
- Bei Erfolg:
  - Browser-Download startet automatisch.
- Bei Fehler:
  - Fehlermeldung wie bei anderen Identity-Aktionen (kurz, retry durch erneuten Klick).

## API-Design (Backend)

### Endpoint
- `GET /v1/posts/export.csv`
- Authentifizierung: Bearer JWT (wie bestehende geschützte `/v1/*`-Routen).
- Request-Parameter (MVP):
  - keine Pflichtparameter

### Response
- `200 OK`
- `Content-Type: text/csv; charset=utf-8`
- `Content-Disposition: attachment; filename="mosa-export-<yyyy-MM-dd>.csv"`
- Body: CSV mit Headerzeile.

### Fehlerfälle
- `401 Unauthorized`: JWT fehlt/ungültig
- `500 Internal Server Error`: unerwarteter Fehler

## CSV-Format

### Header
- `id,type,root_id,stored_at_epoch,stored_at_iso8601,author_gid,text`

### Feldregeln
- `type`: `post` oder `reply` (`reply`, wenn `root_id` gesetzt ist, sonst `post`)
- `root_id`: leer bei Root-Post
- `stored_at_iso8601`: UTC, z. B. `2026-03-07T14:22:05Z`
- `text`: RFC-4180-kompatibel escapen (Anführungszeichen verdoppeln, Feld immer quoten)
- Reihenfolge: deterministisch nach `stored_at_epoch` aufsteigend, bei Gleichstand nach `id`

## Backend-Implementierung

### Routing
- In `ContentController` neue Route im `authenticate`-Block:
  - `getRoute("/posts/export.csv")`
  - ruft `contentService.exportCsvForUser(gid)` auf
  - antwortet als Datei-Download mit den o. g. Headern

### Service
- Neue Service-Funktion:
  - `suspend fun exportCsvForUser(userGID: String): String` (MVP)
  - liest alle Einträge der Identität
  - mappt auf Exportzeilen
  - serialisiert zu CSV
- Wiederverwendung vorhandener Metadaten:
  - `AUTHOR_GID_KEY`
  - `RECEIVED_AT_KEY`
  - `ROOT_ID_KEY`

### Datenzugriff
- Query über vorhandene VectorStore-Suche nach `AUTHOR_GID_KEY = userGID`.
- Für große Datenmengen:
  - paginiert über bestehendes `queryOn`-Konzept iterieren
  - keine harte Größenbegrenzung im MVP, aber implementiert als iterativer Abruf statt einmaliger Vollmaterialisierung im Query-Schritt.

### CSV-Serializer
- Eigenes kleines Utility im Server-Modul (keine neue Dependency):
  - Header schreiben
  - pro Eintrag eine Zeile
  - sauberes Escaping nach RFC-4180

## Frontend-Implementierung

### PodAccess
- Interface + Impl erweitern:
  - `suspend fun exportPostsCsv(jwt: String): ByteArray`
  - GET auf `/v1/posts/export.csv`
  - `Authorization: Bearer <jwt>`
  - Response als Bytes lesen

### Service
- Neuer Client-Service `PostsExportService`:
  - holt JWT via bestehendem Pod-Session-Flow
  - ruft `podAccess.exportPostsCsv(jwt)`
  - triggert Browser-Download über `Blob(type = "text/csv;charset=utf-8")` + temporären `<a download>`

### UI-State
- `IdentitySessionStateMachine`:
  - neue Action `exportPostsCsv()`
  - Busy/Fehlerbehandlung analog zu `downloadIdentity()`
- `IdentityToolsPresenter`:
  - neuer Eintrag mit eigenem Icon (z. B. `fa-file-csv`) und i18n-Key
- i18n ergänzen:
  - `button.identity.export-posts.aria`
  - `button.identity.export-posts.label`
  - `identity.export.inProgress`
  - `identity.export.error`

## Datenschutztext-Anpassung (separater Schritt)
- In `mosa-privacy-gdpr-service.de.yaml` die Rechte-Texte für Auskunft und Datenübertragbarkeit auf dieselbe Funktion referenzieren:
  - `Über „Meine Daten exportieren (CSV)” in {{string:SERVICE_DISPLAY_NAME}}`
- Optional auch EN-Profil analog nachziehen.

## Testkonzept

### Backend Unit-Tests
- CSV-Serializer:
  - escaped Komma, Zeilenumbruch, Anführungszeichen korrekt
  - Header exakt
- `ContentService.exportCsvForUser`:
  - enthält nur Daten des anfragenden `userGID`
  - `type`-Ableitung korrekt (`post`/`reply`)
  - deterministische Sortierung

### Backend Endpoint-Tests
- `GET /v1/posts/export.csv`:
  - `401` ohne Auth
  - `200` mit Auth
  - korrekte Download-Header
  - Body beginnt mit CSV-Header

### Frontend-Tests
- `PodAccess`:
  - korrekter Pfad + Auth-Header
- `PostsExportService`:
  - Download wird mit CSV-MIME ausgelöst
  - Fehlerfall propagiert
- `IdentityToolsPresenter`:
  - Export-Action vorhanden und korrekt enabled/disabled

## Umsetzungsschritte (empfohlen)
1. Backend CSV-Serializer + Service-Methode inkl. Tests (rot/grün/refactor).
2. Endpoint in `ContentController` + Endpoint-Tests.
3. Client `PodAccess` + `PostsExportService` + Tests.
4. UI-Action + i18n + UI-Tests.
5. Datenschutztext-Update.
6. Final: `./gradlew check`.

## Akzeptanzkriterien
- Nutzer kann mit einem Klick eine CSV seiner eigenen Inhalte herunterladen.
- Export läuft über `GET` gegen den Server und verwendet normale Authentifizierung.
- CSV ist in Excel lesbar und technisch robust importierbar.
- Dieselbe Funktion ist in der Datenschutzerklärung für Art. 15 und Art. 20 referenzierbar.
