## Ziel

Die Gesamtintegration der ClearTerms-Umbenennung ueber alle betroffenen Gradle-Module verifizieren.

## Art des Abschlusstests

Vollautomatisierter Repository-Check.

## Vorbedingungen

- `sub001` ist abgeschlossen und liefert den verbindlichen Zielvorschlag.
- Die Umsetzungs-Subtasks fuer `shared`, `server` und `client` sind abgeschlossen.
- Das Repository ist in einem Zustand, in dem die geaenderten Module gemeinsam gebaut und getestet werden koennen.

## Durchfuehrung

1. Pruefen, dass keine alten `ClearTerms*`-Typnamen oder alten Paketpfade unbeabsichtigt in produktivem Code oder Tests zurueckgeblieben sind.
2. `./gradlew check` im Repository-Wurzelverzeichnis ausfuehren.
3. Falls der Check fehlschlaegt, die Ursache einem betroffenen Modul oder Integrationspfad zuordnen und beheben.
4. Den Check erneut ausfuehren, bis ein gruener Gesamtlauf vorliegt.

## Erfolgskriterium

- Die Umbenennung ist ueber `shared`, `server` und `client` konsistent integriert.
- `./gradlew check` laeuft erfolgreich durch.
- Verbleibende bewusste Ausnahmen von der Benennungsregel sind dokumentiert.
