Mosa soll einen einfachen pod-lokalen Nachrichtenkanal zwischen Admin-Konten und normalen Nutzerkonten bekommen.

Im ersten Wurf ist der Kanal bewusst einseitig: Admins koennen einzelnen Nutzerkonten Nachrichten schicken, und die betroffenen Nutzer koennen diese Nachrichten spaeter abrufen. Es gibt keine Antwortfunktion, keine Lesebestaetigung und keine komplexen Zustaende. Der Schwerpunkt liegt auf einer kleinen, klaren Inbox-Funktion mit kurzer Datenlebensdauer.

Die Aufgabe umfasst mindestens diese Punkte:

- Admin-Konten muessen Nachrichten gezielt an genau ein Nutzerkonto senden koennen.
- Normale Nutzerkonten muessen ihre eigenen Nachrichten authentifiziert abrufen koennen.
- Nachrichten duerfen nicht ueber das bestehende oeffentliche Content-, Query- oder Suchmodell realisiert werden, sondern brauchen einen getrennten privaten Kanal.
- Nachrichten sollen unveraenderlich sein; nachtraegliche Bearbeitung ist im ersten Wurf nicht vorgesehen.
- Es soll serverseitig abgesichert sein, dass nur Admin-Konten Nachrichten erzeugen duerfen und dass ein Nutzer nur seine eigenen Nachrichten lesen kann.
- Der Client soll eine einfache Benachrichtigungs- oder Inbox-Funktion erhalten, ueber die neue Nachrichten sichtbar und abrufbar werden.
- Admin-Konten sollen im bestehenden Nachrichten- oder Beitragskontext eine minimale Sende-UI bekommen: Klick auf das Avatar-Bild eines Nutzerbeitrags oeffnet einen kleinen Compose-Popover fuer eine Admin-Nachricht an genau dieses Konto.

Lifecycle und Aufbewahrung:

- Jede Nachricht bekommt eine TTL fuer den Fall, dass sie nie abgerufen wird, zum Beispiel sechs Monate ab Erstellung.
- Beim erfolgreichen Abruf durch das Empfaengerkonto wird die verbleibende Aufbewahrung auf hoechstens eine kuerzere TTL begrenzt, zum Beispiel vier Wochen ab Abruf.
- Die Nachricht soll nach dem ersten Abruf noch fuer eine gewisse Zeit erhalten bleiben, damit sie auch von anderen Geraeten desselben Kontos abgerufen werden kann.
- Der Abruf muss serverseitig das effektive Ablaufdatum anpassen koennen, ohne dafuer unnoetige zusaetzliche personenbezogene Lifecycle-Daten zu speichern.
- Nach Ablauf der jeweils relevanten TTL muss die Nachricht serverseitig entfernt werden.
- Die Bereinigung abgelaufener Nachrichten darf nicht nur kosmetisch im Client passieren.

Akzeptanzkriterien:

- Ein Admin kann eine Nachricht fuer ein bestimmtes Nutzerkonto erstellen.
- Ein Admin kann aus dem bestehenden Nachrichten- oder Beitragskontext heraus ueber das Avatar eines Nutzers den Compose-Popover oeffnen und eine Nachricht an genau diesen Nutzer absenden.
- Das Empfaengerkonto kann diese Nachricht ueber einen authentifizierten Abruf sehen.
- Andere Nutzerkonten duerfen diese Nachricht nicht lesen.
- Nicht-Admin-Konten duerfen keine Admin-Nachrichten erzeugen.
- Eine nie abgerufene Nachricht verschwindet spaetestens nach der langen TTL.
- Eine bereits einmal abgerufene Nachricht verschwindet spaetestens nach der kuerzeren TTL ab erstem Abruf.
- Mehrere Geraete desselben Kontos koennen eine bereits abgerufene Nachricht innerhalb der kuerzeren TTL weiterhin abrufen.
- Bereits abgelaufene Nachrichten werden bei Abruf nicht mehr ausgeliefert.

Bei der Umsetzung ist insbesondere zu klaeren:

- Welche minimalen Felder eine Nachricht im ersten Schritt braucht, zum Beispiel Empfaenger-GID, Sender-GID, Zeitpunkt, Betreff, Text und effektives Ablaufdatum.
- Wie das effektive Ablaufdatum beim Abruf atomar verkuerzt wird, damit bei parallelem Zugriff mehrerer Geraete keine inkonsistenten TTL-Zustaende entstehen.
- Wie abgelaufene Nachrichten sowohl request-nah als auch ueber einen serverseitigen Background-Cleanup entfernt werden.
- Wie die private Speicherung technisch getrennt vom bestehenden Content- und Suchspeicher in einer einfachen JSON-Datei realisiert wird.
- Wie der Client neue Nachrichten sichtbar macht, ohne daraus schon einen komplexen Messaging- oder Moderations-Workflow zu machen.
- Wie der Admin-Compose-Popover am Avatar im bestehenden UI so angebunden wird, dass er fuer Admins direkt erreichbar ist, fuer normale Nutzer aber nicht stoert.
