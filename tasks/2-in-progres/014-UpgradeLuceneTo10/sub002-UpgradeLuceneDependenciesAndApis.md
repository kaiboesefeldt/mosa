Ziel

Die Lucene-Abhaengigkeiten im Server sollen auf die bei Bearbeitungsbeginn per Web-Recherche verifizierte neueste stabile `10.x`-Version angehoben und die dafuer noetigen API-Anpassungen minimal umgesetzt werden.

Scope

- Die im Server direkt verwendeten Lucene-Module auf die Zielversion anheben.
- Relevante API-Aenderungen zwischen `9.12.3` und der Zielversion bewerten.
- Notwendige produktive Anpassungen in Query-Planung, Index-Zugriff, Schreiblogik und Vector-Store minimal umsetzen.
- Bestehendes beobachtbares Verhalten soweit unveraendert erhalten.

Nicht Teil dieser Subtask

- Zuschnitt oder Erweiterung der Testabdeckung ohne direkten Bedarf fuer die Migration.
- Spezifische Behandlung lokaler inkompatibler Altindizes, sofern dafuer eigenes Verhalten oder eigene Dokumentation noetig ist.
- Abschliessende Behandlung aller eventuell neu eingefuehrten Warnungen.
- Gesamtintegration aller betroffenen Komponenten im Repo.

Akzeptanzkriterien

- Die Lucene-Dependencies im Server sind auf die verifizierte neueste stabile `10.x`-Version aktualisiert.
- Der produktive Code kompiliert gegen Lucene `10.x`.
- Textuelle Suche, KNN-Suche, Pagination und Schreibpfade verhalten sich weiterhin entsprechend der vorhandenen Testbasis.
- Nur die fuer die Migration noetigen produktiven Code-Aenderungen wurden vorgenommen.
