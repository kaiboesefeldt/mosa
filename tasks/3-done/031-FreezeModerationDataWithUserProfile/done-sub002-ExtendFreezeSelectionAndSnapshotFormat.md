Ziel ist, den bestehenden Freeze technisch so zu erweitern, dass alle profilbezogenen Moderationsdaten zusammen mit dem Inhalts-Snapshot ausgewaehlt und serialisiert werden koennen.

Verbindliche Grundlage:

- [ModerationSnapshotContract.md](/home/kai/Projects/straightway/mosa/tasks/2-in-progres/031-FreezeModerationDataWithUserProfile/ModerationSnapshotContract.md)

Diese Subtask umfasst mindestens:

- einen minimalen roten Test fuer einen Freeze, der Beschwerden in beiden fachlichen Richtungen des Profils mit aufnimmt
- einen minimalen roten Test fuer einen Freeze, der Admin-Nachrichten in beiden fachlichen Richtungen des Profils mit aufnimmt
- die bestehende Snapshot-Selektion so erweitern, dass neben Beitraegen und Antworten auch Moderationsdaten des Profils ermittelt werden
- das Snapshot-Format und seine Datentypen so erweitern, dass Beschwerden und Admin-Nachrichten zusammen mit den bestehenden Inhaltsdaten serialisiert werden koennen
- noetige Schema- oder Persistenzanpassungen fuer eine effiziente Snapshot-Erzeugung umsetzen, falls der zentrale Vertrag das verlangt
- sicherstellen, dass fachlich fremde Moderationsdaten anderer Profile nicht in den Freeze geraten

Erwartetes Ergebnis:

- Ein Freeze eines Profils erzeugt einen Snapshot, der Inhalte, Beschwerden und Admin-Nachrichten dieses Profils vollstaendig und trennscharf enthaelt
- Die neue Auswahl- und Serialisierungslogik ist durch fokussierte automatisierte Tests abgesichert
