Alle 3rd-Party-Abhängigkeiten im Projekt sollen auf aktuelle stabile Versionen angehoben werden.

Ziel ist, Sicherheits- und Wartungsrisiken durch veraltete Bibliotheken zu reduzieren, ohne das bestehende Verhalten des Systems unbeabsichtigt zu verändern. Die Aktualisierung soll alle relevanten Build- und Laufzeitabhängigkeiten in den betroffenen Modulen umfassen.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle direkt deklarierten 3rd-Party-Abhängigkeiten in den Build-Dateien identifizieren.
- Für diese Abhängigkeiten jeweils prüfen, welche aktuelle stabile Version sinnvoll ist.
- Die Versionen so aktualisieren, dass das Projekt weiterhin baut und die vorhandenen Checks bestehen.
- Änderungen mit besonderem Blick auf Breaking Changes und notwendige Code-Anpassungen bewerten.
- Falls einzelne Abhängigkeiten nicht sinnvoll sofort aktualisiert werden können, die Gründe dafür nachvollziehbar festhalten.

Bei der Umsetzung ist zu klären:

- Ob auch indirekte oder plugin-bezogene Abhängigkeiten Teil der Aufgabe sein sollen.
- Wie mit Abhängigkeiten umgegangen wird, bei denen ein Update größere API-Anpassungen nach sich zieht.
- Ob Updates in einem Schritt erfolgen sollen oder in mehrere kleinere Aufgaben aufgeteilt werden sollten.
- Welche Abhängigkeiten aus Stabilitäts- oder Kompatibilitätsgründen bewusst auf dem aktuellen Stand belassen werden.
