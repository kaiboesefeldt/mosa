Die nach Task 035 neu geschnittene `IdentityConnectedView`-Struktur ist grob entkoppelt, traegt aber weiterhin sehr lange Parameterlisten zwischen Kompositions-, Header-, Board- und Overlay-Funktionen sowie zwischen den extrahierten Controllern und UI-Bausteinen.

Ziel

Die Parameterlisten in diesem Bereich sollen deutlich verkuerzt und auf klar benannte Uebergabeobjekte oder schmale Schnittstellen reduziert werden, ohne das Verhalten zu aendern.

Leitplanken

- Keine neue Fachlogik.
- Keine Rueckverlagerung grosser Verantwortlichkeiten in die Hauptdatei.
- Keine implizite Globalkopplung nur zum Sparen einzelner Parameter.
- Bevorzugt werden kleine, fachlich benannte Datenklassen oder Interfaces fuer zusammengehoerige Props.
- Koin ist nur dann sinnvoll, wenn dadurch keine versteckte UI-Abhaengigkeit entsteht.

Erfolgskriterien

- Die auffaellig langen Parameterlisten in `IdentityConnectedView` und den neuen Helferdateien sind sichtbar reduziert.
- Die neuen Uebergabeobjekte bleiben fachlich klar und nicht generisch-schwammig.
- `./gradlew check` bleibt erfolgreich.
