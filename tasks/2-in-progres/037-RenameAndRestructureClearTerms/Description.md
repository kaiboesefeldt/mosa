# ClearTerms-Klassennamen kuerzen und Pakete neu schneiden

## Ausgangslage

Im Paket `straightway.mosa.legal.clearterms` tragen sehr viele Klassen das Praefix `ClearTerms`, obwohl der Paketkontext bereits sagt, dass es sich um ClearTerms-Code handelt. Das macht Signaturen, Imports und Testcode schwerer lesbar als noetig.

Zugleich liegen noch viele fachlich unterschiedliche Typen direkt im Wurzelpaket `clearterms`, obwohl fuer Teilbereiche bereits Unterpakete wie `canonical`, `locale`, `parse`, `placeholder`, `render` und `validation` existieren.

## Ziel

Die ClearTerms-Struktur soll so vereinfacht werden, dass:

- Klassennamen innerhalb von `clearterms` das redundante Praefix `ClearTerms` in der Regel nicht mehr tragen.
- Klassen in fachlich passende Unterpakete verschoben werden.
- Die Umbenennung und Paketbereinigung in allen betroffenen Gradle-Modulen konsistent durchgezogen wird: `shared`, `server` und `client`, jeweils inklusive ihrer Tests.
- Das Wurzelpaket `straightway.mosa.legal.clearterms` nur noch die wirklich zentralen Einstiegspunkte oder bewusst allgemein sichtbaren Domaintypen enthaelt, idealerweise moeglichst wenige.
- Die neue Struktur konsistent genug ist, dass kuenftige Typen ohne neue Namensdiskussionen eingeordnet werden koennen.

## Benennungskonzept

- Innerhalb des Paketbaums `straightway.mosa.legal.clearterms...` wird das Praefix `ClearTerms` grundsaetzlich entfernt.
- Der Paketname traegt den Kontext, der Klassenname beschreibt nur noch die konkrete Rolle.
- Klassen duerfen nicht auf unpraezise Allerweltsnamen verkuerzt werden. Wenn ein Name ohne Praefix zu generisch waere, soll zuerst das Zielpaket praezisiert werden, nicht der Name kuenstlich verlaengert werden.
- Parser, Renderer, Validatoren, Loader und Repositorys folgen jeweils derselben schlichten Benennung ohne Modul-Praefix.
- Datenklassen, Enums und Value-Objekte werden ebenfalls ohne Praefix benannt, sofern ihr Paket klar genug ist.
- Falls ein Typ bewusst ausserhalb von `clearterms` sehr breit verwendet wird und sein kurzer Name dort kollidiert, ist eine gezielte Ausnahme zulaessig. Solche Ausnahmen muessen explizit begruendet und selten bleiben.

## Betroffene Gradle-Module

- `shared`: enthaelt den Kern des ClearTerms-Modells und den Grossteil der Pakete unter `straightway.mosa.legal.clearterms`.
- `server`: enthaelt serverseitige ClearTerms-Helfer und Integrationspfade wie YAML-Konvertierung, Fragment-Merging und Legal-Services.
- `client`: verwendet ClearTerms-Typen und -Pakete direkt fuer Rendering, Parsing und Aufbereitung der Rechtstexte im Frontend.

## Randbedingungen

- Die Aufgabe umfasst die drei betroffenen Gradle-Module `shared`, `server` und `client` samt ihrer Tests; die Struktur darf nicht nur lokal in einem einzelnen Modul verbessert werden.
- Bestehende Paketgrenzen zwischen `shared` und `server` bleiben erhalten.
- Die neue Struktur soll keine zirkulaeren Abhaengigkeiten zwischen Unterpaketen erzeugen.
- Oeffentliche APIs, Tests, Ressourcenladepfade und Serverintegration muessen nach der Umbenennung konsistent bleiben.
- Die Umsetzung soll in kleinen, testgetriebenen Schritten erfolgen. Sinnvolle Zwischenetappen sind erlaubt, solange jede Etappe kompilierbar und verifizierbar bleibt.
- Vor dem Abschluss ist `./gradlew check` auszufuehren.

## Offene Klaerungen innerhalb der Aufgabe

- Welche konkreten Kurzformen fuer die heutigen `ClearTerms*`-Typen sinnvoll, eindeutig und im Code lesbar sind.
- Welche Unterpakete benoetigt werden und welche Typen jeweils dorthin gehoeren.
- Welche wenigen Typen gegebenenfalls im Wurzelpaket `clearterms` verbleiben sollen.
- Wie serverseitige und clientseitige Integrationsklassen in dieselbe Benennungslogik eingebunden werden.
