Beim Loeschen einer Identitaet im Profil-Dialog muss dieselbe Identitaet nicht nur lokal entfernt, sondern auch auf dem aktuell verbundenen Pod geloescht werden. Dabei muessen auch alle dazugehoerigen Beitraege, Beschwerden und Admin-Nachrichten entfernt werden.

Aktuell entfernt `Identitaet loeschen` im Client nur die lokale Identitaet aus der Auswahl. Fuer die fachliche Pod-Seite ist noch nicht eindeutig festgelegt, welche serverseitigen Daten dabei mit verschwinden muessen. Dadurch besteht das Risiko, dass nach einer lokalen Identitaetsloeschung die Identitaet auf dem Pod faktisch weiterlebt oder verwaiste Moderationsdaten zurueckbleiben, die weiterhin auf ein nicht mehr erreichbares Profil oder dessen Inhalte verweisen.

Ziel ist eine klare, konsistente Loesch-Semantik fuer die Selbstloeschung einer Identitaet. Wenn ein Nutzer seine Identitaet im Profil-Dialog loescht, soll dieselbe Identitaet auf dem aktuell verbundenen Pod mitsamt ihrer pod-lokalen Daten entfernt werden.

Die Pod-Loeschung dieser Identitaet umfasst fachlich:

- alle Beitraege und Antworten der Identitaet auf diesem Pod
- alle Beschwerden, die von dieser Identitaet eingereicht wurden
- alle Beschwerden ueber Beitraege dieser Identitaet
- alle Admin-Nachrichten, die dieser Identitaet zugeordnet sind

Die Aufgabe umfasst mindestens diese Punkte:

- Die bestehende Aktion `Identitaet loeschen` im Profil-Dialog wird fachlich von einer rein lokalen Entfernung zu einer lokalen plus pod-seitigen Loeschung derselben Identitaet erweitert.
- Die serverseitige Bedeutung dieser Identitaetsloeschung wird fuer Beitraege, Beschwerden und Admin-Nachrichten eindeutig festgelegt.
- Beim Loeschen einer Identitaet auf dem Pod werden alle Beitraege und Antworten dieser Identitaet entfernt.
- Beim Loeschen einer Identitaet auf dem Pod werden alle dazugehoerigen Beschwerden entfernt, sowohl vom Profil stammende Beschwerden als auch Beschwerden ueber Inhalte dieses Profils.
- Beim Loeschen einer Identitaet auf dem Pod werden alle dazugehoerigen Admin-Nachrichten entfernt.
- Nach der Loeschung bleiben ueber die relevanten Read-Endpoints keine verwaisten pod-lokalen Moderationsdaten fuer die geloeschte Identitaet sichtbar.
- Die Loeschlogik entfernt nur pod-lokale Daten der betroffenen Identitaet und greift nicht auf unbeteiligte Profile ueber.
- Fuer Fehlerfaelle wird ein klarer Ablauf definiert, insbesondere wenn die pod-seitige Loeschung fehlschlaegt, die lokale Identitaet aber bereits entfernt werden sollte oder noch nicht entfernt werden darf.

Akzeptanzkriterien:

- Eine automatisierte fachliche Verifikation zeigt, dass `Identitaet loeschen` aus dem Profil-Dialog die betroffene Identitaet nicht nur lokal entfernt, sondern auch die pod-seitige Loeschung anstoesst.
- Eine automatisierte fachliche Verifikation zeigt, dass nach der Identitaetsloeschung auf dem Pod keine Beitraege und Antworten dieser Identitaet mehr sichtbar sind.
- Eine automatisierte fachliche Verifikation zeigt, dass nach der Identitaetsloeschung auf dem Pod keine verknuepften Beschwerden dieser Identitaet mehr sichtbar sind.
- Eine automatisierte fachliche Verifikation zeigt, dass nach der Identitaetsloeschung auf dem Pod keine verknuepften Admin-Nachrichten dieser Identitaet mehr sichtbar sind.
- Nicht verknuepfte Beitraege, Beschwerden und Admin-Nachrichten anderer Profile bleiben unveraendert erhalten.
- Die Verifikation deckt auch den Fall ab, dass sowohl eigene Beschwerden der Identitaet als auch Beschwerden ueber ihre Inhalte vorhanden sind.

Bei der Umsetzung ist insbesondere zu klaeren:

- Ob die pod-seitige Loeschung nur bei bestehender aktiver Pod-Verbindung angeboten oder andernfalls gesperrt beziehungsweise anders kommuniziert wird.
- In welcher Reihenfolge lokale und pod-seitige Loeschung ablaufen sollen, damit kein inkonsistenter Zustand fuer den Nutzer entsteht.
- Ob die Loeschung ueber alle betroffenen Datentypen physisch hart erfolgt oder ob fuer einzelne Read-Pfade eine fachlich gleichwertige Unsichtbarkeit ausreicht.
- Ob geschlossene und historische Beschwerden derselben Identitaet ebenfalls entfernt werden muessen oder nur heute sichtbare Eintraege.
- Wie sich diese Aufgabe zu [029-AdminUserModerationActions](/home/kai/Projects/straightway/mosa/tasks/1-open/029-AdminUserModerationActions/Description.md) verhaelt und ob die dortige Admin-Aktion `Nutzer loeschen` danach ausdruecklich auf dieselbe serverseitige Loesch-Semantik verweisen soll.
