Ziel

Die Gesamtintegration von Admin-Allowlist, Reload-Verhalten und serverseitiger Delete-Berechtigung soll ueber einen abschliessenden automatisierten Test nachgewiesen werden.

Scope

- Gemeinsame Betrachtung von Laufzeitkonfiguration und HTTP-Loeschpfad.
- Nachweis des Erfolgsfalls fuer normalen Autor und fuer Admin.
- Nachweis des Fehlfalls fuer Nicht-Admin bei Fremd-Loeschung.
- Nachweis, dass eine aktualisierte Admin-Allowlist ohne Neustart wirksam wird.

Form des Abschlusstests

- Vollautomatisierter Test mit den bestehenden Testwerkzeugen des Projekts.

Vorbedingungen

- Die Admin-Allowlist kann aus `mosa_admin_gids.yaml` geladen und zur Laufzeit neu eingelesen werden.
- Die Loeschpruefung fuer den Einzel-Delete beruecksichtigt Admin-Rechte.
- Die bestehenden Authentifizierungswege fuer den Loesch-Endpoint sind unveraendert nutzbar.

Durchfuehrung

- Einen Testfall fuer einen normalen Autor ausfuehren und pruefen, dass der eigene Beitrag geloescht werden kann.
- Einen Testfall fuer einen Nicht-Admin ausfuehren und pruefen, dass ein fremder Beitrag nicht geloescht werden kann.
- Einen Testfall fuer einen Admin ausfuehren und pruefen, dass derselbe fremde Beitrag ueber `DELETE /v1/posts/{id}` geloescht werden kann.
- Einen Testfall ausfuehren, in dem die Admin-Datei waehrend der Laufzeit geaendert wird.
- Dabei pruefen, dass ein zuvor nicht privilegiertes Konto nach dem Reload fremde Beitraege loeschen darf oder ein zuvor privilegiertes Konto dieses Recht verliert, ohne dass der Server neu gestartet wird.
- Einen Testfall fuer ungueltige Admin-Datei nach zuvor gueltiger Konfiguration ausfuehren.
- Dabei pruefen, dass die zuletzt erfolgreich geladene Admin-Liste weiterhin gilt.

Akzeptanzkriterien

- Der Abschlusstest betrachtet Konfiguration, Reload und Delete-Endpoint zusammen.
- Der Erfolgsfall deckt Eigenloeschung und Admin-Loeschung gemeinsam ab.
- Der Fehlfall weist nach, dass Nicht-Admins keine fremden Inhalte loeschen duerfen.
- Der Reload-Fall weist nach, dass Admin-Aenderungen ohne Neustart wirksam werden.
- Der Fehlerfall weist nach, dass eine ungueltige Admin-Datei die zuletzt gueltige Konfiguration nicht verdrangt.
