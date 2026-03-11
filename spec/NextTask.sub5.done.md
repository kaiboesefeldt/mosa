# Next Task Subtask 5: Browser-Client verifiziert empfangene Posts und Replies

## Ziel

Der Browser-Client soll empfangene Posts und Replies vor Cache und Anzeige kryptographisch verifizieren.

## Voraussetzungen

- `NextTask.sub1.md`
- `NextTask.sub3.md`

## Umzusetzen

### 1. Clientseitigen Verifizierer einfuehren

Fuehre eine Client-Komponente ein, z. B. `ContentSignatureVerifier`, die fuer einen empfangenen Eintrag prueft:

1. `authorPublicKey` base64url-dekodierbar
2. `SHA-256(authorPublicKeyRaw)` passt zu `authorGID`
3. `signatureVersion` wird unterstuetzt
4. kanonischer Payload passt zu `content` und `rootId`
5. `authorSignature` ist gueltig

### 2. Cache-Modell erweitern

Erweitere `CachedPost` um:

- `authorPublicKey`
- `authorSignature`
- `signatureVersion`

`authorGID` bleibt erhalten, wird aber als aus dem Public Key abgeleitete Information behandelt.

### 3. Fetch-Pfade absichern

Verifiziere Inhalte direkt nach dem Empfang und vor dem Caching in:

- `TopicSearchService`
- `ReplySearchService`

Nur erfolgreich verifizierte Eintraege duerfen:

- zu `CachedPost` gemappt werden
- im Cache landen
- spaeter in der UI auftauchen

### 4. Fehlerbehandlung

Bei ungueltigen Eintraegen:

- Eintrag verwerfen
- Warnung loggen
- keinen stillen Cache-Eintrag erzeugen

Falls in einem Ergebnis Eintraege verworfen wurden, darf die Suche insgesamt weiter funktionieren.

## Nicht Teil dieser Unteraufgabe

- Kein neues UI-Design
- Keine komplexe User-Meldungslogik ausser einer minimalen Warnungsmoeglichkeit, falls bereits eine geeignete Stelle existiert

## Tests

Schreibe oder erweitere Tests fuer:

1. Gueltiger `FetchResult.Entry` wird akzeptiert und gecacht.
2. Ungueltige Signatur fuehrt dazu, dass der Eintrag verworfen wird.
3. Falsche Kombination aus `authorPublicKey` und `authorGID` wird verworfen.
4. Replies werden mit ihrer `rootId` verifiziert.
5. Teilgueltige Ergebnislisten bleiben benutzbar; nur fehlerhafte Eintraege fallen heraus.

## Abnahmekriterien

- Browser-Client zeigt nur erfolgreich verifizierte Inhalte an.
- Ungueltige Inhalte landen nicht im Cache.
- GID und Public Key werden immer gegeneinander geprueft.

