Diese Subtask implementiert die serverseitigen Moderationsaktionen fuer Nutzeridentitaeten.

Ziel ist, dass Admins ueber oeffentliche API-Aktionen Identitaeten blockieren, deblockieren und loeschen koennen, waehrend Nicht-Admins und geschuetzte Faelle serverseitig korrekt abgewiesen werden.

Pflichtvorgehen

1. Fuer jeden kleinen Verhaltensschritt zuerst minimalen fehlschlagenden Test ueber oeffentliches Verhalten schreiben.
2. Minimalen Produktionscode ergaenzen, bis Test besteht.
3. Produktions- und Testcode ohne Verhaltensaenderung aufraeumen.

Umfang

- Servertests fuer `Blockieren`, `Deblockieren` und `Nutzer loeschen` ueber oeffentliche Endpunkte oder Controller-Flows ergaenzen.
- Auth- und Admin-Pruefung fuer alle Moderationsaktionen abdecken.
- Schutzregeln fuer unzulaessige Zielidentitaeten serverseitig durchsetzen.
- Gewaehlten Wahrheitsort fuer Identity-Sperrzustaende anbinden.
- Admin-Delete auf dieselbe fachliche Loeschlogik wie Selbst-Loeschung fuehren, aber mit expliziter Ziel-`gid`.
- Fachliche Fehlerfaelle fuer bereits blockierte, nicht blockierte, unbekannte, geschuetzte oder bereits geloeschte Ziele sauber abbilden.
- Falls noetig Onlinehilfe oder Laufzeit-Artefakt-Doku fuer den gewaehlten Sperrzustand anpassen.

Erfolgskriterien

- Automatisierte Tests zeigen erfolgreiche Blockier-, Deblockier- und Loeschfaelle fuer Admins.
- Automatisierte Tests zeigen Ablehnung fuer Nicht-Admins und geschuetzte Zielidentitaeten.
- Automatisierte Tests zeigen, dass Admin-Loeschen dieselben sichtbaren pod-lokalen Loeschfolgen hat wie Selbst-Loeschen.
- Server fuehrt keinen zweiten fachlich abweichenden Delete-Mechanismus ein.

Fehlschlag

- Tests pruefen nur interne Hilfsmethoden statt oeffentlichem Verhalten.
- Admin-Delete loescht andere Daten als Selbst-Loeschung.
- Sperrlogik hat mehr als einen technischen Wahrheitsort.
