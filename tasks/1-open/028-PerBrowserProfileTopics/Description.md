Jedes Nutzer-Profil in einem Browser soll seinen eigenen Satz an Topics haben.

Aktuell ist davon auszugehen, dass Topics im Browser mindestens teilweise global pro Browserzustand gehalten werden und dadurch zwischen verschiedenen lokalen Nutzer-Profilen vermischt werden koennen. Das fuehrt dazu, dass ein Profil Topics sieht oder weiterverwendet, die eigentlich zu einem anderen Profil im selben Browser gehoeren.

Ziel ist eine klare Trennung des Topic-Satzes pro Nutzer-Profil innerhalb desselben Browsers. Wenn in einem Browser mehrere lokale Profile verwendet werden, muss jedes Profil nur seine eigenen Topics sehen, verwalten und wiederfinden.

Die Aufgabe umfasst mindestens diese Punkte:

- Topics werden im Client-Zustand und in browserseitiger Persistenz pro Nutzer-Profil getrennt gehalten.
- Ein Profilwechsel im selben Browser zeigt nur den zu diesem Profil gehoerenden Topic-Satz.
- Aktionen auf Topics eines Profils wirken sich nicht auf Topics anderer Profile im selben Browser aus.
- Die Trennung gilt auch nach Reload oder Neustart des Browsers weiter, soweit Topics bereits browserseitig persistiert werden.
- Bestehende Flows fuer Topic-Anzeige, Topic-Auswahl und topic-bezogene Navigation bleiben funktional konsistent.

Akzeptanzkriterien:

- Wenn zwei verschiedene Nutzer-Profile im selben Browser verwendet werden, hat jedes Profil einen eigenen Topic-Bestand.
- Ein in Profil A sichtbares oder angelegtes Topic erscheint nicht allein durch die gemeinsame Browser-Instanz in Profil B.
- Nach einem Reload bleibt die Zuordnung der Topics zum jeweiligen Profil erhalten.
- Der Wechsel zwischen Profilen fuehrt nicht zu Vermischung, Ueberschreiben oder Verlust des Topic-Satzes eines anderen Profils.

Bei der Umsetzung ist insbesondere zu klaeren:

- Woran ein lokales Nutzer-Profil im Client eindeutig identifiziert wird.
- Welche bestehenden Topic-Daten aktuell global im Browser gespeichert oder gecacht werden.
- Ob und wie vorhandene global gespeicherte Topic-Daten in eine profilgebundene Struktur migriert werden muessen.
- Wie mit Alt-Daten umgegangen wird, wenn sich ein Profil nicht eindeutig rekonstruieren laesst.
