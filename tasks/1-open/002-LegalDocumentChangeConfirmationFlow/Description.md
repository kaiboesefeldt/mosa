Bei Änderungen an Rechtsdokumenten soll Mosa einen Bestätigungs-Flow haben. Nutzerinnen und Nutzer sollen neue Fassungen in Mosa sehen, in speicherbarer Form öffnen oder herunterladen können und die Änderungen ausdrücklich bestätigen, bevor die weitere Nutzung des Pods möglich ist.

Wichtig ist dabei, dass Mosa keine zusätzlichen personenbezogenen Kontaktdaten voraussetzt. Der Flow muss also vollständig in der Anwendung funktionieren und zum bestehenden Pseudonym-/Pod-Modell passen.

Der Produkt-Flow soll mindestens diese Punkte erfüllen:

- Jedes relevante Rechtsdokument braucht eine klar erkennbare Versionskennung.
- Der Client muss speichern, welcher Version des jeweiligen Dokuments die aktuell aktive Identität zuletzt zugestimmt hat.
- Wenn der Pod für ein Dokument eine neuere Version ausliefert als die lokal bestätigte, darf der normale Pod-Zugriff nicht einfach weiterlaufen.
- Stattdessen muss Mosa sofort einen Bestätigungs-Dialog oder eine vorgeschaltete Bestätigungsansicht zeigen.
- Dort muss die neue Fassung direkt lesbar sein.
- Dort muss die neue Fassung auch in speicherbarer Form bereitstehen, zum Beispiel als PDF oder als andere herunterladbare Fassung mit Versionsstand.
- Wenn möglich, soll zusätzlich ein verständlicher Änderungsvergleich zur vorherigen Fassung gezeigt werden.
- Die Zustimmung darf nicht stillschweigend erfolgen. Es braucht eine ausdrückliche Bestätigung durch die Nutzerin oder den Nutzer.
- Erst nach dieser ausdrücklichen Bestätigung darf die normale Nutzung des Pods weiter möglich sein.
- Wenn die neue Fassung nicht bestätigt wird, darf der Pod nicht weiter normal genutzt werden.
- Auch ohne Bestätigung müssen faire Ausstiegsfunktionen weiter erreichbar bleiben, insbesondere das Herunterladen oder Sichern eigener Daten.
- Der Flow muss so gebaut sein, dass nicht gleichzeitig ein Teil der Nutzer mit alter und ein anderer Teil mit neuer Fassung weiterarbeitet. Ein Versionswechsel gilt also podweit.
- Die gespeicherte Zustimmung muss identitätsbezogen sein, damit mehrere lokale Pseudonyme im selben Browser sauber getrennt behandelt werden.
- Ein bloßes Wegklicken oder Schließen des Dialogs darf nicht als Zustimmung gewertet werden.
- Der Zustand muss nach Browser-Neustart erhalten bleiben.

Technisch sollte daraus folgen:

- Der Pod braucht eine auslieferbare Dokumentversion pro Rechtsdokument.
- Eine mögliche Versionierungsstrategie wäre, jede neue Dokumentenversion als neues Profil abzubilden, das auf dem letzten Profil der jeweiligen Vorversion aufsetzt.
- Der Client braucht einen lokalen Speicher für bestätigte Dokumentversionen je Identität.
- Geschützte Pod-Funktionen müssen zentral gesperrt werden können, bis die nötigen Bestätigungen vorliegen.
- Export-, Download- oder Sicherungsfunktionen für eigene Daten müssen trotz offener Bestätigung gezielt weiter zugänglich bleiben.
