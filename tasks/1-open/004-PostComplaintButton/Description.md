Mosa soll eine erste Funktion bekommen, mit der Nutzerinnen und Nutzer sich über einzelne Beiträge beschweren können.

Im ersten Wurf soll das bewusst einfach bleiben: pro Beitrag gibt es einen einzelnen Beschwerde-Button ohne komplexen Melde-Flow und ohne Kategorien. Gleichzeitig braucht es aber einen einfachen Admin-Workflow, in dem eingegangene Beschwerden sichtbar und bearbeitbar sind.

Die Aufgabe umfasst mindestens diese Punkte:

- Bei einzelnen Beiträgen soll eine klar erkennbare Beschwerde-Aktion verfügbar sein.
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
- Dieser Einstieg soll eine kompakte Liste offener Beschwerden oeffnen, in der pro Eintrag der betroffene Beitrag im kompletten Text mit Autor und Zeitstempel, der Beschwerdeführer, die Begründing der Beschwerde sowie deren Zeitstempel angezeigt wird.
- Die Bearbeitung soll in dieser Moderations-Queue mit genau den drei Zielaktionen moeglich sein: Beitrag loeschen, als "Beschwerde liegt vor" kennzeichnen oder als in Ordnung approven.
- Die bestehende userbezogene Admin-Aktionskarte und die private Admin-Nachrichten-Inbox bleiben davon fachlich getrennt und werden fuer Beschwerden nicht zweckentfremdet.

Fachliche Festlegungen fuer einen einfachen, implementierbaren ersten Wurf:

- Pro Beitrag gibt es zu einem Zeitpunkt hoechstens einen offenen Moderationsfall.
- Der Moderationsfall entsteht mit der ersten erfolgreich gespeicherten Beschwerde zu einem Beitrag.
- Ein Moderationsfall hat genau einen dieser Zustaende:
  - `OPEN`: Beschwerde eingegangen und noch nicht entschieden.
  - `CONFIRMED`: Admin hat entschieden, dass die Beschwerde berechtigt ist.
  - `REJECTED`: Admin hat entschieden, dass der Beitrag in Ordnung ist.
  - `DELETED`: Der betroffene Beitrag wurde im Rahmen der Beschwerdebearbeitung geloescht.
- Die Admin-Aktion "Beschwerde liegt vor" entspricht fachlich dem Zustand `CONFIRMED`.
- Die Admin-Aktion "in Ordnung approven" entspricht fachlich dem Zustand `REJECTED`.
- Nur `OPEN`-Faelle erscheinen in der offenen Moderations-Queue und zaehlen fuer das Badge.
- `CONFIRMED`, `REJECTED` und `DELETED` sind Endzustaende fuer den jeweiligen Moderationsfall.
- `REJECTED` und `DELETED` sperren den Beitrag dauerhaft fuer weitere Beschwerden.
- `CONFIRMED` bedeutet dagegen: Die aktuelle Beschwerde ist erledigt und berechtigt, aber fuer denselben Beitrag darf spaeter erneut eine neue Beschwerde entstehen.
- Damit gilt die Einmaligkeitsregel bewusst einfach: Es kann je Beitrag immer nur einen offenen Fall geben, aber nach `CONFIRMED` darf spaeter wieder ein neuer `OPEN`-Fall angelegt werden.

Einfaches Datenmodell fuer den ersten Wurf:

- Eine Beschwerde speichert mindestens:
  - die ID des betroffenen Beitrags
  - die GID des Beschwerdefuehrers
  - den Freitext der Beschwerde
  - den serverseitigen Empfangszeitstempel
  - die Signatur des Beschwerdefuehrers
  - die Signaturversion des Beschwerdefuehrers
  - den aktuellen Moderationszustand
- Die Moderationsentscheidung speichert zusaetzlich:
  - die GID des entscheidenden Admins
  - den serverseitigen Entscheidungszeitstempel
  - den Freitext fuer die Rueckmeldung an den Beschwerdefuehrer
  - die Signatur des Admins
  - die Signaturversion des Admins
- Die Beschwerde verweist nur per `postId` auf den Beitrag. Der Beitrag selbst wird im ersten Wurf nicht um ein Beschwerdekennzeichen erweitert.

Signaturfestlegungen:

- Die Nutzer-Beschwerde wird als eigenes signiertes Payload behandelt und nicht als normales `PublishRequest`.
- Das signierte Payload der Nutzer-Beschwerde enthaelt:
  - Payload-Version
  - Typ `POST_COMPLAINT`
  - betroffene `postId`
  - Begruendungstext
