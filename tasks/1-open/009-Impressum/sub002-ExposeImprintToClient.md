Ziel dieser Subtask ist, die geladene Impressumskonfiguration für den Client abrufbar zu machen.

Umfang:

- Einen serverseitigen Auslieferungspfad für das Impressum einführen.
- Ein transportgeeignetes DTO definieren, das die festen semantischen Blöcke aus `imprint.yaml` abbildet.
- Sicherstellen, dass optionale oder `null`-Angaben transportiert werden können, ohne dass die Struktur unklar wird.
- Den neue Auslieferungspfad in bestehende Legal- oder Client-Zugriffspfade einordnen.

Vorgehen:

- Die Endpunkt- oder Service-Semantik über automatisierte Tests absichern.
- Mindestens einen Test für erfolgreich ausgelieferte Daten anlegen.

Voraussetzung:

- `sub001-LoadAndValidateImprintConfiguration.md`

Nicht Teil dieser Subtask:

- HTML- oder Markdown-Rendering im Client
- Sichtbare UI-Darstellung
