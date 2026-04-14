# Aktueller Zustand

## Fachlicher Zuschnitt

Der heutige Bereich `Moderation` besteht aus drei umgesetzten, fachlich verbundenen Bausteinen:

- Beschwerden ueber einzelne Beitraege
- private Admin-Nachrichten an einzelne Nutzer
- einfache Nutzer-Moderation durch Admins auf Profil-Ebene

Die Bereiche sind weiterhin nicht als ein einziges geschlossenes Moderationsmodell umgesetzt, aber sie greifen inzwischen sichtbar ineinander.

## Server: Nutzer-Moderation

Aktuell gibt es vier relevante Endpunkte:

- `GET /v1/admin/identities/capabilities?gid=...`: Admin liest den aktuellen Moderationsstatus eines Zielprofils.
- `POST /v1/admin/identities/block`: Admin blockiert ein Zielprofil.
- `POST /v1/admin/identities/unblock`: Admin hebt die Blockierung eines Zielprofils auf.
- `DELETE /v1/admin/identities/{gid}`: Admin loescht ein Zielprofil mitsamt verknuepften Moderationsdaten.

Berechtigungen und Schutzregeln:

- Alle Endpunkte sind nur fuer Admins zugaenglich.
- Admin-Rechte haengen an der pod-lokalen Admin-Allowlist.
- Admins duerfen sich nicht selbst moderieren.
- Admins duerfen keine anderen Admin-Profile blockieren, deblockieren oder loeschen.
- Ziel-GIDs werden serverseitig validiert und normalisiert.

Aktuelle Fachlogik:

- Der Status-Endpunkt liefert heute mindestens `isAdmin` und `isBlocked`.
- Blockierte Identitaeten werden bei authentifizierten Requests serverseitig gesperrt.
- Das eigene Profil darf trotz Blockstatus weiterhin ueber `DELETE /v1/identity` geloescht werden.
- Das Admin-Loeschen eines Profils ist kein reines Content-Delete, sondern ein aufgeraeumter Gesamtloeschvorgang fuer zugeordnete Moderationsdaten.

Persistenz und Lifecycle:

- Der Blockstatus liegt als eigener strukturierter Zustand ausserhalb des Vector-Stores vor.
- Beim Admin-Loeschen eines Profils werden Blockstatus, Beschwerden, Admin-Nachrichten und Inhalte des Zielprofils gemeinsam entfernt.

Was aktuell nicht vorhanden ist:

- feinere Moderationsstufen jenseits von `blocked` oder `deleted`
- Moderationsgruende, Fristen oder Wiedervorlagen fuer Nutzer-Massnahmen
- Audit- oder Verlaufsprotokolle fuer Block/Unblock/Delete auf Profil-Ebene

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
- Beim Profil-Loeschen werden verknuepfte Beschwerden des Zielprofils mit entfernt.
- Beschwerden sind in Freeze/Snapshot bereits beruecksichtigt.

Was aktuell nicht vorhanden ist:

- direkte Moderationsobjekte fuer Nutzer statt fuer Beitraege
- ein gemeinsames Moderationsmodell fuer Beschwerden, Nutzer-Massnahmen und Admin-Nachrichten
- eine fachlich reichere Fallhistorie ueber mehrere Moderationsereignisse hinweg

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
- Fuer den Nutzer gibt es weiterhin den minimalen Flow `Grund eingeben und absenden`.

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
- Die Flaeche zeigt zusaetzlich den aktuellen Moderationsstatus des Zielprofils.
- Admins koennen dort heute `Blockieren`, `Deblockieren` und `Nutzer loeschen` ausfuehren.

Wichtige Beobachtung:

- Die heutige Admin-Aktionsflaeche ist nicht mehr nur ein kontextgebundener Nachrichten-Composer.
- Sie ist inzwischen eine kleine profilbezogene Moderationskarte mit Nachrichtenverlauf und Basisaktionen.
- Sie bleibt aber weiterhin kontextgebunden und ist noch keine eigenstaendige allgemeine Admin-Konsole fuer Moderationsfaelle.

## Aktuelle Kopplungen und Brueche

Vorhandene Kopplungen:

