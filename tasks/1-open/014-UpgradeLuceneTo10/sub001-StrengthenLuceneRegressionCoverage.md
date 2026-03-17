Ziel

Vor dem Lucene-Major-Upgrade soll geprueft und bei Bedarf erweitert werden, ob die bestehende Testabdeckung die relevanten Regressionsrisiken im Such- und Vector-Store-Verhalten ausreichend absichert.

Scope

- Bestehende Lucene-bezogene Unit- und Integrationstests im Server sichten.
- Pruefen, welche oeffentlich beobachtbaren Risiken bereits abgedeckt sind.
- Fehlende automatisierte Tests gezielt ergaenzen, insbesondere fuer Schreibpfade, textuelle Suche, KNN-Suche, Pagination und Index-Handling.
- Neue Tests klein und deterministisch halten und nur oeffentlich beobachtbares Verhalten pruefen.

Nicht Teil dieser Subtask

- Das eigentliche Upgrade auf Lucene `10.x`.
- Produktive Code-Aenderungen ohne unmittelbaren Testbezug.
- Der abschliessende Repo-weite Vollcheck.

Akzeptanzkriterien

- Es liegt eine belastbare automatisierte Testbasis fuer die vom Upgrade betroffenen Lucene-Pfade vor.
- Testluecken mit erkennbarem Regressionsrisiko sind vor dem Upgrade geschlossen.
- Es gibt mindestens einen automatisierten Integrationspfad, der Lucene-Schreiben und anschliessendes Lesen gemeinsam abdeckt.
- Es gibt mindestens einen automatisierten Test fuer Pagination oder Folgeseiten-Verhalten auf Lucene-Basis.
- Die neuen oder angepassten Tests laufen mit den bestehenden Testwerkzeugen des Projekts.
