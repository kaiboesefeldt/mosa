Beim Loeschen eines User-Profils muessen auch die dazugehoerigen Beschwerden und Admin-Nachrichten entfernt werden.

Aktuell ist `Nutzer loeschen` als Moderationsaktion noch nicht fachlich bis zu allen verknuepften Moderationsdaten ausformuliert. Dadurch besteht das Risiko, dass nach einer Profilloeschung verwaiste Beschwerden oder Admin-Nachrichten zurueckbleiben, die weiterhin auf ein nicht mehr existentes Profil oder dessen Inhalte verweisen.

Ziel ist eine klare, konsistente Loesch-Semantik fuer profilbezogene Moderationsdaten. Wenn ein Profil geloescht wird, sollen alle Beschwerden entfernt werden, die dieses Profil betreffen oder von ihm stammen, und ebenso alle Admin-Nachrichten, die diesem Profil zugeordnet sind.

Die Aufgabe umfasst mindestens diese Punkte:

- Die fachliche Bedeutung von `Nutzer loeschen` wird fuer Beschwerden und Admin-Nachrichten eindeutig festgelegt.
- Beim Loeschen eines User-Profils werden alle dazugehoerigen Beschwerden entfernt.
- Beim Loeschen eines User-Profils werden alle dazugehoerigen Admin-Nachrichten entfernt.
- Nach der Loeschung bleiben keine ueber die relevanten Read-Endpoints sichtbaren verwaisten Moderationsdaten fuer das geloeschte Profil bestehen.
- Die Loeschlogik entfernt nur die Daten des betroffenen Profils und greift nicht auf unbeteiligte Profile ueber.

Akzeptanzkriterien:

- Eine automatisierte fachliche Verifikation zeigt, dass nach `Nutzer loeschen` keine verknuepften Beschwerden des Profils mehr sichtbar sind.
- Eine automatisierte fachliche Verifikation zeigt, dass nach `Nutzer loeschen` keine verknuepften Admin-Nachrichten des Profils mehr sichtbar sind.
- Nicht verknuepfte Beschwerden und Admin-Nachrichten anderer Profile bleiben unveraendert erhalten.

Bei der Umsetzung ist insbesondere zu klaeren:

- Nach welchem Profilbezug Beschwerden genau mitgeloescht werden muessen, insbesondere bei Beschwerden ueber Inhalte des Profils gegenueber Beschwerden, die das Profil selbst eingereicht hat.
- Ob die Loeschung in allen betroffenen Speichern atomar genug erfolgen muss oder ob eine definierte Reihenfolge mit sauberer Fehlerbehandlung ausreicht.
- Wie sich diese Aufgabe zu [029-AdminUserModerationActions](/home/kai/Projects/straightway/mosa/tasks/1-open/029-AdminUserModerationActions/Description.md) verhaelt und ob `Nutzer loeschen` dort danach auf diese Semantik verweisen sollte.
