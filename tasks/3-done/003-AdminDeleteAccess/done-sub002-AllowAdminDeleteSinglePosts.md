Ziel

Admin-Konten sollen ueber den bestehenden Einzel-Delete fremde Beitraege loeschen koennen, ohne die bisherigen Rechte normaler Konten zu erweitern.

Scope

- Die bestehende Loeschpruefung fuer `DELETE /v1/posts/{id}` um Admin-Rechte erweitern.
- Normale Konten duerfen weiterhin nur eigene Beitraege loeschen.
- Admin-Rechte gelten nur fuer Einzel-Loeschungen fremder Beitraege.
- Bestehende Bulk-Loeschungen fuer eigene Inhalte bleiben unveraendert und werden nicht auf fremde Inhalte erweitert.

Vorgehen

- Die Berechtigungslogik zuerst mit einem minimalen fehlenden Test ueber oeffentliches Verhalten reproduzieren.
- Die fachliche Pruefung so schneiden, dass sie zwischen Autor, Admin und Nichtberechtigtem klar unterscheidet.
- Anschliessend den minimalen Produktionscode ergaenzen, damit der neue Admin-Fall gruene Tests bekommt.
- Danach vorhandene Tests fuer bisherige Eigenloeschungen gegen moegliche Regressionen absichern.

Akzeptanzkriterien

- Der Autor eines Beitrags kann den eigenen Beitrag weiterhin loeschen.
- Ein Admin kann einen fremden Beitrag ueber den bestehenden Einzel-Delete loeschen.
- Ein Nicht-Admin kann keinen fremden Beitrag loeschen.
- Die Antwort fuer nicht erlaubte Fremd-Loeschungen bleibt fachlich konsistent zum bisherigen Verhalten.
- Bulk-Delete fuer fremde Inhalte wird nicht eingefuehrt.

Nicht Teil dieser Subtask

- Neue Admin-UI
- Beschwerden oder Moderations-Workflows
- Zusaetzliche Audit- oder Moderationsprotokolle
