Ziel

Das bisherige Dumpen von Beispiel- und Hint-YAML auf `stdout` soll durch gezieltes Fehler-Logging ersetzt werden, das auf die erzeugten oder unvollstaendigen Dateien verweist.

Scope

- Bisherige `println`-basierte Ausgabe fuer diesen Fall entfernen oder ersetzen.
- Logging auf ERROR-Level verwenden.
- Log-Nachrichten auf konkrete Dateien und naechsten manuellen Schritt ausrichten.

Nicht Teil dieser Subtask

- Logik zum Schreiben fehlender Dateien.
- Inhaltliches Rendering der Vorlagen.
- Fachliche Validierungsregeln fuer Legal-Profile.

Akzeptanzkriterien

- Bei fehlenden oder unvollstaendigen Operator-Profilen wird kein unstrukturierter YAML-Dump mehr auf `stdout` ausgegeben.
- Stattdessen entsteht ein ERROR-Log mit Verweis auf `mosa_privacy.yaml` und/oder `mosa_terms.yaml`, sofern betroffen.
- Das Logging ist in automatisierten Tests oder ueber oeffentlich beobachtbares Verhalten pruefbar.
