Diese Subtask zieht den fachlichen und technischen Rahmen fuer Task 029 fest.

Ziel ist eine eindeutig implementierbare Moderationsschnittstelle fuer `Blockieren`, `Deblockieren` und `Nutzer loeschen`, bevor Server- und Client-Code angepasst werden.

Umfang

- Festlegen, welche Zielidentitaeten durch Admin-Moderation adressiert werden duerfen und welche geschuetzt sind.
- Festlegen, ob Sperrzustaende in der bestehenden dateibasierten Identity-Blocklist bleiben oder nach `mosa_db.sqlite` wechseln.
- Festlegen, ueber welche oeffentlichen API-Aktionen `Blockieren`, `Deblockieren` und `Nutzer loeschen` ausgeloest werden.
- Festlegen, welche Request- und Response-Daten sowie welche fachlichen Fehlerfaelle sichtbar sind.
- Festlegen, welche serverseitige Loeschlogik die Admin-Aktion `Nutzer loeschen` nutzt und wie die Zielidentitaet per `gid` adressiert wird.
- Falls neue shared DTOs noetig sind, deren minimalen Vertrag definieren.

Erfolgskriterien

- Schutzregeln fuer Selbstmoderation und Moderation anderer geschuetzter Konten sind eindeutig beschrieben.
- Es gibt genau einen entschiedenen Wahrheitsort fuer Identity-Sperrzustaende.
- Aus der Subtask geht klar hervor, welche serverseitigen TDD-Tests in der naechsten Subtask zuerst geschrieben werden muessen.
- Client- und Server-Implementierung koennen ohne weitere fachliche Rueckfragen beginnen.

Fehlschlag

- Schutzregeln bleiben offen oder widerspruechlich.
- Speicherort fuer Sperrzustaende bleibt unentschieden.
- API-Vertrag bleibt so vage, dass mehrere inkompatible Implementierungen moeglich waeren.
