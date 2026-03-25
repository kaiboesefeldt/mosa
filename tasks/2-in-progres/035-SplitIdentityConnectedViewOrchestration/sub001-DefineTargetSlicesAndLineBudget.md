Ziel

Vor dem naechsten Refactoring soll fuer `IdentityConnectedView` ein verbindlicher Zuschnitt mit klaren Dateigrenzen und einer 300-Zeilen-Leitplanke festgelegt werden.

Scope

- Die verbleibenden Verantwortlichkeiten in `IdentityConnectedView.kt` kartieren.
- Einen konkreten Zielzuschnitt mit Dateinamen und Zustaendigkeiten festlegen.
- Fuer jeden Zielteil ein grobes Zeilenbudget definieren, sodass kein Teil ueber 300 Zeilen wachsen soll.
- Den Zuschnitt so waehlen, dass spaetere Extraktionen entlang echter fachlicher Grenzen erfolgen und nicht nur zufaellige Codebloecke verschieben.

Nicht Teil dieser Subtask

- Groessere Produktionscode-Extraktionen.
- Abschliessende Integrationspruefung.

Akzeptanzkriterien

- Es existiert eine nachvollziehbare Zielstruktur fuer die verbleibende View-Orchestrierung.
- Die Zielstruktur nennt konkrete Dateien oder Hooks fuer mindestens Content-Lifecycle, Moderation, Admin-Messaging, Mobile/Swipe/Observer und Shell.
- Fuer jeden Zielteil ist ein plausibles Zeilenbudget kleiner oder gleich 300 benannt.
- Die Struktur macht deutlich, welche Teile bewusst in der Hauptdatei verbleiben sollen.
