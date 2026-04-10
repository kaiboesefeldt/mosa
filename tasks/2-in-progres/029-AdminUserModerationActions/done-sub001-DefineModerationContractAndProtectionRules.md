Diese Subtask legt den fachlichen und technischen Vertrag fuer Task 029 verbindlich fest.

Ziel ist, dass Server- und Client-Implementierung ohne weitere Fachklaerung beginnen koennen.

Entscheidungen

- Moderationsziel ist immer genau eine pod-lokale Identitaet, adressiert ueber ihre `gid`.
- `Nutzer loeschen` verwendet dieselbe serverseitige Loeschlogik wie die bestehende Selbstloeschung. Unterschied nur beim Aufruf: Selbstloeschung leitet Ziel-`gid` aus JWT ab, Admin-Loeschung uebergibt Ziel-`gid` explizit.
- Identity-Sperrzustaende bleiben in der bestehenden dateibasierten `mosa_identity_blocklist.yaml`.
- Fuer Task 029 wird kein SQLite-Migrationspfad eingefuehrt. Grund: bestehender Sperrmechanismus existiert bereits, ist serverweit wirksam und vermeidet fuer diesen Inkrement zusaetzliche Persistenz-, Migrations- und Freeze-Folgearbeit.
- Admin-Moderation bekommt eigene Admin-Endpunkte. Bestehender Selbstloesch-Endpunkt `DELETE /v1/identity` bleibt unveraendert.
- Blockieren und Deblockieren laufen nicht ueber Spezialregeln im Client, sondern ueber denselben serverseitigen Identity-Sperrmechanismus, der bereits authentifizierte Requests blockiert.

Schutzregeln

- Admin darf eigene Identitaet nicht blockieren.
- Admin darf eigene Identitaet nicht loeschen.
- Weitere Admin-Konten duerfen in Task 029 ebenfalls nicht blockiert, deblockiert oder geloescht werden.
- Schutzpruefung ist serverseitig verbindlich. Client darf diese Ziele zusaetzlich ausblenden oder deaktivieren, aber nicht allein absichern.
- Deblockieren ist nur fuer aktuell blockierte, nicht geschuetzte Zielidentitaeten erlaubt.
- Blockieren ist nur fuer aktuell nicht blockierte, nicht geschuetzte Zielidentitaeten erlaubt.
- Loeschen ist nur fuer aktuell nicht geschuetzte Zielidentitaeten erlaubt.

Oeffentliche API

- `POST /v1/admin/identities/block`
- `POST /v1/admin/identities/unblock`
- `DELETE /v1/admin/identities/{gid}`

Request und Response

- Blockieren und Deblockieren erhalten Body mit genau einem Feld `gid`.
- Admin-Loeschen adressiert Zielidentitaet ueber Path-Parameter `gid`.
- Blockieren antwortet bei Erfolg mit `204 No Content`.
- Deblockieren antwortet bei Erfolg mit `204 No Content`.
- Admin-Loeschen antwortet bei Erfolg mit `200 OK` und demselben `DeleteIdentityResult` wie bestehende Selbstloeschung.
- `gid`-Validierung folgt denselben Formatregeln wie in bestehenden Admin-Nachrichten-APIs.

Fehlerfaelle

- Nicht authentifiziert: `401 Unauthorized`
- Authentifiziert, aber kein Admin: `403 Forbidden`
- Ziel-`gid` fehlt oder hat invalides Format: `400 Bad Request`
- Zielidentitaet ist geschuetzt: `403 Forbidden`
- Blockieren einer bereits blockierten Identitaet: `409 Conflict`
- Deblockieren einer nicht blockierten Identitaet: `409 Conflict`
- Admin-Loeschen einer bereits geloeschten oder fachlich unbekannten Identitaet ist fuer Task 029 kein Sonderfehler. Es wird dieselbe Loeschlogik wie bei Selbstloeschung ausgefuehrt; sichtbares Ergebnis bestimmt sich ausschliesslich aus deren Rueckgabe.

Client-Vertrag

- Admin-Karte zeigt nie gleichzeitig `Blockieren` und `Deblockieren`.
- Sichtbarer Blockstatus muss aus serverseitig geladenem Zustand oder aus eindeutigem Admin-Zustand ableitbar sein.
- `Loeschen` bleibt separate destruktive Aktion mit Bestaetigungsdialog.
- `Blockieren` und `Deblockieren` brauchen in diesem Inkrement keinen eigenen Textdialog, aber klaren Busy- und Fehlerzustand.
- Bestehender Admin-Nachrichtenfluss in derselben Karte bleibt unveraendert.

Serverseitige erste TDD-Schritte fuer Subtask 002

1. Test: Admin kann fremde nicht blockierte Identitaet blockieren.
2. Test: Nicht-Admin kann Zielidentitaet nicht blockieren.
3. Test: Admin kann eigene Identitaet nicht blockieren.
4. Test: Admin kann anderes Admin-Konto nicht blockieren.
5. Test: Blockieren bereits blockierter Identitaet liefert `409 Conflict`.
6. Test: Admin kann blockierte Identitaet deblockieren.
7. Test: Deblockieren nicht blockierter Identitaet liefert `409 Conflict`.
8. Test: Admin-Loeschen liefert `DeleteIdentityResult` mit gleicher Loeschwirkung wie Selbstloeschung.
9. Test: Admin kann eigene Identitaet nicht loeschen.
10. Test: Admin kann anderes Admin-Konto nicht loeschen.

Erfolgskriterien

- Schutzregeln sind ohne Widerspruch festgelegt.
- Wahrheitsort fuer Identity-Sperrzustaende ist entschieden.
- Endpunkte, Request-Form, Erfolgsantworten und Fehlerfaelle sind fuer Implementierung ausreichend konkret.
- Aus dieser Subtask folgt direkt eine kleine, testgetriebene Server-Implementierung.

Fehlschlag

- Delete-Semantik unterscheidet sich fachlich von Selbstloeschung.
- Schutzregeln bleiben teilweise im Client statt serverseitig.
- API-Vertrag laesst mehrere inkompatible Implementierungen zu.
