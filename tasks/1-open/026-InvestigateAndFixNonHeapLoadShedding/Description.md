Es gibt wiederholt Probleme mit Load-Shedding, das durch hohe oder vermeintlich hohe Non-Heap-Memory-Auslastung ausgeloest wird. Das fuehrt offenbar zu unerwuenschten 503-/Shedding-Situationen, obwohl die eigentliche Last oder der verfuegbare Speicher nicht zwingend kritisch wirken.

Die Aufgabe ist, dieses Verhalten gezielt zu untersuchen und so zu korrigieren, dass Non-Heap-bedingtes Load-Shedding nur noch dann anspringt, wenn es fachlich und technisch wirklich sinnvoll ist.

Die Aufgabe umfasst mindestens diese Punkte:

- Verstehen, wie die aktuelle Non-Heap-Memory-Messung und ihre Schwellenwerte im bestehenden Defense-/Load-Shedding-Stack umgesetzt sind.
- Reproduzierbar machen, unter welchen Bedingungen Non-Heap-basiertes Shedding aktuell ausloest.
- Pruefen, ob die aktuelle Metrik fuer Non-Heap geeignet ist oder ob sie durch JVM-/Classloader-/Metaspace-/Code-Cache-Eigenschaften systematisch irrefuehrend ist.
- Pruefen, ob Konfiguration, Normalisierung oder Interpretation der Non-Heap-Werte fehlerhaft oder fuer reale Deployments zu aggressiv sind.
- Eine Korrektur umsetzen, die das beobachtete Fehlverhalten beseitigt, ohne sinnvolles Schutzverhalten bei echter Speicherdrucklage zu verlieren.
- Die Entscheidung fachlich nachvollziehbar dokumentieren, insbesondere falls die Loesung in einer geaenderten Metrik, anderen Schwellwerten oder einer engeren Definition von "kritischer" Non-Heap-Auslastung besteht.
- Automatisierte Tests ergaenzen oder anpassen, sodass das problematische Verhalten reproduzierbar abgesichert ist und nicht spaeter unbemerkt wiederkehrt.
- Falls die Untersuchung ergibt, dass das System unter den beobachteten Bedingungen tatsaechlich legitim an einer realen Non-Heap-Grenze laeuft, ist auch ein Ergebnis ohne Codeaenderung zulaessig, solange dieser Befund klar belegt und von einem nur scheinbaren Fehlverhalten abgegrenzt wird.

Akzeptanzkriterien:

- Es gibt eine klare technische Erklaerung dafuer, warum Non-Heap-basiertes Shedding bisher zu oft oder falsch angesprungen ist.
- Die Ursache ist im bestehenden Code oder in der Konfiguration nachvollziehbar lokalisiert.
- Es gibt mindestens einen automatisierten Test, der das bisher problematische Verhalten reproduziert oder den relevanten Fehlpfad direkt absichert.
- Nach der Umsetzung spricht Non-Heap-basiertes Shedding nicht mehr bei dem bisher beanstandeten Fehlverhalten an.
- Echte Schutzfaelle werden nicht versehentlich komplett abgeschaltet, sofern die Untersuchung nicht gerade ergibt, dass die bisherige Non-Heap-Regel insgesamt fachlich ungeeignet ist und ersetzt werden muss.
- Die finale Loesung bleibt im bestehenden Defense-Konzept einfach und nachvollziehbar und fuehrt keinen unnötig komplexen Sondermechanismus ein.
- Wenn keine Codeaenderung erfolgt, ist stattdessen klar dokumentiert, warum das beobachtete Shedding unter den gegebenen Systemgrenzen korrekt ist und daher kein Softwarefehler vorliegt.

Bei der Umsetzung ist insbesondere zu klaeren:

- Ob die aktuelle Non-Heap-Metrik auf `MemoryMXBean`/`MemoryUsage` fuer diesen Zweck ueberhaupt belastbar genug ist.
- Ob eher Metaspace, Code Cache oder andere Non-Heap-Anteile das Fehlverhalten treiben.
- Ob der Fehler primaer in der Messung, in den Grenzwerten oder in der Verknuepfung mit dem Shedding-Decider liegt.
