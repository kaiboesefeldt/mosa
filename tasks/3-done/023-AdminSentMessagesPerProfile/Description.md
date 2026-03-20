In der Admin-Ansicht soll fuer das jeweils betrachtete Profil sichtbar werden, welche Admin-Nachrichten Admin-Konten diesem Profil bisher gesendet haben.

Aktuell koennen Admins Nachrichten an Nutzer senden, und normale Nutzer koennen ihre eigene Inbox abrufen. Fuer Admins fehlt jedoch eine profilbezogene Verlaufssicht der bereits gesendeten Nachrichten. Dadurch ist in der Admin-Ansicht nicht erkennbar, ob ein bestimmtes Profil schon kontaktiert wurde, wann zuletzt geschrieben wurde und mit welchem Inhalt eine fruehere Admin-Nachricht verschickt wurde.

Ziel ist eine einfache, profilgebundene Verlaufssicht der von Admin-Konten gesendeten Nachrichten an genau dieses Profil. Da die Admin-Konten hier fachlich kooperativ betrachtet werden, soll die Ansicht nicht nur die Nachrichten des aktuell eingeloggten Admins zeigen, sondern den gemeinsamen Admin-Verlauf fuer dieses Profil. Im ersten Wurf geht es um Lesbarkeit und Orientierung in der Admin-Ansicht, nicht um Bearbeiten, Rueckruf, Antwortfunktion oder einen vollwertigen Chat.

Die Aufgabe umfasst mindestens diese Punkte:

- Die Admin-Ansicht eines Profils zeigt alle bisher an dieses Profil gesendeten Admin-Nachrichten an, solange sie serverseitig noch vorhanden sind.
- Die Liste ist klar als von Admins gesendeter Verlauf fuer genau dieses Profil erkennbar.
- Jede Nachricht zeigt mindestens Absender, Versandzeitpunkt, Betreff, Nachrichtentext und die verbleibende oder absolute TTL (TTL ist entsprechend auch für Laien lesbar auszudrücken, nicht "TTL" im UI verwenden!)
- Die Reihenfolge der angezeigten Nachrichten ist absteigend nach Versandzeitpunkt.
- Der fuer diese Verlaufssicht genutzte Endpoint `GET /admin/messages` liefert die Nachrichten in genau dieser Sortierung gepaged aus.
- Die Paging-Groesse ist serverseitig fest vorgegeben (10) und nicht frei vom Client konfigurierbar. Die Nachrichten werden smart nachgeladen, wenn beim Scrollen die Paginggrenze erreicht wird.
- Gepagte Listenresponses geben die ausgelieferte Seitennummer und ein boolesches Flag zurueck, das angibt, ob weitere Seiten verfuegbar sind.
- Die Daten duerfen nur fuer authentifizierte Admin-Konten sichtbar sein.
- Die Anzeige soll auf der bestehenden Admin-Nachrichtenfunktion aufsetzen und keinen zweiten fachlich abweichenden Nachrichtenkanal einfuehren.
- Der bestehende Endpoint `GET /messages`, der aktuell ungepagte Inbox-Daten liefert, soll im Zuge dieser Aufgabe ebenfalls auf Paging umgestellt werden. Auch die von diesem Endpoint im Client empfangenen Nachrichten werden smart nachgeladen, wenn beim Scrollen die Paginggrenze erreicht wird.
- Der neue oder erweiterte Endpoint `GET /admin/messages` ist ausschliesslich fuer die Admin-Verlaufssicht zustaendig.
- Das Lesen ueber die Admin-Verlaufssicht darf keine TTL-Aenderung oder sonstige Abrufnebenwirkung fuer das Empfaengerkonto ausloesen.
- Die verbleibende Gueltigkeit jeder Nachricht wird in der Admin-Ansicht als fuer Laien verstaendliche Restlaufzeit in Tagen angezeigt.
- Wenn fuer ein Profil noch keine Admin-Nachricht existiert, zeigt die Admin-Ansicht einen klaren leeren Zustand statt einer missverstaendlichen oder leeren Flaeche.

Akzeptanzkriterien:

- Ein Admin kann in der Admin-Ansicht eines Profils alle bisher an dieses Profil gesendeten Admin-Nachrichten aller Admin-Konten sehen, soweit sie serverseitig noch vorhanden sind.
- Die angezeigten Nachrichten gehoeren ausschliesslich zum aktuell betrachteten Profil.
- Versandzeitpunkt, Betreff, Text und TTL-Information jeder angezeigten Nachricht sind sichtbar.
- Die Nachrichten werden in einer konsistenten, fachlich nachvollziehbaren Reihenfolge dargestellt.
- Die Verlaufsdaten werden seitenweise ueber `GET /admin/messages` mit fester Page-Groesse geladen.
- Die Responses der gepagten Listen enthalten die ausgelieferte Seitennummer und ein Flag fuer weitere verfuegbare Seiten.
- Nicht-Admins koennen diese Verlaufssicht nicht abrufen oder sehen.
- Ein Profil ohne bisherige Admin-Nachrichten zeigt einen eindeutigen Leerzustand.
- `GET /messages` verwendet nach der Aenderung ebenfalls Paging statt einer ungepagten Gesamtliste.
- `GET /admin/messages` liefert den Admin-Verlauf getrennt von der Empfaenger-Inbox.
- Das Lesen des Admin-Verlaufs veraendert nicht die TTL oder andere Lifecycle-Daten der betroffenen Nachricht.
- Die bestehende Funktion zum Senden von Admin-Nachrichten bleibt erhalten und fuehrt weiterhin dazu, dass neu gesendete Nachrichten in diesem Verlauf erscheinen.

Bei der Umsetzung ist insbesondere zu klaeren:

- Wie die bestehende private Nachrichtenspeicherung effizient fuer eine Admin-Verlaufssicht nach Empfaenger abgefragt werden kann oder ob der API-Vertrag dafuer erweitert werden muss.
- Wie `GET /messages` fuer die Nutzer-Inbox und `GET /admin/messages` fuer den Admin-Verlauf technisch so getrennt werden, dass Admin-Lesen keine TTL-Verkuerzung ausloest, Nutzer-Abruf aber seine bisherige Lifecycle-Semantik behaelt.
