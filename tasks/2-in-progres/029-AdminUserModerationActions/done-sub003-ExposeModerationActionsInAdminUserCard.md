Diese Subtask bindet die neuen Moderationsaktionen in die bestehende Admin-Aktionsflaeche fuer Nutzer ein.

Ziel ist, dass Admins den aktuellen Moderationszustand eines dargestellten fremden Nutzers erkennen und die passende Aktion direkt aus derselben Oberflaeche ausloesen koennen, in der heute bereits Admin-Nachrichten liegen.

Umfang

- Client-seitige Modelle oder Services fuer die neuen Moderationsaktionen minimal erweitern.
- In der bestehenden Admin-Aktionsflaeche nur die fuer den aktuellen Zustand sinnvollen Aktionen anzeigen.
- Sichtbar machen, ob Zielidentitaet aktuell blockiert ist.
- Sicherheitsabfragen oder Bestaetigungsdialoge gemaess in `sub001` festgelegter Regeln einbauen.
- Fehler- und Busy-Zustaende fuer Moderationsaktionen benutzbar anzeigen.
- Sicherstellen, dass eigene Identitaet oder andere geschuetzte Ziele im UI nicht regulaer moderiert werden koennen, soweit dies aus vorhandenen Daten ableitbar ist.
- Vorhandene Admin-Nachrichtenfunktion in derselben Flaeche funktional intakt halten.

Erfolgskriterien

- Client-Tests zeigen, dass `Blockieren`, `Deblockieren` und `Loeschen` nur im passenden Zustand angeboten werden.
- Client-Tests zeigen, dass der aktuelle Blockstatus erkennbar ist.
- Client-Tests zeigen, dass erfolgreiche Aktionen den sichtbaren Zustand konsistent aktualisieren.
- Vorhandene Flows fuer Admin-Nachrichten bleiben unveraendert benutzbar.

Fehlschlag

- UI bietet gleichzeitig `Blockieren` und `Deblockieren` an.
- Zustand nach erfolgreicher Moderationsaktion bleibt unklar oder veraltet.
- Neue Moderationssteuerung bricht bestehende Admin-Nachrichtenfluesse.
