Für die Anwendung soll ein Impressum realisiert werden.

Ziel ist, die rechtlich erforderlichen Anbieterinformationen für Nutzerinnen und Nutzer leicht auffindbar bereitzustellen. Das Impressum soll in die bestehende Anwendung integriert sein und inhaltlich so umgesetzt werden, dass spätere Pflege und Aktualisierung unkompliziert bleiben.

Das Impressum läuft ausdrücklich nicht über ClearTerms. Stattdessen wird ein eigenes YAML-basiertes Modell für Impressumsangaben eingeführt.

Die Aufgabe umfasst mindestens diese Punkte:

- Einen gut auffindbaren Einstieg zum Impressum in der Anwendung vorsehen.
- Eine eigene Darstellung für das Impressum mit den erforderlichen Angaben umsetzen.
- Die Impressumsdaten aus einer YAML-Datei des Operators laden.
- Sicherstellen, dass die Darstellung auf Desktop und Mobilgeräten brauchbar funktioniert.
- Berücksichtigen, wie sich das Impressum in die bestehende Navigation und Seitenstruktur einfügt.

## Datenmodell

Die Impressumsdaten liegen in genau einer Datei `imprint.yaml`.

Die Datei liegt im CWD des Mosa-Servers, analog zur Ablage von Privacy und AGB.

Jeder Pod hat genau einen Betreiber und damit genau eine maßgebliche Jurisdiktion. Mehrere jurisdiktionsspezifische Varianten innerhalb eines Pods sind nicht Teil dieses Modells.

Es gibt keine frei definierbaren Blöcke. Stattdessen gibt es ein festes minimales Schema mit optionalen semantischen Blöcken.

## Einstieg in der Oberfläche

Der bestehende Einstieg über das Menü `Rechtliches` kann beibehalten werden.

Dabei gilt:

- Innerhalb dieses Menüs muss der Eintrag `Impressum` klar benannt sein.
- Der Einstieg muss stabil und dauerhaft erreichbar sein.
- Das Impressum darf nicht nur implizit in einem allgemeinen Rechtetext aufgehen.

## Minimales Schema

Die Daten sind als YAML-Dictionary mit diesen bekannten Blöcken aufgebaut:

- `provider`
- `address`
- `contact`
- `register`
- `tax`
- `supervision`
- `editorial`
- `additional`

Die Blöcke haben mindestens diese möglichen Angaben:

- `provider`
  - `name`: Name des Anbieters oder der Organisation
  - `legal_form`: Rechtsform, falls vorhanden
  - `representative`: Vertretungsberechtigte Person oder Stelle, falls vorhanden
- `address`
  - `street`
  - `postal_code`
  - `city`
  - `region`
  - `country`
- `contact`
  - `email`
  - `phone`
  - `website`
- `register`
  - `court`
  - `number`
  - `name`
- `tax`
  - `vat_id`
  - `business_id`
  - `other`
- `supervision`
  - `authority`
  - `address`
  - `website`
- `editorial`
  - `responsible_person`
  - `address`
- `additional`
  - `markdown`

Die Struktur innerhalb der verschachtelten `address`-Angaben ist dieselbe wie im Block `address`.

## Pflicht und Optionalität

Für das minimale Modell gelten zunächst diese Pflichtangaben in der Basisdatei:

- `provider.name`
- `address.street`
- `address.postal_code`
- `address.city`
- `address.country`
- `contact.email`

Alle anderen Angaben sind optional.

Wenn Pflichtangaben fehlen, darf der Server nicht erfolgreich hochfahren. Der Fehler soll mit einem aussagekräftigen Error-Log sichtbar gemacht werden.

## Rendering

Das Rendering erfolgt datengetrieben anhand der vorhandenen Blöcke und Angaben.

- Es gibt eine feste Blockreihenfolge:
  1. `provider`
  2. `address`
  3. `contact`
  4. `register`
  5. `tax`
  6. `supervision`
  7. `editorial`
  8. `additional`
- Ein Block wird nur gerendert, wenn er relevante Angaben enthält.
- `null`-Angaben werden beim Rendering grundsätzlich weggelassen.
- Eine Angabe, die im minimalen Schema für den Pflichtkern vorgesehen ist, darf nicht stillschweigend zu einer unvollständigen Darstellung führen. Fehlende Pflichtangaben müssen als Konfigurationsfehler behandelt werden.
- `additional.markdown` wird als letzter Block gerendert und dient für zusätzliche Freitextangaben, die nicht sinnvoll in die festen Blöcke passen.

## Nicht-Ziele

Diese Aufgabe umfasst ausdrücklich nicht:

- Modellierung über ClearTerms
- Übersetzung des Impressums
- Frei definierbare zusätzliche Blöcke
- Mehrere Jurisdiktionsvarianten pro Pod
- Vollständige rechtliche Validierung aller Länder
