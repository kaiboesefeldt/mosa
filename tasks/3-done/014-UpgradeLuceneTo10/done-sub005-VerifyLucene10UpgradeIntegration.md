Ziel

Die Gesamtintegration des Lucene-`10.x`-Upgrades soll abschliessend ueber automatisierte Ausfuehrung der relevanten Tests und des Repo-weiten Vollchecks nachgewiesen werden.

Scope

- Gemeinsame Betrachtung von Testabdeckung, produktiver Lucene-Migration, lokalem Index-Verhalten und Warnungsfreiheit.
- Nachweis, dass die wichtigsten Lucene-relevanten Pfade nach dem Upgrade gemeinsam funktionieren.
- Ausfuehrung des finalen Qualitaetsgates fuer das Repo.

Form des Abschlusstests

- Vollautomatisierter Test mit den bestehenden Testwerkzeugen des Projekts und dem abschliessenden Build-Check.

Vorbedingungen

- Die Lucene-Testbasis ist vor dem Upgrade geprueft und bei Bedarf erweitert.
- Die Lucene-Dependencies und API-Anpassungen fuer `10.x` sind umgesetzt.
- Der Umgang mit inkompatiblen lokalen `9.x`-Indizes ist geklaert und implementiert.
- Durch das Upgrade neu eingebrachte Warnungen sind beseitigt.

Durchfuehrung

- Alle fuer den Lucene-Vector-Store relevanten Unit- und Integrationstests ausfuehren.
- Dabei insbesondere die Pfade fuer Schreiben, textuelle Suche, KNN-Suche, Pagination und Index-Handling gemeinsam betrachten.
- Falls es einen expliziten Test fuer den Umgang mit inkompatiblen lokalen Indizes gibt, diesen mit ausfuehren und auf das beabsichtigte Verhalten pruefen.
- Anschliessend `./gradlew check` ausfuehren.

Akzeptanzkriterien

- Die betroffenen Lucene-Tests sind grün.
- Die gemeinsam betrachteten Hauptpfade fuer Schreiben, Suche, Pagination und Index-Handling funktionieren nachweisbar unter Lucene `10.x`.
- Das beabsichtigte Verhalten fuer lokal verwerfbare `9.x`-Indizes ist nachweisbar abgesichert oder nachvollziehbar dokumentiert.
- `./gradlew check` laeuft erfolgreich durch.
