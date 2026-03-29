# Aktueller Zustand

## Fachlicher Zuschnitt

Der heutige Bereich `Moderation` besteht im Kern aus zwei getrennten, aber lose gekoppelten Funktionen:

- Beschwerden ueber einzelne Beitraege
- private Admin-Nachrichten an einzelne Nutzer

Eine echte Nutzer-Moderation mit Aktionen wie `Blockieren`, `Deblockieren` oder `Nutzer loeschen` existiert noch nicht als umgesetzter Flow. Dafuer gibt es bislang nur die offene Folgeaufgabe [029-AdminUserModerationActions](/home/kai/Projects/straightway/mosa/tasks/1-open/029-AdminUserModerationActions/Description.md).

## Server: Admin-Nachrichten

Aktuell gibt es drei relevante Endpunkte:

- `POST /v1/admin/messages`: Admin sendet eine Nachricht an genau ein Nutzerprofil.
- `GET /v1/messages`: angemeldeter Nutzer liest die eigene Inbox.
- `GET /v1/admin/messages?gid=...`: Admin liest den Verlauf der an ein bestimmtes Nutzerprofil gesendeten Nachrichten.

Berechtigungen:

- Senden und Admin-Verlauf sind nur fuer Admins erlaubt.
- Die Nutzer-Inbox liefert nur Nachrichten fuer die Identitaet des aktuellen JWT.
- Admin-Rechte haengen an der pod-lokalen Admin-Allowlist.

Persistenz und Lifecycle:

- Admin-Nachrichten liegen in SQLite in `mosa_db.sqlite`.
- Die Daten liegen ausserhalb des Content- und Vector-Stores als eigener strukturierter Zustand.
- Neue Nachrichten erhalten beim Erzeugen ein Ablaufdatum fuer den ungelesenen Zustand.
- Beim Abruf der Inbox wird die Restlaufzeit auf eine kuerzere `retrieved`-TTL begrenzt.
- Ein Hintergrundjob loescht abgelaufene Nachrichten periodisch.
- Der Inbox-Abruf unterstuetzt `fromTime`, damit der Client inkrementell nach neuen Nachrichten seit dem letzten bekannten Zeitstempel nachladen kann.

Was aktuell nicht vorhanden ist:

- Loeschen von Admin-Nachrichten
- Bearbeiten von Admin-Nachrichten
- Antworten, Threads oder Konversationen
- serverseitige Read-Receipts
- Audit- oder Moderationsprotokolle fuer Admin-Nachrichten

## Server: Beschwerden ueber Beitraege

Aktuell gibt es folgende Beschwerde-Endpunkte:

- `GET /v1/posts/{id}/complaint-status`: prueft, ob fuer einen Beitrag eine neue Beschwerde moeglich ist.
- `POST /v1/post-complaints`: legt eine neue Beschwerde fuer einen Beitrag an.
- `GET /v1/admin/post-complaints/count`: liefert die Zahl offener Beschwerden fuer Admins.
- `GET /v1/admin/post-complaints`: liefert die paginierte Beschwerde-Queue fuer Admins.
- `POST /v1/admin/post-complaints/decision`: Admin entscheidet ueber eine Beschwerde.

Aktuelle Fachlogik:

- Beschwerden beziehen sich auf einzelne Beitraege, nicht direkt auf Nutzerprofile.
- Der Autor eines Beitrags kann sich nicht ueber den eigenen Beitrag beschweren.
- Pro Beitrag kann immer nur eine offene Beschwerde gleichzeitig existieren.
- Beschwerde-Zustaende sind `OPEN`, `CONFIRMED`, `REJECTED` und `DELETED`.
- Bei einer Admin-Entscheidung mit Zielzustand `DELETED` wird der betroffene Beitrag geloescht.
- Nach jeder Entscheidung wird automatisch eine Admin-Nachricht mit dem Feedback an den Beschwerdefuehrer gesendet.

Persistenz:

- Beschwerden liegen ebenfalls im strukturierten SQLite-Zustand.
- Der offene Zustand wird technisch ueber einen `openMarker` abgesichert.
- Die Queue kann offen oder inklusive bereits bearbeiteter Eintraege abgefragt werden.

Was aktuell nicht vorhanden ist:

- direkte Moderationsobjekte fuer Nutzer statt fuer Beitraege
- ein gemeinsames Moderationsmodell fuer Beschwerden und Admin-Nachrichten
- explizite Semantik fuer Beschwerden beim Profil-Loeschen oder Freeze

## Client: Nutzer-Inbox und Beschwerdefluss

Normale Nutzer haben heute zwei sichtbare Moderationsbezuege:

- Sie koennen fuer fremde Beitraege eine Beschwerde einreichen.
- Sie koennen ihre Admin-Inbox abrufen.

Inbox-Verhalten im Client:

- Der Client speichert Inbox-Zustand pro `podBase` und `gid` in `localStorage`.
- Gespeichert werden bekannte Nachrichten, lokal als gesehen markierte Nachrichten und die zuletzt bekannten Eintraege.
- Gesehene Nachrichten werden aktuell lokal gefiltert; es gibt dafuer keinen serverseitigen Lesestatus.
- Beim Refresh werden neue Nachrichten ueber `fromTime` inkrementell nachgeladen und mit dem lokalen Snapshot zusammengefuehrt.

