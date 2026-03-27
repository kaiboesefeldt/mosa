## Ziel

Die in `shared` liegenden ClearTerms-Typen gemaess dem in `sub001` festgelegten Zielbild umbenennen und in ihre neuen Unterpakete verschieben.

## Umfang

- Produktionscode in `shared/src/commonMain/kotlin`
- zugehoerige Tests in `shared`
- notwendige Anpassungen an Imports, Dateinamen und Paketdeklarationen

## Leitplanken

- Die Umsetzung folgt strikt dem in `sub001` verabschiedeten Namens- und Paketvorschlag.
- Die Aenderung erfolgt in kleinen, testgetriebenen Schritten.
- Das Wurzelpaket `straightway.mosa.legal.clearterms` soll nur Typen behalten, die im Zielbild ausdruecklich dort vorgesehen sind.
- Zwischenstaende muessen kompilierbar bleiben.

## Erfolgskriterium

Alle betroffenen ClearTerms-Typen in `shared` tragen ihre neuen Namen, liegen in den vorgesehenen Paketen und die `shared`-Tests laufen gruendlich gegen die neue Struktur.