- Die Admin-Entscheidung wird ebenfalls als eigenes signiertes Payload behandelt.
- Das signierte Payload der Admin-Entscheidung enthaelt:
  - Payload-Version
  - Typ `POST_COMPLAINT_DECISION`
  - betroffene `postId`
  - Zielzustand `CONFIRMED`, `REJECTED` oder `DELETED`
  - Rueckmeldungstext an den Beschwerdefuehrer
- Fuer beide Payloads reicht im ersten Wurf eine kanonische JSON-Serialisierung analog zu den vorhandenen Content-Signaturen.

Validierungs- und Konfliktregeln:

- Das Beschwerde-Freitextfeld ist Pflichtfeld und auf 500 Zeichen begrenzt.
- Das Rueckmeldungsfeld des Admins ist Pflichtfeld und auf 1000 Zeichen begrenzt.
- Leere oder nur aus Whitespace bestehende Texte sind ungueltig.
- Eine Beschwerde gegen den eigenen Beitrag ist serverseitig unzulaessig, auch wenn der Client die Aktion bereits ausblendet.
- Eine Beschwerde gegen einen nicht vorhandenen Beitrag liefert `404 Not Found`.
- Wenn fuer einen Beitrag bereits ein offener Moderationsfall existiert, liefert das Anlegen einer neuen Beschwerde `409 Conflict`.
- Wenn der letzte Moderationsfall eines Beitrags den Zustand `CONFIRMED` hat, darf das Anlegen einer neuen Beschwerde wieder erfolgreich sein und erzeugt einen neuen `OPEN`-Fall.
- Wenn der letzte Moderationsfall eines Beitrags den Zustand `REJECTED` oder `DELETED` hat, bleibt eine neue Beschwerde unzulaessig und liefert `409 Conflict`.
- Wenn zwei Beschwerden fast gleichzeitig eingehen, darf genau eine erfolgreich gespeichert werden; die andere endet ebenfalls mit `409 Conflict`.
- Eine Admin-Entscheidung auf einem bereits abgeschlossenen Fall liefert `409 Conflict`.
- Nur Admins duerfen offene Beschwerden abrufen oder entscheiden; andere authentisierte Nutzer erhalten `403 Forbidden`.

API-Richtung fuer den ersten Wurf:

- Der Client holt beim Oeffnen des Beschwerde-UI den aktuellen Beschwerdestatus exakt fuer den betroffenen Beitrag vom Server.
- Dafuer gibt es einen kleinen statusorientierten Endpunkt pro Beitrag, der nur zur unmittelbaren Aktion dient und kein allgemeines Beschwerde-Listing fuer normale Nutzer bereitstellt.
- Das Anlegen einer Beschwerde erfolgt ueber einen eigenen Endpunkt und schreibt serverseitig in SQLite.
- Fuer Admins gibt es:
  - einen Endpunkt fuer die Anzahl offener Beschwerden
  - einen paginierten Endpunkt fuer offene Beschwerden, sortiert nach Eingangszeit aufsteigend
  - einen Endpunkt fuer die Entscheidung ueber eine offene Beschwerde
- Eine eigene Historienansicht abgeschlossener Beschwerden ist nicht Teil dieses ersten Wurfs.

Wirkung der Admin-Entscheidung:

- Bei jeder Admin-Entscheidung wird genau eine private Admin-Nachricht an den Beschwerdefuehrer erzeugt.
- Diese Nachricht bleibt fachlich ein Admin-Message-Eintrag; die Beschwerde selbst bleibt davon getrennt modelliert.
- Bei der Aktion `DELETED` wird zuerst die Moderationsentscheidung gespeichert und danach der betroffene Beitrag ueber den bestehenden serverseitigen Loeschpfad entfernt.
- Falls das Loeschen des Beitrags zwischenzeitlich nicht mehr moeglich ist, wird die gesamte Aktion als Fehler behandelt und der Moderationsfall bleibt `OPEN`.

Akzeptanzkriterien fuer Implementierungsreife:

- Die Aufgabe gilt als ausreichend spezifiziert, wenn die Implementierung mit genau einem offenen Moderationsfall pro Beitrag auskommt, parallele Mehrfachbeschwerden auf denselben offenen Fall verhindert und nach `CONFIRMED` wieder einen neuen offenen Fall fuer denselben Beitrag zulassen kann.
- Die Aufgabe fuehrt keine allgemeine Sichtbarkeit von Beschwerdekennzeichen fuer normale Nutzer ein.
- Die offene Moderations-Queue fuer Admins bleibt von der bestehenden Admin-Message-Inbox getrennt.
- Die fachliche Rueckmeldung an den Beschwerdefuehrer erfolgt ausschliesslich ueber die bestehende Admin-Message-Infrastruktur.
