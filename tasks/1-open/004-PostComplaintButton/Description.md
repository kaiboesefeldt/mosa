Mosa soll eine erste Funktion bekommen, mit der Nutzerinnen und Nutzer sich über einzelne Beiträge beschweren können.

Voraussetzung fuer diese Aufgabe ist die vorgeschaltete SQLite-Einfuehrung fuer dynamische strukturierte Zustandsdaten, damit Beschwerden und ihre spaeteren Moderationszustaende nicht auf einem weiteren ad-hoc-Dateispeicher aufbauen.

Im ersten Wurf soll das bewusst einfach bleiben: pro Beitrag gibt es einen einzelnen Beschwerde-Button ohne komplexen Melde-Flow und ohne Kategorien. Gleichzeitig braucht es aber einen einfachen Admin-Workflow, in dem eingegangene Beschwerden sichtbar und bearbeitbar sind.

Die Aufgabe umfasst mindestens diese Punkte:

- Bei einzelnen Beiträgen soll eine klar erkennbare Beschwerde-Aktion verfügbar sein.
- Das Auslösen der Beschwerde muss sich auf genau den betroffenen Beitrag beziehen.
- Der Server muss Beschwerden entgegennehmen und so speichern oder weiterleiten, dass sie später bearbeitet werden können.
- Es muss verhindert werden, dass die Funktion nur kosmetisch im Client existiert; die eigentliche Beschwerde-Erfassung braucht serverseitiges Verhalten.
- Die Bedienung soll den normalen Lesefluss so wenig wie möglich stören.
- Die Lösung soll so einfach bleiben, dass sie als erster Wurf schnell nutzbar ist.
- Beschwerden müssen für Admin-Konten sichtbar sein.
- Admin-Konten müssen eine Beschwerde bearbeiten können, indem sie entweder den betroffenen Beitrag löschen oder ihn ausdrücklich als in Ordnung approven.
- Wenn ein Beitrag als in Ordnung approved wurde, darf es danach keine weiteren Beschwerden für diesen Beitrag mehr geben.
- Auch die Bearbeitung von Beschwerden und das Approven müssen serverseitig abgesichert werden.

Bei der Umsetzung ist zu klären:

- Welche minimalen Daten zu einer Beschwerde im ersten Schritt gespeichert werden sollen, zum Beispiel Beitrag, meldendes Konto und Zeitpunkt.
- Ob das Abschicken direkt mit einem Klick passiert oder ob vorab noch eine kurze Bestätigung nötig ist.
- Wie mit Mehrfachbeschwerden derselben Person für denselben Beitrag umgegangen werden soll.
- Wie der Admin-Zugriff auf die Beschwerdeansicht an die separate Aufgabe für Admin-Berechtigungen angebunden wird.
- Ob ein Approve frühere offene Beschwerden am Beitrag automatisch erledigt oder ob ein eigener Abschlusszustand für Beschwerden gespeichert werden soll.
- Wie sichtbar gemacht wird, dass ein Beitrag bereits approved wurde und deshalb nicht erneut beschwert werden kann.

UI- und UX-Richtung fuer einen stimmigen ersten Wurf:

- Die Beschwerde-Aktion fuer normale Nutzer soll direkt am Beitrag als kleine, klar erkennbare Aktion erscheinen, ohne den normalen Lesefluss mit einem komplexen Dialog zu unterbrechen.
- Beschwerden sollen im Client nicht als normale Admin-Nachrichten modelliert oder in die bestehende Inbox gemischt wirken, weil es sich fachlich um contentbezogene Moderation und nicht um nutzerbezogene Kommunikation handelt.
- Fuer Admin-Konten soll es stattdessen einen eigenen Moderations-Einstieg mit Badge fuer offene Beschwerden geben, analog zur vorhandenen Inbox-Logik, aber als getrennte Moderations-Queue.
- Dieser Einstieg soll eine kompakte Liste offener Beschwerden oeffnen, in der pro Eintrag mindestens der betroffene Beitrag, ein kurzer Ausschnitt, der Autor, die Anzahl offener Beschwerden und ein zeitlicher Hinweis sichtbar sind.
- Die Bearbeitung soll in dieser Moderations-Queue mit genau den beiden ersten Zielaktionen moeglich sein: Beitrag loeschen oder als in Ordnung approven.
- Zusaetzlich kann am betroffenen Beitrag fuer Admins ein dezenter Statushinweis wie gemeldet oder approved angezeigt werden, damit der Moderationskontext auch in der normalen Content-Ansicht sichtbar bleibt.
- Die bestehende userbezogene Admin-Aktionskarte und die private Admin-Nachrichten-Inbox bleiben davon fachlich getrennt und werden fuer Beschwerden nicht zweckentfremdet.
