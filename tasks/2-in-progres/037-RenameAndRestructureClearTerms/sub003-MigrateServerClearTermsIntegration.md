## Ziel

Die serverseitigen ClearTerms-Integrationspfade auf die in `sub001` definierte Zielstruktur umstellen.

## Umfang

- Produktionscode in `server/src/main/kotlin`
- zugehoerige Tests in `server/src/test/kotlin`
- serverseitige Helfer, YAML-bezogene Klassen und Legal-Integrationen

## Leitplanken

- Diese Subtask setzt die in `sub001` festgelegten Namen und Pakete konsistent in `server` um.
- Falls `server` eigene ClearTerms-Helfer in neue Unterpakete verschiebt, muss das zur in `shared` eingefuehrten Struktur passen.
- Anpassungen bleiben minimal und zielgerichtet; keine inhaltliche Erweiterung der Fachlogik.
- Die Aenderung muss mit den Ergebnissen aus `sub002` zusammenspielen, ohne diese zu konterkarieren.

## Erfolgskriterium

Serverseitige Legal- und ClearTerms-Pfade verwenden ausschliesslich die neue Benennung und Paketstruktur, und die betroffenen `server`-Tests bestaetigen die Migration.
