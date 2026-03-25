Beim Freeze eines User-Profils muessen auch die dazugehoerigen Beschwerden und Admin-Nachrichten mit eingefroren werden.

Aktuell beschreibt der Freeze-Mechanismus fuer Profile noch nicht explizit, wie mit zugeordneten Moderationsdaten umzugehen ist. Ohne diese Festlegung droht ein unvollstaendiger Snapshot, bei dem ein Profilstand eingefroren wird, aber seine Beschwerden oder Admin-Nachrichten spaeter nicht mehr konsistent dazu rekonstruiert werden koennen.

Ziel ist ein vollstaendiger Freeze fuer profilbezogene Moderationsdaten. Wenn ein User-Profil eingefroren wird, sollen alle zugeordneten Beschwerden und Admin-Nachrichten im Freeze mit enthalten sein, damit der eingefrorene Zustand in sich konsistent bleibt.

Die Aufgabe umfasst mindestens diese Punkte:

- Die Freeze-Semantik fuer profilbezogene Beschwerden wird fachlich festgelegt.
- Die Freeze-Semantik fuer profilbezogene Admin-Nachrichten wird fachlich festgelegt.
- Ein Freeze eines User-Profils enthaelt alle verknuepften Beschwerden.
- Ein Freeze eines User-Profils enthaelt alle verknuepften Admin-Nachrichten.
- Die Wiederherstellung oder Nutzung eines eingefrorenen Zustands arbeitet mit diesen Moderationsdaten konsistent weiter.

Akzeptanzkriterien:

- Eine automatisierte fachliche Verifikation zeigt, dass ein Freeze eines Profils auch die verknuepften Beschwerden enthaelt.
- Eine automatisierte fachliche Verifikation zeigt, dass ein Freeze eines Profils auch die verknuepften Admin-Nachrichten enthaelt.
- Ein Freeze eines anderen Profils enthaelt keine fachlich fremden Moderationsdaten.

Bei der Umsetzung ist insbesondere zu klaeren:

- Nach welchem Profilbezug Beschwerden und Admin-Nachrichten in den Freeze aufgenommen werden muessen.
- Ob der Freeze physische Kopien, referenzielle Snapshots oder eine andere bestehende Snapshot-Form nutzt.
- Wie sich die Freeze-Erweiterung auf die bestehenden Services in `server/src/main/kotlin/straightway/mosa/content/snapshot` auswirkt.
