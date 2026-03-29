Dies ist die verpflichtende Abschluss-Subtask fuer die Gesamtintegration der Aenderung.

Ziel ist, die geaenderte Gesamtloesung ueber die wichtigsten funktionalen Pfade hinweg zu pruefen.

Betroffene Komponenten:

- Vertrag und serverseitiger API-Pfad fuer die pod-seitige Selbstloeschung
- serverseitige Kaskade ueber Beitraege, Beschwerden und Admin-Nachrichten
- bestehender Profil-Dialog und lokaler Identitaetszustand im Client

Vorbedingungen:

- Die Subtasks fuer Vertrag, Server und Client sind umgesetzt.
- Testhilfen koennen Beitraege, Beschwerden, Admin-Nachrichten und den verbundenen Identitaetszustand deterministisch vorbereiten.

Auszufuehrende Pruefungen:

1. `Identitaet loeschen` aus dem Profil-Dialog stoesst die pod-seitige Loeschung der aktuell verbundenen Identitaet an.
2. Nach erfolgreicher Loeschung sind keine Beitraege oder Antworten der geloeschten Identitaet mehr sichtbar.
3. Nach erfolgreicher Loeschung sind keine selbst eingereichten Beschwerden der geloeschten Identitaet mehr sichtbar.
4. Nach erfolgreicher Loeschung sind keine Beschwerden ueber Inhalte der geloeschten Identitaet mehr sichtbar.
5. Nach erfolgreicher Loeschung sind keine Admin-Nachrichten der geloeschten Identitaet mehr sichtbar.
6. Daten anderer Identitaeten bleiben ueber die relevanten Read-Pfade unveraendert erreichbar.
7. Bei einem serverseitigen Fehlschlag bleibt die lokale Identitaet erhalten und der Fehler wird im bestehenden UI-Rahmen sichtbar.
8. `./gradlew check` laeuft erfolgreich.

Erfolgskriterium:

- Die wesentlichen API-, Kaskaden-, Fehler- und UI-Pfade der Identitaetsloeschung sind automatisch abgesichert und der komplette Quality Gate ist gruen.
