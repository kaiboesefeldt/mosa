Ziel

Alle durch das Lucene-Upgrade neu eingefuehrten Warnungen sollen, falls sie nach der eigentlichen Migration auftreten, gezielt bereinigt werden, insbesondere Deprecations und vergleichbare Build- oder Compiler-Hinweise mit direktem Bezug zur Migration.

Scope

- Nach der Umstellung auf Lucene `10.x` pruefen, welche neuen Warnungen tatsaechlich auftreten.
- Warnungen mit direktem Bezug zum Upgrade beseitigen.
- Falls noetig, betroffene Test- oder Hilfscodes anpassen, damit die Warnungen nicht offen bleiben.

Nicht Teil dieser Subtask

- Bereits vorher vorhandene, upgrade-unabhaengige Warnungen an anderen Stellen.
- Groessere Refactorings ohne direkten Warnungsbezug.
- Der abschliessende Repo-weite Integrationsnachweis.

Akzeptanzkriterien

- Falls das Lucene-Upgrade neue Warnungen einbringt, bleiben diese nicht offen stehen.
- Insbesondere neue Deprecation-Warnungen im produktiven oder direkt betroffenen Testcode sind beseitigt.
- Die vorgenommenen Anpassungen veraendern kein fachliches Verhalten.
