Diese Subtask entfernt das Ablaufdatum aus dem Freeze-Snapshot-Modell und aus der Restore-Validierung.

Ziel ist, dass Freeze-Snapshots fachlich und technisch nicht mehr an eine zeitbasierte Restore-Grenze gebunden sind.

Umfang:

- `FreezeSnapshotHeader` enthaelt kein Ablaufdatum mehr.
- Das serialisierte Snapshot-Format enthaelt kein Ablaufdatum mehr.
- Snapshot-Erzeugung schreibt kein Ablaufdatum mehr.
- Snapshot-Restore lehnt Snapshots nicht mehr wegen Zeitablaufs ab.
- Bestehende Snapshot-Tests werden so angepasst, dass das Entfernen des Ablaufdatums klar abgesichert ist.

Wichtige Randbedingungen:

- Es wird keine Rueckwaertskompatibilitaet fuer bereits erzeugte Snapshot-Dateien eingebaut.
- Es wird kein Workaround mit einer nur sehr grossen, aber endlichen Laufzeit eingefuehrt.
- Business-Logik-Aenderungen werden testgetrieben in kleinen Schritten umgesetzt.

Erfolgskriterien:

- Ein neu erzeugter Snapshot enthaelt kein `expiresAt` mehr.
- Restore-Pruefungen enthalten keine Zeitablauf-Ablehnung mehr.
- Die relevanten Unit-Tests dokumentieren das neue Verhalten explizit.
