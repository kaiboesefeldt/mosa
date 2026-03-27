## Ziel

Die clientseitige Nutzung von ClearTerms auf die in `sub001` definierte Zielstruktur umstellen.

## Umfang

- Produktionscode in `client/src/jsMain/kotlin`
- zugehoerige Tests in `client/src/jsTest/kotlin`
- insbesondere Rendering-, Parsing- und Aufbereitungspfade fuer Rechtstexte

## Leitplanken

- Diese Subtask uebernimmt die in `sub001` festgelegten Namen und Zielpakete unverwaessert in `client`.
- Die Migration bleibt auf Benennung, Paketstruktur und die dafuer noetigen Import- und Aufrufanpassungen begrenzt.
- UI-bezogene Nutzung von ClearTerms-Typen soll nach der Umstellung lesbarer sein als zuvor.
- Die Aenderung muss mit den Ergebnissen aus `sub002` und `sub003` konsistent bleiben.

## Erfolgskriterium

Der `client` verwendet nur noch die neue ClearTerms-Benennung und Paketstruktur, und die betroffenen Client-Tests bestaetigen die Anpassung.