- Beschwerden und Admin-Nachrichten treffen sich heute an genau einem Punkt: Eine Admin-Beschwerdeentscheidung erzeugt eine Admin-Nachricht an den Beschwerdefuehrer.
- Nutzer-Moderation und Admin-Nachrichten treffen sich heute in derselben Admin-Aktionsflaeche fuer ein Zielprofil.
- Admin-Rechte werden fuer Beschwerdeentscheidungen, Admin-Nachrichten und Nutzer-Moderation ueber dieselbe pod-lokale Allowlist geprueft.
- Beschwerden und Admin-Nachrichten liegen bereits in derselben strukturierten SQLite-Grundlage.
- Profil-Loeschung raeumt heute bereits verknuepfte Beschwerden, Admin-Nachrichten und Blockstatus mit auf.
- Freeze/Snapshot beruecksichtigt bereits moderation-nahe Daten fuer Beschwerden und Admin-Nachrichten.

Aktuelle Brueche:

- Beschwerden sind weiterhin beitragszentriert, Admin-Nachrichten und Nutzer-Massnahmen profilzentriert.
- Die Beschwerde-Queue arbeitet fallbezogen, die Admin-Aktionsflaeche arbeitet profilbezogen.
- Es gibt noch keinen gemeinsamen Wahrheitsort fuer die komplette Moderationshistorie eines Nutzerprofils.
- Blockstatus, Beschwerdefall und Admin-Kommunikation sind technisch verbunden, aber noch kein konsolidiertes Fachobjekt.

## Wichtigste aktuelle Luecken

- Keine Loeschfunktion fuer bestehende Admin-Nachrichten
- Kein zusammenhaengendes Moderationsmodell ueber Nutzer, Beschwerden und Admin-Kommunikation
- Keine serverseitigen Lesezustaende fuer die Inbox
- Keine Audit-Trails fuer Nutzer-Massnahmen oder Admin-Nachrichten
- Keine Admin-Ansicht, die Moderationshistorie, Beschwerden und Nutzeraktionen in einer gemeinsamen Oberflaeche zusammenfuehrt
- Keine feineren Moderationsmassnahmen wie temporaere Sperren, Begruendungen oder gestufte Sanktionen

## Anschluss an bestehende offene Aufgaben

Die aktuelle Bestandsaufnahme schliesst direkt an diese offenen Aufgaben an:

- [024-AllowAdminDeleteAdminMessages](/home/kai/Projects/straightway/mosa/tasks/1-open/024-AllowAdminDeleteAdminMessages/Description.md)
- [029-AdminUserModerationActions](/home/kai/Projects/straightway/mosa/tasks/1-open/029-AdminUserModerationActions/Description.md)
- [030-DeleteModerationDataWithUserProfile](/home/kai/Projects/straightway/mosa/tasks/1-open/030-DeleteModerationDataWithUserProfile/Description.md)
- [031-FreezeModerationDataWithUserProfile](/home/kai/Projects/straightway/mosa/tasks/1-open/031-FreezeModerationDataWithUserProfile/Description.md)

Von diesen Folgeaufgaben sind Teile inzwischen bereits fachlich vorweggenommen:

- Die in [029-AdminUserModerationActions](/home/kai/Projects/straightway/mosa/tasks/1-open/029-AdminUserModerationActions/Description.md) beschriebenen Basisaktionen `Blockieren`, `Deblockieren` und `Nutzer loeschen` existieren bereits.
- Die in [030-DeleteModerationDataWithUserProfile](/home/kai/Projects/straightway/mosa/tasks/1-open/030-DeleteModerationDataWithUserProfile/Description.md) adressierte Kopplung zur Profil-Loeschung ist fuer Beschwerden und Admin-Nachrichten bereits umgesetzt.
- Die in [031-FreezeModerationDataWithUserProfile](/home/kai/Projects/straightway/mosa/tasks/1-open/031-FreezeModerationDataWithUserProfile/Description.md) adressierte Beruecksichtigung im Freeze/Snapshot ist fuer Beschwerden und Admin-Nachrichten bereits umgesetzt.

Zusammengefasst ist der Bereich funktional deutlich weiter als eine reine Bestandsaufnahme aus den frueheren Aufgaben vermuten laesst. Aktuell existieren bereits Beitraagsbeschwerden, Admin-Kommunikation und einfache Profil-Moderation, aber noch kein einheitliches Moderationsmodell mit gemeinsamer Historie, vollstaendiger Admin-Konsole und konsistentem Audit-Trail.
