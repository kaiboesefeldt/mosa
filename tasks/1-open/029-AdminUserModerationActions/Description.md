Admins sollen andere Nutzer ueber die bestehende Admin-Aktionsflaeche sperren, entsperren oder loeschen koennen.

Aktuell gibt es bereits userbezogene Admin-Aktionen fuer Admin-Nachrichten, aber keine direkte Moderationsfunktion fuer den Zielnutzer selbst. Dadurch fehlt ein klarer, zusammenhaengender Pfad fuer typische Betreiberentscheidungen wie das Blockieren problematischer Identitaeten, das spaetere Aufheben einer Sperre oder das gezielte Entfernen eines Nutzerkontos aus dem Pod-Kontext.

Ziel ist ein kleiner, klarer Moderationssatz direkt an der bestehenden Admin-Aktionsflaeche fuer einen Nutzer. Im ersten Wurf geht es um genau drei Admin-Aktionen: Nutzer blockieren, Nutzer deblockieren und Nutzer loeschen. Die Aufgabe soll dabei ausdruecklich mitpruefen, ob die bestehende dateibasierte Identity-Blocklist dafuer noch passend ist oder ob diese Sperrzustaende besser in der bereits eingefuehrten SQLite-basierten strukturierten Persistenz liegen sollten.

Die Aufgabe umfasst mindestens diese Punkte:

- Ein Admin kann fuer einen dargestellten fremden Nutzer eine Blockieraktion ausloesen.
- Ein Admin kann fuer einen bereits blockierten Nutzer eine Deblockieraktion ausloesen.
- Ein Admin kann fuer einen dargestellten fremden Nutzer eine Loeschaktion ausloesen.
- Die Aktionen sind klar an der bestehenden userbezogenen Admin-Aktionsflaeche verortet und fuehren keinen zweiten fachlich abweichenden Moderationspfad ein.
- Blockier- und Deblockierzustand wirken serverseitig ueber denselben fachlichen Identitaets-Sperrmechanismus, statt parallele Sonderregeln einzufuehren.
- Im Rahmen der Aufgabe wird explizit entschieden, ob die Identity-Blocklist dateibasiert bleibt oder in `mosa_db.sqlite` ueberfuehrt wird.
- Falls die Identity-Blocklist in SQLite ueberfuehrt wird, wird die Onlinehilfe fuer Laufzeit-Artefakte entsprechend angepasst.

Akzeptanzkriterien:

- Ein Admin sieht fuer fremde Nutzer in der Admin-Aktionsflaeche die Moderationsaktionen `Blockieren`, `Deblockieren` und `Loeschen`, soweit sie fuer den aktuellen Zustand sinnvoll sind.
- Eine blockierte Identitaet ist nach der Aktion serverseitig als blockiert wirksam.
- Eine deblockierte Identitaet ist nach der Aktion serverseitig nicht mehr blockiert.
- Nicht-Admins koennen diese Moderationsaktionen weder im UI regulaer ausloesen noch serverseitig erfolgreich verwenden.
- Die Nutzerloeschung entfernt genau den vorgesehenen Nutzerbezug und fuehrt nicht dazu, dass andere Nutzer unbeabsichtigt betroffen sind.
- Die Loesung verwendet genau einen klaren technischen Wahrheitsort fuer Identity-Sperrzustaende.

Bei der Umsetzung ist insbesondere zu klaeren:

- Was `Nutzer loeschen` im Mosa-Kontext fachlich exakt bedeutet, insbesondere in Bezug auf Identitaet, Inhalte, Admin-Nachrichten und moegliche verbleibende Referenzen.
- Ob eine Nutzerloeschung die Inhalte der Identitaet mit entfernt, nur die lokale Nutzbarkeit kappt oder beides in einer definierten Reihenfolge ausfuehrt.
- Ob die Identity-Blocklist fuer diese Admin-Aktionen noch sinnvoll dateibasiert aktualisiert werden kann oder ob sie als strukturierter Moderationszustand in SQLite besser aufgehoben ist.
- Wie verhindert wird, dass Admins sich selbst oder andere besonders geschuetzte Betreiberkonten versehentlich blockieren oder loeschen.
- Welche Bestaetigungsdialoge oder Sicherheitsabfragen fuer Blockieren, Deblockieren und Loeschen noetig sind.
- Wie die UI den aktuellen Zustand eines Nutzers sichtbar macht, damit `Blockieren` und `Deblockieren` nicht verwechselt werden.
