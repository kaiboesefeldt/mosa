Ziel dieser Subtask ist der Abschlusstest der Gesamtintegration aller im Rahmen von Task 038 konsolidierten Bereiche.

Diese Subtask ist als vollautomatisierte Repo-Verifikation gedacht.

Vorbedingungen:

- Die Subtasks `sub001` bis `sub006` sind umgesetzt oder bewusst mit begruendeter Ausnahme abgeschlossen.
- Alle betroffenen Produktions- und Testdateien sind im Arbeitsbaum in ihrem finalen Stand.

Durchzufuehrende Schritte:

1. Relevante betroffene Tests fuer Client, Server und Shared-Module gruenschnittig ausfuehren, soweit sie die konsolidierten Bereiche direkt absichern.
2. Anschliessend die vollstaendige Qualitaetsschranke mit `./gradlew check` ausfuehren.
3. Die betroffenen konsolidierten Dateien erneut auf ihren Zuschnitt pruefen und verifizieren, dass keine neue Sammeldatei mit demselben Problem entstanden ist.

Explizit gemeinsam zu betrachtende Komponenten:

- Client-App-Einstieg und Legal-Dialog-Fluss
- Client-Legal-Rendering und Privacy/Terms-Dokumentaufbereitung
- Moderationsoberflaeche mit Inbox- und Complaint-Queue-Fluss
- Server-Content-Use-Cases fuer Publish, Reply, Query, Fetch, Delete und Export
- Server-Legal-Profilaufladung
- Server-VectorStore-Query- und Paginationfluss

Erfolgsmerkmale:

- Alle ausgefuehrten relevanten Tests bestehen.
- `./gradlew check` besteht ohne neue fachliche Regression in den betroffenen Bereichen.
- Die neuen Dateizuschnitte sind nachvollziehbar und verschieben den Monolithen nicht nur in anders benannte Sammeldateien.

Fehlschlag ist gegeben, wenn:

- relevante Regressionstests fehlschlagen,
- `./gradlew check` fehlschlaegt,
- oder der neue Zuschnitt zwar Dateien verschiebt, die Verantwortungsmischung aber praktisch unveraendert beibehält.
