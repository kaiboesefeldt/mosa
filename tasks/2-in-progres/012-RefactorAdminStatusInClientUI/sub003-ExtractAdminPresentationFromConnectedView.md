Ziel

Die Admin-Praesentationslogik soll aus `IdentityConnectedView.kt` herausgeloest und an genau einer gut auffindbaren Stelle zusammengefuehrt werden.

Scope

- Vor dem Herausloesen der Admin-Praesentationslogik die bisher sichtbaren fachlichen Badge-Entscheidungen ueber Tests des aktuellen Verhaltens oder ueber Teilintegrationen absichern.
- Die Logik fuer Header-Badge und Profil-Badge in eine kleine dedizierte Logikkomponente oder einen klar abgegrenzten Hook auslagern.
- Dort explizit zwischen Admin-Status der verbundenen Identitaet fuer die Hauptseite und Admin-Status der aktuell angezeigten Identitaet fuer die Profilansicht unterscheiden.
- `IdentityConnectedView.kt` auf Rendering und Verdrahtung von UI-Ereignissen reduzieren.
- Die wichtigsten UI-nahen Zustandsfaelle ueber passende Tests der nicht-UI-Logik absichern.

Nicht Teil dieser Subtask

- Neuer fachlicher Rechteumfang.
- Sichtbare Designaenderungen.
- Grossflaechiger Umbau anderer Bereiche der Connected-View.

Akzeptanzkriterien

- Das Header-Badge leitet sich nur aus dem Admin-Status der verbundenen Identitaet ab.
- Das Badge in der Profilansicht leitet sich nur aus dem Admin-Status der aktuell angezeigten Identitaet ab.
- Die Entscheidung ueber beide Badges ist in einer kleinen, klar benannten Einheit zusammengefasst.
- `IdentityConnectedView.kt` enthaelt keine verstreute Admin-Praesentationslogik mehr.
- Die Badge-Entscheidung ist ueber eine explizite Szenario-Matrix automatisiert abgesichert, nicht nur ueber wenige Beispieltests.
- Vor dem eigentlichen Herausloesen ist das bisher fachlich sichtbare Badge-Verhalten bereits automatisiert abgesichert.
