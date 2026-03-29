Ziel ist, den bestehenden Profil-Dialog so zu erweitern, dass `Identitaet loeschen` die pod-seitige Selbstloeschung ausloest und erst danach lokal abschliesst.

Diese Subtask umfasst mindestens:

- einen minimalen failing Test fuer den geaenderten Client-Flow von `Identitaet loeschen` schreiben
- den Profil-Dialog beziehungsweise den bestehenden Client-Flow an den neuen pod-seitigen Loeschpfad anbinden
- den Nutzer bei Fehlschlaegen in einer klaren, konsistenten Form im bestehenden UI-Rahmen informieren
- sicherstellen, dass die lokale Identitaet nicht stillschweigend entfernt wird, wenn die pod-seitige Loeschung nicht erfolgreich war
- den erfolgreichen Abschluss so integrieren, dass danach kein veralteter lokaler oder verbundener Zustand zurueckbleibt

Wichtig:

- Die bestehende Bedienlogik des Profil-Dialogs soll nur minimal erweitert werden.
- UI-spezifische Aenderungen muessen nur kompilieren, die Fachlogik des Flows ist jedoch ueber automatisierte Tests abzusichern.
