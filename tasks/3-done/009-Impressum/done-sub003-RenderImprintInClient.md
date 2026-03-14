Ziel dieser Subtask ist, das Impressum im Client sichtbar zu machen.

Umfang:

- Den bestehenden Einstieg über `Rechtliches` nutzen und den Eintrag `Impressum` an die echte Impressumsansicht anbinden.
- Das vom Server gelieferte Impressum datengetrieben rendern.
- Die feste Blockreihenfolge aus der Hauptaufgabe einhalten:
  1. `provider`
  2. `address`
  3. `contact`
  4. `register`
  5. `tax`
  6. `supervision`
  7. `editorial`
  8. `additional`
- Blöcke nur rendern, wenn sie relevante Angaben enthalten.
- `null`-Angaben und fehlende optionale Angaben beim Rendering weglassen.
- `additional.markdown` als letzten Block rendern.
- Die Darstellung für Desktop und Mobilgeräte brauchbar halten.

Vorgehen:

- Die Client-Logik und das Rendering so weit wie sinnvoll automatisiert absichern.
- Mindestens einen Test für die Darstellung vorhandener Blöcke und einen Test für das Weglassen leerer optionaler Angaben anlegen.

Voraussetzung:

- `sub002-ExposeImprintToClient.md`

Nicht Teil dieser Subtask:

- Erweiterte rechtliche Länderlogik
- Übersetzung des Impressums