Beschwerdefluss im Client:

- Eine Beschwerde wird aus dem Beitragskontext gestartet.
- Vor dem Oeffnen des Eingabedialogs wird die Verfuegbarkeit serverseitig geprueft.
- Fuer den Nutzer gibt es nur den minimalen Flow `Grund eingeben und absenden`.

## Client: Admin-Flows

Admins haben heute zwei getrennte Bedienorte:

- die Beschwerde-Queue
- die Admin-Aktionsflaeche fuer einen konkreten Nutzer

Beschwerde-Queue:

- Admins koennen offene Beschwerden laden, paginieren und optional bearbeitete Eintraege einblenden.
- Fuer jede Beschwerde kann ein Entscheidungsdialog geoeffnet werden.
- Moegliche Aktionen haengen vom aktuellen Zustand ab.
- Nach erfolgreicher Entscheidung wird die Queue aktualisiert und die Inbox des verbundenen Nutzers erneut synchronisiert.

Admin-Aktionsflaeche fuer Nutzer:

- Sie wird aktuell aus dem Kontext eines dargestellten Beitrags geoeffnet.
- Der Einstieg liegt am Avatar eines fremden Nutzers.
- Die Flaeche zeigt den bisherigen Admin-Nachrichtenverlauf zu genau diesem Nutzerprofil.
- Admins koennen dort eine neue Nachricht mit `subject` und `body` verfassen und absenden.

Wichtige Beobachtung:

- Die heutige Admin-Aktionsflaeche ist noch keine allgemeine Nutzer-Moderationskarte.
- Sie ist fachlich vor allem ein kontextgebundener Nachrichten-Composer mit Verlaufsansicht.
- Die Freischaltung im Client haengt aktuell am vorhandenen Admin-Kontext des dargestellten Eintrags.

## Aktuelle Kopplungen und Brueche

Vorhandene Kopplungen:

- Beschwerden und Admin-Nachrichten treffen sich heute an genau einem Punkt: Eine Admin-Beschwerdeentscheidung erzeugt eine Admin-Nachricht an den Beschwerdefuehrer.
- Admin-Rechte werden sowohl fuer Beschwerdeentscheidungen als auch fuer Admin-Nachrichten ueber dieselbe pod-lokale Allowlist geprueft.
- Beide Datenarten liegen bereits in derselben strukturierten SQLite-Grundlage.

Aktuelle Brueche:

- Beschwerden sind beitragszentriert, Admin-Nachrichten profilzentriert.
- Die Admin-Aktionsflaeche arbeitet profilbezogen, die Beschwerde-Queue arbeitet fallbezogen.
- Es gibt noch keinen gemeinsamen Wahrheitsort fuer einen kompletten Moderationszustand eines Nutzerprofils.
- Folgefragen wie Profil-Loeschung, Freeze oder Aufraeumen verknuepfter Moderationsdaten sind noch offen und bereits als eigene Aufgaben erfasst.

## Wichtigste aktuelle Luecken

- Keine umgesetzten Nutzer-Moderationsaktionen wie `Blockieren`, `Deblockieren` oder `Nutzer loeschen`
- Keine Loeschfunktion fuer bestehende Admin-Nachrichten
- Keine definierte Behandlung von Beschwerden und Admin-Nachrichten bei Profil-Loeschung
- Keine definierte Behandlung dieser Daten im Freeze/Snapshot
- Kein zusammenhaengendes Moderationsmodell ueber Nutzer, Beschwerden und Admin-Kommunikation
- Keine serverseitigen Lesezustaende fuer die Inbox
- Keine Admin-Ansicht, die Moderationshistorie und Nutzeraktionen in einer gemeinsamen Oberflaeche zusammenfuehrt

## Anschluss an bestehende offene Aufgaben

Die aktuelle Bestandsaufnahme schliesst direkt an diese offenen Aufgaben an:

- [024-AllowAdminDeleteAdminMessages](/home/kai/Projects/straightway/mosa/tasks/1-open/024-AllowAdminDeleteAdminMessages/Description.md)
- [029-AdminUserModerationActions](/home/kai/Projects/straightway/mosa/tasks/1-open/029-AdminUserModerationActions/Description.md)
- [030-DeleteModerationDataWithUserProfile](/home/kai/Projects/straightway/mosa/tasks/1-open/030-DeleteModerationDataWithUserProfile/Description.md)
- [031-FreezeModerationDataWithUserProfile](/home/kai/Projects/straightway/mosa/tasks/1-open/031-FreezeModerationDataWithUserProfile/Description.md)

Zusammengefasst ist der Bereich funktional bereits benutzbar, aber noch kein geschlossenes Moderationssystem. Aktuell existieren mehrere kleine, fachlich sinnvolle Bausteine, die noch nicht zu einem konsistenten Gesamtmodell fuer Betreiberentscheidungen zusammengezogen sind.
