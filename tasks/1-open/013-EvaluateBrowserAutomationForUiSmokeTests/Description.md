Ziel dieser Aufgabe ist, fuer MOSA einen belastbaren Weg zu bewerten, wie manuelle UI-Smoke-Tests nach Frontend-Aenderungen weitgehend automatisiert werden koennen.

Ausloeser ist, dass rein manuelle Nachtests nach UI-Aenderungen unzuverlaessig, laestig und teuer sind. Gleichzeitig soll keine Loesung eingefuehrt werden, die unkontrolliert echte Browseraktionen gegen beliebige Seiten ausfuehren oder sicherheitskritische Daten exponieren kann.

Die Aufgabe ist zunaechst eine Evaluations- und Entscheidungsvorlage, noch keine verpflichtende Einfuehrung eines bestimmten Tools.

Zu beantworten sind mindestens diese Fragen:

- Welche frei verfuegbaren Open-Source-Werkzeuge sind fuer Browser-Automation und visuelle UI-Smoke-Tests in diesem Repo geeignet?
- Was laesst sich mit diesen Werkzeugen tatsaechlich absichern?
- Welche Luecken bleiben trotz Headless-Browser, DOM-Tests, Screenshots und visueller Vergleiche bestehen?
- Welche Risiken entstehen durch Browser-Automation selbst, insbesondere bei Fernsteuerung, Screenshot-Artefakten, Zugriff auf lokale Daten und moeglichen Fehlbedienungen?
- Unter welchen Sicherheitsgrenzen waere so eine Loesung fuer MOSA akzeptabel?

Zu betrachtende Werkzeugklassen:

- Browser-Automation fuer definierte UI-Flows, zum Beispiel Playwright oder Selenium.
- Bestehende Browser-nahe Testumgebungen im Repo, insbesondere die vorhandenen Kotlin/JS-Browser-Tests.
- Screenshot- und Visual-Regression-Verfahren, sofern sie Open Source und fuer einen pragmatischen Einsatz geeignet sind.
- Optional Browser-Fernsteuerung ueber MCP- oder DevTools-nahe Schnittstellen, falls das als weitergehende Option sinnvoll erscheint.

Fuer jede betrachtete Option soll mindestens beschrieben werden:

- Lizenz- und Open-Source-Status
- Integrationsaufwand im bestehenden Repo
- welche Arten von Tests moeglich sind:
  - DOM-basierte Verhaltenspruefung
  - echte Klick-/Tipp-Flows
  - Screenshot-Erzeugung
  - Visual Regression
  - interaktive Browser-Fernsteuerung durch die KI
- welche Arten von Fehlern damit gut erkannt werden
- welche Arten von Fehlern damit eher nicht erkannt werden
- wie stabil und wartbar die Loesung im Alltag voraussichtlich ist

Sicherheitsbetrachtung:

Die Aufgabe muss ausdruecklich analysieren, welche Risiken bei Browser-Automation fuer MOSA entstehen. Dazu gehoeren mindestens:

- unbeabsichtigte Ausfuehrung echter Aktionen in falschen Umgebungen
- Sichtbarkeit oder Persistenz sensibler Daten in Screenshots, Videos, Traces oder Logs
- Zugriff auf produktive Accounts, Tokens, Cookies oder lokale Dateien
- moegliche Fehlsteuerung eines fernsteuerbaren Browsers durch zu breite Freigaben
- Gefahr, dass eine KI unbeabsichtigt echte externe Seiten bedient, z.B. Einkaeufe, Formulare oder destructive Actions
- Unterschiede zwischen lokalem Testbrowser, CI-Ausfuehrung und interaktiver MCP-Fernsteuerung

Die Bewertung soll nicht nur Risiken sammeln, sondern konkrete Leitplanken formulieren, zum Beispiel:

- nur lokale oder dedizierte Testumgebungen
- nur Test-Identitaeten und Testdaten
- keine produktiven Secrets in Browser- oder Testartefakten
- klare Freigabegrenzen fuer Browser-Fernsteuerung
- definierte Smoke-Pfade statt freier Exploration
- Regeln fuer Speicherung und Loeschung von Screenshots und Traces

Erwartetes Ergebnis:

- eine kurze, belastbare Empfehlung, ob MOSA
  - nur die bestehenden Browser-Tests ausbauen sollte,
  - zusaetzlich Playwright oder ein aehnliches Open-Source-Werkzeug einfuehren sollte,
  - Screenshot- oder Visual-Regression-Tests ergaenzen sollte,
  - oder bewusst auf MCP-basierte Browser-Fernsteuerung verzichten sollte
- eine klare Aussage, welche Kombination fuer den Projektalltag den besten Nutzen bei vertretbarem Sicherheitsrisiko bietet

Akzeptanzkriterien:

- Es gibt eine nachvollziehbare Gegenueberstellung der realistisch geeigneten Open-Source-Optionen.
- Fuer jede Option ist klar beschrieben, was technisch moeglich ist und was nicht.
- Die Sicherheitsrisiken sind konkret auf MOSA bezogen beschrieben und nicht nur abstrakt.
- Die Aufgabe enthaelt ausdruecklich Schutz gegen unbeabsichtigte echte Fremdaktionen im Browser.
- Das Ergebnis ist so formuliert, dass daraus anschliessend eine Implementierungsaufgabe fuer die gewaehlte Loesung abgeleitet werden kann.

Nicht Ziel dieser Aufgabe:

- sofortige Einfuehrung eines neuen Testframeworks
- produktive Freischaltung einer Browser-Fernsteuerung
- allgemeine Security-Review des gesamten Projekts jenseits des Browser-Automation-Themas
