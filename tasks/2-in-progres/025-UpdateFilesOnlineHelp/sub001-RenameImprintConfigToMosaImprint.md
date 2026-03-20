Die Imprint-Konfiguration soll technisch von `imprint.yaml` auf `mosa_imprint.yaml` umgestellt werden.

Ziel ist eine konsistente Benennung aller dateibasierten Top-Level-Artefakte des MOSA-Servers im aktuellen Arbeitsverzeichnis. Die bisherige Sonderbenennung der Imprint-Datei soll entfallen.

Diese Subtask umfasst mindestens:

- Alle produktiven Codepfade identifizieren, die aktuell `imprint.yaml` erwarten oder dokumentieren.
- Die Imprint-Konfiguration im Server auf `mosa_imprint.yaml` umstellen.
- Veraltete Referenzen auf `imprint.yaml` im betroffenen Code und in direkt betroffenen Dokumentationen entfernen oder anpassen.
- Keine parallele Unterstuetzung des alten Dateinamens vorsehen.

Akzeptanzkriterien:

- Der Server erwartet fuer die Imprint-Konfiguration nur noch `mosa_imprint.yaml`.
- Direkte Referenzen im betroffenen Code verwenden konsistent den neuen Dateinamen.
- Es gibt keine absichtliche Rueckfalllogik auf `imprint.yaml`.

Hinweise zur Umsetzung:

- Die Aenderung betrifft fachliches Produktionsverhalten und ist deshalb gemaess AGENTS per Test-first zu schneiden.
- Dokumentationsanpassungen ausserhalb der unmittelbaren Umbenennungsfolgen sind nicht Teil dieser Subtask.
