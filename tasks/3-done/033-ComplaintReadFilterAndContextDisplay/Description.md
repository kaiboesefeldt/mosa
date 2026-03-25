Beschwerden sollen im Client auch nach dem Lesen oder Bearbeiten erhalten bleiben und mit mehr Kontext angezeigt werden.

Aktuell verschwinden gelesene oder bearbeitete Beschwerden aus der sichtbaren Liste, und die Anzeige enthaelt zu wenig Kontext ueber den betroffenen Beitrag. Dadurch wird die Moderation unnoetig schwer, weil bearbeitete Faelle nicht mehr schnell nachpruefbar sind und die Beschwerde ohne den referenzierten Inhalt fachlich nur eingeschraenkt beurteilbar bleibt.

Ziel ist eine uebersichtliche Beschwerden-Ansicht mit lokalem Verlauf und ausreichendem Inhaltskontext. Bearbeitete Beschwerden sollen standardmaessig ausgeblendet, aber per Filterknopf wieder einblendbar sein. Zusaetzlich soll jede Beschwerde den betroffenen Inhalt, dessen Autor mit Avatar und FictionalName sowie das Empfangsdatum des betroffenen Beitrags anzeigen. Bereits lokal bekannte Beschwerden sollen im Browser-Speicher erhalten bleiben, auch wenn sie spaeter serverseitig geloescht wurden. Der UI-Text `Beitrag approven` soll in `Beschwerde abweisen` umbenannt werden; die englische Fassung soll sinngleich angepasst werden.

Die Aufgabe umfasst mindestens diese Punkte:

- Gelesene oder bearbeitete Beschwerden bleiben im lokalen Browser-Zustand erhalten.
- Es gibt einen Filterknopf, mit dem bearbeitete Beschwerden ein- und ausgeblendet werden koennen.
- Der Default-Zustand blendet bearbeitete Beschwerden aus.
- Bereits lokal bekannte Beschwerden bleiben im Browser-Speicher erhalten, auch wenn sie spaeter serverseitig geloescht wurden.
- Bei einer Beschwerde ueber einen Post wird der Text dieses Posts angezeigt.
- Bei einer Beschwerde ueber einen Reply werden der Text des zugehoerigen Posts und der Reply-Text angezeigt.
- Der Autor des betroffenen Beitrags wird mit Avatar und FictionalName angezeigt.
- Das Empfangsdatum des betroffenen Beitrags wird angezeigt.
- Der UI-Text `Beitrag approven` wird durch `Beschwerde abweisen` ersetzt; die englische Fassung wird entsprechend angepasst.

Akzeptanzkriterien:

- Eine bearbeitete Beschwerde verschwindet nicht dauerhaft aus dem Client, sondern kann ueber den Filter wieder eingeblendet werden.
- Beim ersten Oeffnen der Beschwerden-Ansicht sind bearbeitete Beschwerden standardmaessig ausgeblendet.
- Eine bereits lokal bekannte Beschwerde bleibt nach serverseitiger Loeschung im Browser-Speicher und in der Client-Ansicht verfuegbar.
- Beschwerden ueber Posts und Replies zeigen den jeweils geforderten inhaltlichen Kontext vollstaendig an.
- Die Beschwerdeansicht zeigt Autor, Avatar, FictionalName und Empfangsdatum des betroffenen Beitrags.
- Die aktualisierten UI-Texte sind in den betroffenen Sprachfassungen sichtbar.

Bei der Umsetzung ist insbesondere zu klaeren:

- Woran fachlich `bearbeitet` fuer Beschwerden genau erkannt wird und ob dieser Zustand rein lokal, serverseitig oder kombiniert bestimmt wird.
- Wie der betroffene Beitrag fuer geloeschte oder lokal zwischengespeicherte Beschwerden robust rekonstruiert werden kann.
- Wie fuer Replies der Post-Text und der Reply-Text ohne unklare Reihenfolge oder doppelte Datenbeschaffung angezeigt werden sollen.
