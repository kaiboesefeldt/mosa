# Rechtliche Notizen zu Mosa

## §§ 327 ff. BGB und Mosa

Stand dieser Einschätzung: 2026-03-12

### Vorläufiges Ergebnis

Nach derzeitigem Stand sprechen starke Gründe dafür, dass die §§ 327 ff. BGB auf Mosa eher nicht anwendbar sind.

Der Grund ist nicht, dass Mosa kostenlos ist, sondern dass Mosa nach heutigem Stand weder einen Preis verlangt noch personenbezogene Daten als Gegenleistung für andere als die zur Bereitstellung nötigen Zwecke verarbeitet.

### Ausgangspunkt im Gesetz

Die §§ 327 ff. BGB gelten für Verbraucherverträge über digitale Produkte grundsätzlich dann,

- wenn ein Preis gezahlt wird, oder
- wenn personenbezogene Daten bereitgestellt werden.

Das folgt aus [§ 327 Abs. 1 und 3 BGB](https://www.gesetze-im-internet.de/bgb/__327.html) in Verbindung mit [§ 312 Abs. 1a BGB](https://www.gesetze-im-internet.de/bgb/__312.html).

Wichtig ist aber die Ausnahme in § 312 Abs. 1a BGB:

- Die Sonderregeln greifen nicht, wenn die personenbezogenen Daten ausschließlich verarbeitet werden, um den digitalen Dienst bereitzustellen oder um rechtliche Pflichten zu erfüllen.
- Das gilt nur, wenn die Daten nicht zusätzlich für andere Zwecke verarbeitet werden.

### Bedeutung für Mosa

Für Mosa ist nach aktuellem Produkt- und Code-Stand relevant:

- Es gibt keine Zahlung.
- Es gibt keine Werbung.
- Es gibt kein Tracking oder Profiling zu anderen Zwecken.
- Es gibt keine Nutzung der Daten für Vermarktung, personalisierte Werbung oder sonstige Nebenzwecke.

Die verarbeiteten Daten dienen nach aktuellem Stand nur diesen Zwecken:

- Authentifizierung
- Bereitstellung der Pod-Funktionen
- Speicherung und Abruf von Inhalten
- Suche und Antwortzuordnung
- Missbrauchsschutz, Rate Limiting und Blocklisten
- Stabilität und Sicherheit des Systems
- Erfüllung rechtlicher Pflichten

Diese Zweckbeschreibung passt auch zum aktuellen Datenschutztext in [mosa-privacy-gdpr-service.de.yaml](/home/kai/Projects/straightway/mosa/server/src/main/resources/clearterms/privacy/mosa-privacy-gdpr-service.de.yaml).

### Bedeutung der Gesetzesbegründung

Die Gesetzesbegründung bestätigt diese enge Ausnahme.

Sie sagt sinngemäß:

- Werden personenbezogene Daten nur verarbeitet, um die Leistung zu erbringen oder gesetzliche Pflichten zu erfüllen, sollen die §§ 327 ff. BGB nicht greifen.
- Werden die Daten zusätzlich für andere Zwecke genutzt, etwa Werbung, dann greifen diese Regeln doch.

Quelle: [BT-Drs. 19/27653, S. 34 f.](https://dserver.bundestag.de/btd/19/276/1927653.pdf)

### Wichtige Unsicherheit

Für diese Frage ist nicht entscheidend, ob Mosa Klarnamen speichert.

Auch Pseudonyme, GIDs, Public Keys, IP-Adressen oder Nutzungsverläufe können rechtlich personenbezogene Daten sein. Dafür spricht die weite Definition in [Art. 4 Nr. 1 DSGVO](https://eur-lex.europa.eu/eli/reg/2016/679/art_4/par_1/oj).

Die entscheidende Frage ist deshalb nicht:

- ob Mosa personenbezogene Daten verarbeitet,

sondern:

- ob diese Daten ausschließlich für die Bereitstellung des Dienstes und für rechtliche Pflichten verarbeitet werden.

Nach derzeitigem Stand lautet die Antwort auf diese zweite Frage: ja.

### Praktische Folgerung

Für Mosa spricht deshalb derzeit vieles dafür, dass die besonderen Regeln für digitale Produkte in §§ 327 ff. BGB nicht einschlägig sind.

Trotzdem bleiben allgemeines Vertragsrecht und AGB-Recht relevant, vor allem:

- [§ 305 BGB](https://www.gesetze-im-internet.de/bgb/__305.html)
- [§ 305c BGB](https://www.gesetze-im-internet.de/bgb/__305c.html)
- [§ 307 BGB](https://www.gesetze-im-internet.de/bgb/__307.html)
- [§ 308 BGB](https://www.gesetze-im-internet.de/bgb/__308.html)

Das bedeutet:

- Auch wenn §§ 327 ff. BGB eher nicht gelten, müssen die AGB weiter klar, fair und nicht überraschend sein.

### Was diese Einschätzung kippen würde

Die Einschätzung müsste neu geprüft werden, wenn Mosa künftig zusätzlich Daten nutzt für:

- Werbung
- Tracking
- Profilbildung
- Empfehlungs- oder Ranking-Zwecke außerhalb der eigentlichen Suche
- Produktanalyse, die nicht mehr nur der Bereitstellung, Sicherheit oder Stabilität dient
- sonstige Nebenzwecke, die über die eigentliche Dienstleistung hinausgehen

### Arbeitsfazit

Diese Einordnung ist eine Arbeitsgrundlage für Mosa, keine abschließende Rechtsberatung.

Der aktuelle Stand ist:

- §§ 327 ff. BGB sind für Mosa eher nicht anwendbar.
- Der Hauptgrund dafür ist die enge Zweckbindung der verarbeiteten Daten.
- Sobald zusätzliche Datenzwecke hinzukommen, muss die Einschätzung neu geprüft werden.
