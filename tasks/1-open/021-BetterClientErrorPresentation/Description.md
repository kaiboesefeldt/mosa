Der Client braucht eine deutlich bessere Darstellung von Fehlern gegenueber Nutzerinnen und Nutzern.

Aktuell werden Server- und Netzwerkfehler im UI nur unzureichend oder zu technisch sichtbar. Das ist besonders problematisch bei haeufigen oder wichtigen Faellen wie `503 Service Unavailable` und `422 Unprocessable Entity`, aber auch bei sonstigen Fehlern wie `400`, `401`, `403`, `404`, `5xx` oder Verbindungsproblemen ohne HTTP-Response.

Ziel ist eine pragmatische, konsistente und nutzerverstaendliche Fehlerbehandlung im Client, ohne dass dabei relevante fachliche Unterschiede zwischen Fehlerarten verlorengehen.

Die Aufgabe umfasst mindestens diese Punkte:

- Der Client soll wichtige Fehlerfaelle sichtbar und verstaendlich anzeigen, statt nur generische oder technische Meldungen zu zeigen.
- `503` soll als temporaeres Server- oder Betriebsproblem erkennbar sein.
- `422` soll als fachlicher oder validierungsnaher Fehler erkennbar sein, soweit der konkrete Kontext das sinnvoll macht.
- Auch sonstige HTTP-Fehler sollen sinnvoll gruppiert oder differenziert angezeigt werden.
- Fehler ohne gueltige HTTP-Antwort, zum Beispiel Netzwerkausfaelle oder Timeouts, muessen gesondert beruecksichtigt werden.
- Die Darstellung soll im bestehenden UI konsistent sein und nicht pro Feature komplett anders ausfallen.
- Es muss klar sein, an welchen Stellen Rohfehler geloggt oder intern erhalten bleiben und an welchen Stellen nur nutzergeeignete Meldungen angezeigt werden.

Bei der Umsetzung ist zu klaeren:

- Wo im Client die Fehlerabbildung zentralisiert werden soll, damit nicht jede View eigene Ad-hoc-Meldungen baut.
- Welche Fehlertypen oder Statuscodes explizit eigene Texte bekommen und welche bewusst in Sammelkategorien fallen.
- Wie serverseitige Fehlertexte genutzt werden duerfen, ohne unkontrolliert technische Interna direkt ins UI zu spiegeln.
- Ob sich die Darstellung je nach Aktion unterscheiden muss, zum Beispiel Publish, Reply, Login, Inbox oder Admin-Funktionen.
- Wie Retry-Hinweise oder konkrete naechste Schritte fuer temporaere Fehler sinnvoll angezeigt werden.
- Wie Internationalisierung fuer die neuen Fehlermeldungen sauber eingebunden wird.

Akzeptanzkriterien:

- Fuer `503` und `422` gibt es im Client bewusst gestaltete, vom Nutzer verstehbare Fehlermeldungen.
- Fuer weitere haeufige Fehlerklassen existiert eine nachvollziehbare und konsistente Strategie statt verteilter Einzelloesungen.
- Netzwerkfehler ohne HTTP-Status werden nicht mit normalen Serverfehlern verwechselt.
- Die Fehlerdarstellung ist so zentralisiert, dass neue API-Aufrufe sie mit vertretbarem Aufwand wiederverwenden koennen.
- Die Loesung bleibt einfach und vermeidet ein ueberkomplexes Exception-Framework im Client.
