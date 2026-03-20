Dies ist die verpflichtende Abschluss-Subtask fuer die Gesamtintegration der Aenderung.

Ziel ist, die geaenderte Gesamtloesung fuer dauerhaft wiederherstellbare Freeze-Snapshots ueber die wichtigsten funktionalen Pfade hinweg zu pruefen.

Betroffene Komponenten:

- Snapshot-Header und Snapshot-Format
- Snapshot-Erzeugung und Snapshot-Restore
- Restore-Validierung ohne Zeitablauf-Pruefung
- file-basierter Archivschluessel-Store mit aktivem und historischen Schluesseln
- rechtliche Snapshot-Texte
- technische Dateidokumentation in `server/src/main/resources/doc/serve/files.md`

Vorbedingungen:

- `sub001-RemoveSnapshotExpiryFromModelAndRestore.md` ist abgeschlossen.
- `sub002-IntroduceMultiKeyArchiveStoreFormat.md` ist abgeschlossen.
- `sub003-AdaptSnapshotServicesAndUserFacingTexts.md` ist abgeschlossen.

Auszufuehrende Pruefungen:

1. Ein neu erzeugter Snapshot enthaelt kein Ablaufdatum im Header oder im serialisierten Format.
2. Ein Snapshot-Restore wird nicht mehr wegen eines Zeitablaufs abgelehnt.
3. Ein Snapshot kann mit einem historischen, nicht mehr aktiven Archivschluessel per `keyId` entschluesselt werden.
4. Der aktive Archivschluessel bleibt getrennt von der historischen Aufloesung modelliert.
5. Das neue Archivschluessel-Dateiformat kann mehrere Schluessel aufnehmen und wird durch Tests oder dokumentierte Integrationspruefungen abgesichert.
6. Nutzertexte und rechtliche Dokumente enthalten keine Aussage mehr, dass Snapshots nur bis zu einem Ablaufdatum wiederherstellbar sind.
7. `server/src/main/resources/doc/serve/files.md` beschreibt die geaenderte Snapshot-Datei- und Schluesselablage korrekt.
8. `./gradlew check` laeuft erfolgreich.

Erfolgskriterium:

- Die wesentlichen Modell-, Restore-, Schluessel- und Dokumentationspfade der Freeze-Snapshot-Umstellung sind gemeinsam abgesichert und der komplette Quality Gate ist gruen.
