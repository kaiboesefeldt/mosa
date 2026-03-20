Diese Subtask verbindet die fachliche Snapshot-Umstellung mit den verbleibenden Integrationsstellen in Server, UI-Texten und Dokumentation.

Ziel ist, dass Snapshot-Erzeugung und Snapshot-Restore das neue dauerhafte Verhalten mit historischem Schluesselbestand konsistent nutzen und dass keine Nutzerkommunikation mehr ein Ablaufdatum behauptet.

Umfang:

- Snapshot-Service und Restore-Service verwenden den neuen Header und den Mehrschluessel-Store konsistent.
- Snapshots bleiben auch nach kuenftiger Schluesselrotation wiederherstellbar, solange der passende historische Schluessel im Store vorhanden ist.
- Nutzertexte und rechtliche Dokumente beschreiben korrekt, dass die Restore-Grenze nicht zeitbasiert ist.
- `server/src/main/resources/doc/serve/files.md` beschreibt die geaenderte Snapshot-bezogene Dateistruktur und Schluesselablage korrekt.

Wichtige Randbedingungen:

- UI-spezifische Aenderungen muessen nur kompilieren; dedizierte UI-Tests sind nicht erforderlich.
- Fachliche Server-Logik und schluesselbezogene Aufloesung werden weiterhin testgetrieben umgesetzt.

Erfolgskriterien:

- Ein Snapshot kann mit einem nicht mehr aktiven, aber noch gespeicherten Archivschluessel erfolgreich wiederhergestellt werden.
- Es bleiben keine Nutzer- oder Rechtstexte uebrig, die eine Wiederherstellung nur bis zu einem Ablaufdatum beschreiben.
- `files.md` dokumentiert das neue Snapshot-bezogene Dateiformat verstaendlich und korrekt.
