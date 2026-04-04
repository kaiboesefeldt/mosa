Beim Freeze eines User-Profils muessen auch saemtliche dazugehoerigen Moderationsdaten mit eingefroren werden.

Aktuell beschreibt der Freeze-Mechanismus fuer Profile noch nicht explizit, wie mit zugeordneten Moderationsdaten umzugehen ist. Ohne diese Festlegung droht ein unvollstaendiger Snapshot, bei dem ein Profilstand eingefroren wird, aber seine Beschwerden oder Admin-Nachrichten spaeter nicht mehr konsistent dazu rekonstruiert werden koennen.

Ziel ist ein vollstaendiger Freeze fuer ein Nutzerprofil mitsamt aller fachlich zugehoerigen Moderationsdaten. Wenn ein User-Profil eingefroren wird, sollen alle zugeordneten Beschwerden und Admin-Nachrichten im Freeze mit enthalten sein, damit der eingefrorene Zustand in sich konsistent bleibt und spaeter wieder als geschlossener Gesamtzustand eingespielt werden kann.

Fachlicher Umfang des Freeze:

- alle Beitraege und Antworten des Profils, die bereits Teil des bestehenden Freeze sind
- alle Beschwerden, die von diesem Profil eingereicht wurden
- alle Beschwerden ueber Beitraege oder Antworten, die dieses Profil erstellt hat
- alle Admin-Nachrichten, die diesem Profil als Empfaenger zugeordnet sind
- alle Admin-Nachrichten, die dieses Profil als Sender zugeordnet sind

Die Aufgabe umfasst mindestens diese Punkte:

- Die Freeze-Semantik fuer profilbezogene Beschwerden wird eindeutig auf beide fachlich relevanten Richtungen festgelegt: vom Profil stammende Beschwerden und Beschwerden ueber Inhalte des Profils.
- Die Freeze-Semantik fuer profilbezogene Admin-Nachrichten wird eindeutig auf beide Richtungen festgelegt: Nachrichten an das Profil und Nachrichten vom Profil.
- Ein Freeze eines User-Profils enthaelt alle verknuepften Beschwerden gemaess dieser Semantik.
- Ein Freeze eines User-Profils enthaelt alle verknuepften Admin-Nachrichten gemaess dieser Semantik.
- Ein Restore eines eingefrorenen User-Profils spielt diese Beschwerden und Admin-Nachrichten wieder ein.
- Nach dem Restore gelten alle eingespielten Admin-Nachrichten wieder als ungelesen.
- Nach dem Restore gelten alle eingespielten Beschwerden wieder als ungelesen.
- Die Wiederherstellung oder Nutzung eines eingefrorenen Zustands arbeitet mit diesen Moderationsdaten konsistent weiter.

Akzeptanzkriterien:

- Eine automatisierte fachliche Verifikation zeigt, dass ein Freeze eines Profils alle von diesem Profil eingereichten Beschwerden enthaelt.
- Eine automatisierte fachliche Verifikation zeigt, dass ein Freeze eines Profils alle Beschwerden ueber Beitraege oder Antworten dieses Profils enthaelt.
- Eine automatisierte fachliche Verifikation zeigt, dass ein Freeze eines Profils alle Admin-Nachrichten an dieses Profil enthaelt.
- Eine automatisierte fachliche Verifikation zeigt, dass ein Freeze eines Profils alle Admin-Nachrichten von diesem Profil enthaelt.
- Eine automatisierte fachliche Verifikation zeigt, dass ein Restore diese Beschwerden und Admin-Nachrichten wieder einspielt.
- Eine automatisierte fachliche Verifikation zeigt, dass eingespielte Admin-Nachrichten nach dem Restore als ungelesen sichtbar sind.
- Eine automatisierte fachliche Verifikation zeigt, dass eingespielte Beschwerden nach dem Restore als ungelesen sichtbar sind.
- Ein Freeze eines anderen Profils enthaelt keine fachlich fremden Moderationsdaten.

Bei der Umsetzung ist insbesondere zu klaeren:

- Wie Beschwerden und Admin-Nachrichten im Snapshot-Format repraesentiert werden, obwohl der bestehende Freeze aktuell inhaltszentriert aufgebaut ist.
- Wie das Restore Konflikte behandelt, wenn zu importierenden IDs bereits Daten auf dem Pod gegenueberstehen.
- Wie sich die Freeze-Erweiterung auf die bestehenden Services in `server/src/main/kotlin/straightway/mosa/content/snapshot` und die zugehoerigen Moderationsspeicher auswirkt.
- Es ist fachlich und technisch zulaessig, das Datenschema der betroffenen Moderations- und Snapshot-Speicher anzupassen, wenn dies fuer einen effizienten, konsistenten Freeze und Restore des Gesamtzustands des Profils erforderlich ist.
