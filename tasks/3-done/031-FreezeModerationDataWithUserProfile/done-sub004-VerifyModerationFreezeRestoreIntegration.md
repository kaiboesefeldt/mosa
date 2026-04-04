Dies ist die verpflichtende Abschluss-Subtask fuer die Gesamtintegration der Aenderung.

Ziel ist, die geaenderte Gesamtloesung ueber die wichtigsten funktionalen Pfade hinweg zu pruefen.

Betroffene Komponenten:

- [ModerationSnapshotContract.md](/home/kai/Projects/straightway/mosa/tasks/2-in-progres/031-FreezeModerationDataWithUserProfile/ModerationSnapshotContract.md)
- Snapshot-Selektion und Snapshot-Format
- Persistenz fuer Beschwerden und Admin-Nachrichten
- Lesepfade fuer wiederhergestellte Admin-Nachrichten und Beschwerden
- bestehende Freeze- und Restore-Endpunkte

Vorbedingungen:

- Die Subtasks fuer Vertrag, Freeze-Auswahl und Restore sind umgesetzt.
- Testhilfen koennen Beitraege, Beschwerden und Admin-Nachrichten fuer mehrere Profile deterministisch vorbereiten.

Ausgefuehrte Pruefungen:

1. Ein Freeze eines Profils enthaelt dessen eigene Beschwerden.
2. Ein Freeze eines Profils enthaelt Beschwerden ueber Inhalte dieses Profils.
3. Ein Freeze eines Profils enthaelt Admin-Nachrichten an dieses Profil.
4. Ein Freeze eines Profils enthaelt Admin-Nachrichten von diesem Profil.
5. Ein Freeze eines anderen Profils enthaelt keine fachlich fremden Moderationsdaten.
6. Ein Restore spielt die eingefrorenen Beschwerden und Admin-Nachrichten wieder ein.
7. Wiederhergestellte Admin-Nachrichten sind nach dem Restore als ungelesen sichtbar.
8. Wiederhergestellte Beschwerden sind nach dem Restore als ungelesen sichtbar.
9. Die Gesamtloesung besteht `./gradlew check`.
10. Die Dokumentation in `spec`, `server/src/main/resources/clearterms` und `server/src/main/resources/doc/serve` wurde geprueft; die betroffenen Snapshot-Hinweise in ClearTerms und Server-Doku wurden angepasst.
11. Es wurden keine neuen 3rd-Party-Komponenten eingefuehrt; damit war keine zusaetzliche Lizenzpruefung noetig.

Erfolgskriterium:

- Die wesentlichen Freeze-, Restore-, Abgrenzungs- und Ungelesen-Pfade sind automatisch abgesichert, notwendige Dokumentation ist geprueft, und der komplette Quality Gate ist gruen.
