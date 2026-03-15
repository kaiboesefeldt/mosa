Ziel

Die serverseitige Admin-Allowlist soll als eigene dateibasierte Laufzeitkonfiguration eingefuehrt werden.

Scope

- Eine Konfigurationsdatei `mosa_admin_gids.yaml` fuer Admin-GIDs einfuehren.
- Die Datei analog zur bestehenden Identity-Blocklist ueber den bestehenden Auto-Refresh-Mechanismus laden.
- Regelmaessiges Neu-Einlesen zur Laufzeit im 5s-Intervall unterstuetzen.
- Fehlende Datei als leere Admin-Liste behandeln.
- Bei ungueltigem Format die zuletzt erfolgreich geladene Admin-Liste weiterverwenden.

Vorgehen

- Die Konfiguration testgetrieben ueber die oeffentlichen oder bestehenden technischen Ladepfade einfuehren.
- Zunaechst einen minimalen Test fuer den Erfolgsfall mit gueltiger Datei anlegen.
- Danach die Randfaelle fuer fehlende Datei und ungueltiges Format absichern.
- Die neue Konfiguration so anbinden, dass andere Komponenten Admin-Rechte nur ueber diese geladene Allowlist abfragen.

Akzeptanzkriterien

- Eine gueltige `mosa_admin_gids.yaml` wird in eine Menge von Admin-GIDs geladen.
- Aenderungen an der Datei werden ohne Neustart nach spaetestens einem Refresh-Zyklus wirksam.
- Fehlt die Datei, ist niemand Admin.
- Nach einem Formatfehler bleibt die zuletzt erfolgreich geladene Admin-Liste wirksam.

Nicht Teil dieser Subtask

- Erweiterung der Loeschrechte fuer Posts
- HTTP-Tests fuer Admin-Delete-Verhalten
