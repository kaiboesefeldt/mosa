Ziel dieser Subtask ist, `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPrivacyPolicyService.kt` entlang seiner unterschiedlichen Verantwortlichkeiten zu konsolidieren.

Ausgangsproblem:

- Die Datei vereint Pod-Zugriff, Sprachwahl, ClearTerms-Parsing, Profilvalidierung, Kettenanalyse, Highlight-Ermittlung, Fallback-Strategien und HTML-Rendering.
- Datenbeschaffung, fachliche Aufbereitung und praesentationnahe Renderlogik sind dadurch zu eng miteinander vermischt.

Erwartetes Ergebnis:

- IO-nahe Dokumentbeschaffung bleibt von ClearTerms-spezifischer Aufbereitung getrennt.
- Kettenanalyse und Ermittlung unkritischer Vergleichstexte sind von HTML-Rendering und Markup-Erzeugung entkoppelt.
- Die oeffentliche Funktion des Services bleibt unveraendert.
