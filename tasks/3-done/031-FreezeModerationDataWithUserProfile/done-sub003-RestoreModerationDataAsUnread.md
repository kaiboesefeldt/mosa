Ziel ist, den Restore so zu erweitern, dass eingefrorene Moderationsdaten wieder eingespielt und danach ueber die bestehenden Lesepfade als ungelesen behandelt werden.

Verbindliche Grundlage:

- [ModerationSnapshotContract.md](/home/kai/Projects/straightway/mosa/tasks/2-in-progres/031-FreezeModerationDataWithUserProfile/ModerationSnapshotContract.md)

Diese Subtask umfasst mindestens:

- einen minimalen roten Test fuer das Restore von Beschwerden und Admin-Nachrichten aus einem Snapshot
- einen minimalen roten Test dafuer, dass wieder eingespielte Admin-Nachrichten nach dem Restore als ungelesen sichtbar sind
- einen minimalen roten Test dafuer, dass wieder eingespielte Beschwerden nach dem Restore als ungelesen sichtbar sind
- die Restore-Logik so erweitern, dass snapshotte Beschwerden und Admin-Nachrichten wieder in die betroffenen Speicher eingespielt werden
- noetige Schema- oder Persistenzanpassungen fuer eine effiziente Restore-Logik und eine klare Ungelesen-Semantik umsetzen, falls der zentrale Vertrag das verlangt
- die bestehenden Lese- und Verarbeitungsfluesse so anpassen, dass die wiederhergestellten Entitaeten fachlich konsistent als ungelesen erscheinen
- Konfliktfaelle gemaess dem zentralen Vertrag behandeln

Erwartetes Ergebnis:

- Ein Restore eines Profils spielt alle eingefrorenen Beschwerden und Admin-Nachrichten wieder ein
- Wiederhergestellte Moderationsdaten erscheinen ueber die relevanten Pfade als ungelesen
