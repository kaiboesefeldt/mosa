Mosa soll eine erste Funktion bekommen, mit der Nutzerinnen und Nutzer sich über einzelne Beiträge beschweren können.

Im ersten Wurf soll das bewusst einfach bleiben: pro Beitrag gibt es einen einzelnen Beschwerde-Button ohne komplexen Melde-Flow und ohne Kategorien. Gleichzeitig braucht es aber einen einfachen Admin-Workflow, in dem eingegangene Beschwerden sichtbar und bearbeitbar sind.

Die Aufgabe umfasst mindestens diese Punkte:

- Bei einzelnen Beiträgen soll eine klar erkennbare Beschwerde-Aktion verfügbar sein.- 
  - Die Beschwerde-Aktion soll nur für Beiträge anderer User:innen verfügbar sein.
  - Das Auslösen der Beschwerde muss sich auf genau den betroffenen Beitrag beziehen und hat ein Freitextfeld beschränkter Größe, wo ein User seine Beschwerde begründen kann.
  - Der Server muss Beschwerden entgegennehmen und so speichern oder weiterleiten, dass sie später bearbeitet werden können.
  - Die Beschwerde wird von dem User digital signiert, wenn er sie absendet.
  - Eine Beschwerde enthält diese Daten:
    - Identität des User, der die Beschwerde einreicht
    - ID des Beitrages, über den sich beschwert wird
    - Kurzer Freitext, der die Beschwerde begründet
    - Bei der serverseitigen Speicherung: Zeitstempel des Empfangs
    - Vor Absenden der Beschwerde wird noch einmal eine Bestätigung des Users eingeholt
    - Beim Drücken des Beschwerdebuttons wird der Beschwerdezustand eines Beitrags direkt von Server geholt. Liegt bereits eine Beschwerde zu dem Beitrag vor, so wird der User darauf hingewiesen und kann keine weitere Beschwerde dazu einreichen.
    - Auch das Absenden der Beschwerde kann mit einem entsprechenden HTTP-Fehler beantwortet werden, falls ganz kurz vorher bereits eine andere Beschwerde zu dem Beitrag empfangen wurde. Damit ist sichergestellt, dass es zu jedem Beitrag höchstens eine Beschwerde gibt.
    - Beiträge haben im Client KEIN Kennzeichen über Beschwerden, diese Information wird beim Beschwerdeführen zeitnah vom Server eingeholt. 
  - Es muss verhindert werden, dass die Funktion nur kosmetisch im Client existiert; die eigentliche Beschwerde-Erfassung braucht serverseitiges Verhalten.
- Die Bedienung soll den normalen Lesefluss so wenig wie möglich stören.
- Die Lösung soll so einfach bleiben, dass sie als erster Wurf schnell nutzbar ist.
- Beschwerden müssen für Admin-Konten sichtbar sein.
  - Admin-Konten müssen eine Beschwerde bearbeiten können, indem sie entweder den betroffenen Beitrag löschen, ihn als "Beschwerde liegt vor" markieren oder ihn ausdrücklich als "in Ordnung" approven.
  - Wenn ein Beitrag als "in Ordnung" approved wurde, darf es danach keine weiteren Beschwerden für diesen Beitrag mehr geben.
  - Die Statusänderung der Beschwerde ist mit dem Ausfüllen eines Freitextfeldes verbunden, das dem Beschwerdeführer über die Admin-Nachrichten zugestellt wird. 
  - Auch die Bearbeitung von Beschwerden und das Approven müssen serverseitig abgesichert werden.
  - Die Bearbeitung muss von dem ausführenden Admin digital signiert werden.
- Beschwerden werden in der Datenbank gespeicher.

UI- und UX-Richtung fuer einen stimmigen ersten Wurf:

- Die Beschwerde-Aktion fuer normale Nutzer soll direkt am Beitrag als kleine, klar erkennbare Aktion erscheinen, ohne den normalen Lesefluss mit einem komplexen Dialog zu unterbrechen.
- Beschwerden sollen im Client nicht als normale Admin-Nachrichten modelliert oder in die bestehende Inbox gemischt wirken, weil es sich fachlich um contentbezogene Moderation und nicht um nutzerbezogene Kommunikation handelt.
- Fuer Admin-Konten soll es stattdessen einen eigenen Moderations-Einstieg mit Badge fuer offene Beschwerden geben, analog zur vorhandenen Inbox-Logik, aber als getrennte Moderations-Queue.
- Dieser Einstieg soll eine kompakte Liste offener Beschwerden oeffnen, in der pro Eintrag der betroffene Beitrag im kompletten Text mit Autor und Zeitstempel, der Beschwerdeführer, die Begründing der Beschwerde sowie deren Zeitstempel
- Die Bearbeitung soll in dieser Moderations-Queue mit genau den drei Zielaktionen moeglich sein: Beitrag loeschen, als "Beschwerde liegt vor" kennzeichnen oder als in Ordnung approven.
- Die bestehende userbezogene Admin-Aktionskarte und die private Admin-Nachrichten-Inbox bleiben davon fachlich getrennt und werden fuer Beschwerden nicht zweckentfremdet.
