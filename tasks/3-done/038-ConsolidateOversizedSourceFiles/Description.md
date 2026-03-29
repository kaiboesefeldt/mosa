Ziel ist, die aktuell wirklich auffaelligen nicht-testbezogenen Code-Dateien einer gezielten Konsolidierungspruefung zu unterziehen und bei Bedarf so zu zerlegen oder zu straffen, dass Verantwortlichkeiten klarer getrennt und kuenftige Aenderungen einfacher werden.

Die Aufgabe umfasst zwei Schritte:

- Fuer jede betroffene Datei wird geprueft, ob sie fachlich oder technisch zu viele Verantwortlichkeiten in einer Datei vereint.
- Wo die Pruefung einen sinnvollen Zuschnitt zeigt, wird die Datei konsolidiert, aufgeteilt oder vereinfacht, ohne sichtbares Verhalten zu aendern.

Leitplanken:

- Konsolidierung bedeutet nicht bloss Verschiebung in neue Sammeldateien.
- Kleine, zusammengehoerige Dateien sind besser als ein grosser Monolith, aber kuenstliche Zersplitterung ist zu vermeiden.
- Bestehende Paket- und Modulgrenzen sollen respektiert werden.
- Wenn eine Datei bewusst ueber 300 Zeilen bleiben muss, ist das in der Bearbeitung nachvollziehbar zu begruenden.

Diese Aufgabe ist bewusst auf Dateien beschraenkt, bei denen Groesse und Rolle zusammen auf einen echten Konsolidierungsbedarf hindeuten. Rein knapp ueber 300 Zeilen liegende Dateien ohne offensichtliche Verantwortungsmischung gehoeren nicht in diesen Zuschnitt.

- 637 Zeilen: `client/src/jsMain/kotlin/straightway/mosa/client/Main.kt`
- 535 Zeilen: `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPrivacyPolicyService.kt`
- 369 Zeilen: `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStore.kt`
- 351 Zeilen: `server/src/main/kotlin/straightway/mosa/legal/LegalProfileSource.kt`
- 331 Zeilen: `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/moderation/ModerationController.kt`
- 323 Zeilen: `server/src/main/kotlin/straightway/mosa/content/ContentService.kt`

Konkreter Problemzuschnitt pro Datei:

`client/src/jsMain/kotlin/straightway/mosa/client/Main.kt`

- Die Datei mischt App-Bootstrap, globale Service-Aufloesung, Session-Screen-Umschaltung, Legal-FAB, Overlay-State, asynchrone Dokumentlade-Logik, Scroll-Marker-Logik und DOM-nahe Vergleichsinteraktion in einem einzigen Einstiegspunkt.
- Dadurch ist die Hauptdatei gleichzeitig Einstieg, Container-Component und Spezial-UI fuer den Legal-Dialog, was Aenderungen am Einstieg und Aenderungen an der Legal-Oberflaeche unnötig eng koppelt.

`client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPrivacyPolicyService.kt`

- Die Datei vereint Pod-Zugriff, Sprachwahl, ClearTerms-Parsing, Profilvalidierung, Kettenanalyse, Highlight-Ermittlung, Fallback-Strategien fuer unkritische Texte und HTML-Rendering in einem Service.
- Damit vermischt sie Datenbeschaffung, fachliche Aufbereitung und Praesentationsnahe Rendering-Details, sodass einzelne Teile kaum isoliert aenderbar oder separat nachvollziehbar sind.

`server/src/main/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStore.kt`

- Die Datei buendelt Lifecycle, Schreibzugriff, normale Suche, KNN-Nachfilterung, Pagination-Token-Verwaltung, Fetch-Varianten, Voll-Dump und Lucene-Dokumentabbildung.
- Besonders Query-Ausfuehrung und Pagination mit scorebasierter Nachfilterung sind hier eng miteinander verflochten, was die Suchlogik schwer pruefbar und schwer lokal aenderbar macht.

`server/src/main/kotlin/straightway/mosa/legal/LegalProfileSource.kt`

- Die Datei vereint Ressourcenlesen, Dateisystem- und JAR-Scanning, Fragment-Erkennung, YAML-Merging, Metadaten-Parsing und die Aufloesung einer deterministischen Profilkette.
- Dadurch liegen Infrastrukturdetails fuer Ressourcenquellen und fachliche Kettenlogik in derselben Klasse, obwohl das unterschiedliche Aenderungsgruende und unterschiedliche Abstraktionsebenen sind.

`client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/moderation/ModerationController.kt`

- Die Datei haelt sehr viel React-UI-State fuer zwei unterschiedliche Teilbereiche gleichzeitig: Inbox-Moderation und Complaint-Queue inklusive Composer-, Decision-, Pagination- und Popover-Zustand.
- Zusaetzlich mischt sie State-Persistenz, asynchrone Ladeablaeufe, DOM-Outside-Click-Behandlung und die Verdrahtung der Action-Modelle, wodurch der Hook als Sammelstelle fuer mehrere unabhaengige Interaktionsablaeufe fungiert.

`server/src/main/kotlin/straightway/mosa/content/ContentService.kt`

- Die Datei vereint Publishing, Reply-Erzeugung, Loeschregeln, rekursive Root-Reply-Loeschung, Query- und Fetch-Use-Cases, Suchprojektionen, CSV-Export und mehrere Mapping-Funktionen zwischen VectorStore- und API-Modellen.
- Damit ist sie weniger ein fokussierter Service als eine Sammelklasse fuer fast den gesamten Content-Anwendungsfall, was Verantwortlichkeiten fuer Schreiben, Lesen, Loeschen und Export unnoetig koppelt.

Nicht Teil dieser Aufgabe sind vorerst Dateien, die zwar die 300-Zeilen-Leitplanke reissen, deren Groesse aber fuer sich genommen noch kein hinreichender Hinweis auf einen problematischen Zuschnitt ist, zum Beispiel Renderer, Views oder eng fokussierte Ablaufdateien.

Akzeptanzkriterien:

- Alle oben gelisteten Dateien wurden auf Konsolidierungsbedarf geprueft.
- Fuer jede geaenderte Datei ist der neue Zuschnitt fachlich und technisch nachvollziehbar.
- Neue oder angepasste Dateien fuehren nicht zu neuen Monolithen ueber 300 Zeilen ohne begruendete Ausnahme.
- Verhalten und bestehende fachliche Absicherung bleiben erhalten.
