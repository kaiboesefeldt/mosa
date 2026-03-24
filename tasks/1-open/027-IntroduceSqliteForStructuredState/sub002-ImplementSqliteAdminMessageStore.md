Die bestehende JSON-basierte Persistenz fuer Admin-Nachrichten wird auf die neue SQLite-Grundlage umgestellt.

Ziel:

- Den bisherigen `FileBackedAdminMessageStore` fachlich durch einen SQLite-basierten Store ersetzen.
- Das Verhalten der bestehenden Admin-Message-Flows unveraendert erhalten.
- Die bisherige JSON-Datei `mosa_admin_messages.json` nicht mehr als produktiven Primarspeicher verwenden.

Verpflichtende Umsetzungsreihenfolge:

1. Zuerst ueber oeffentliches Verhalten einen minimalen fehlschlagenden Test fuer die neue Persistenz schreiben.
2. Dann die minimale Produktionsaenderung fuer einen grünen Test implementieren.
3. Danach schrittweise weitere Tests fuer die verbleibenden Verhaltenspfade ergänzen.

Mindestens abzudecken:

- Append neuer Admin-Nachrichten in SQLite.
- Laden sichtbarer Nachrichten fuer einen Empfaenger.
- Atomare TTL-Verkuerzung beim Inbox-Abruf.
- Kein TTL-Shortening beim Admin-Verlauf.
- Loeschen abgelaufener Nachrichten.
- Weiterhin identischer API-Vertrag fuer Inbox, Verlauf, Paging und Berechtigungen.
- Eine vorhandene `mosa_admin_messages.json` wird von der neuen Persistenz nicht ausgewertet.

Erwartetes Ergebnis:

- Die produktive Verdrahtung nutzt den neuen SQLite-Store.
- Bestehende Admin-Message-Service- und Integrationspfade bleiben fachlich grün.
- Die neue Persistenz ist ueber Tests robust beschrieben.
