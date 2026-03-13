Topics und Issues sollen Filter bekommen, mit denen sich Beitragslisten gezielter einschränken lassen.

Gefiltert werden soll im ersten Schritt nach Alter der Beiträge und nach Autorinnen bzw. Autoren. Beim Alter sollen die festen Zeitfenster `1d`, `7d`, `30d`, `1y` und `5y` verfügbar sein. Beim Autor-Filter sollen mehrere Autorinnen oder Autoren gleichzeitig auswählbar sein.

Die Aufgabe umfasst mindestens diese Punkte:

- In Topics und Issues soll es eine Filtermöglichkeit für das Alter von Beiträgen geben.
- Die auswählbaren Altersfilter sollen genau `1d`, `7d`, `30d`, `1y` und `5y` sein.
- In Topics und Issues soll es eine Filtermöglichkeit nach Autorinnen und Autoren geben.
- Beim Autor-Filter müssen mehrere Autorinnen oder Autoren gleichzeitig ausgewählt werden können.
- Mehrere aktive Filter müssen gemeinsam wirken, also insbesondere Altersfilter zusammen mit einem oder mehreren Autor-Filtern.
- Die Filterung muss serverseitig im Lucene-Query angewendet werden; eine reine Client-Filterung reicht nicht aus.
- Die Bedienung soll für Topics und Issues möglichst einheitlich sein.

Bei der Umsetzung ist zu klären:

- Welche Zeitbasis für den Altersfilter maßgeblich ist, insbesondere Zeitzone und genauer Vergleichszeitpunkt.
- Wie der Mehrfach-Author-Filter in der Oberfläche dargestellt wird, ohne die Listenansicht zu überladen.
- Ob die aktiven Filter im Zustand der Anwendung erhalten bleiben sollen, zum Beispiel bei Navigation oder Reload.
- Wie mit Beiträgen umgegangen wird, deren Autorinformation fehlt oder nicht mehr auflösbar ist.
